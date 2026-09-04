import Foundation
import SwiftSoup

/// Analizador HTML genérico para páginas de clasificación con una tabla `<table>` normal
/// (posición, piloto/equipo, puntos) — equivalente exacto de
/// `MotorsportStandingsHtmlSource.kt`. Hoy su único uso directo es MotoGP
/// (autosport.com) — IndyCar, F1 Academy, NASCAR Cup y Porsche Supercup, que antes
/// también lo usaban, han ido pasando a fuentes propias en Android y aquí siguen el
/// mismo camino (ver `OfficialRosterStandingsSource`/`DriverDbStandingsSource`).
///
/// HONESTO: esto es "leer una página web", no una API — si el sitio cambia el diseño de
/// su tabla, esta categoría dejará de funcionar y habrá que ajustar los selectores.
///
/// A diferencia del Kotlin original (`open class`, pensada para subclasificarse), esta
/// versión es `final`: en Swift, un caso "misma clase con parámetros fijos por serie" se
/// resuelve mejor con una función factoría que devuelve una instancia ya configurada
/// (ver `MotoGpStandingsSource.make()`) que con herencia — evita reimplementar `fetch()`
/// para no aportar nada nuevo.
public final class MotorsportStandingsHTMLSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory
    private let driverUrl: String
    private let teamUrl: String?
    private let standingsClass: StandingsClass
    /// Si se indica, se descarta a cualquier piloto que no aparezca en esta página de
    /// referencia (parrilla confirmada) — evita reservas/wildcards con 0 puntos. Ver
    /// `RosterNameFilter`.
    private let knownRosterUrl: String?
    /// Fotos de piloto por clave "inicial + apellido" (ver `photoKey`) — mapa fijo.
    private let driverPhotoUrls: [String: String]
    /// Logos de equipo por nombre normalizado, aplicados a las filas TEAM.
    private let teamLogoUrls: [String: String]
    /// UUID de categoría de la API interna de motogp.com (Moto2/Moto3) — si se indica,
    /// fotos de piloto y logos de equipo se resuelven EN VIVO en cada sincronización, con
    /// prioridad sobre los dos mapas fijos de arriba para el mismo piloto/equipo.
    private let pulseliveCategoryUuid: String?

    public init(
        category: StandingsCategory,
        driverUrl: String,
        teamUrl: String? = nil,
        standingsClass: StandingsClass = .overall,
        knownRosterUrl: String? = nil,
        driverPhotoUrls: [String: String] = [:],
        teamLogoUrls: [String: String] = [:],
        pulseliveCategoryUuid: String? = nil
    ) {
        self.category = category
        self.driverUrl = driverUrl
        self.teamUrl = teamUrl
        self.standingsClass = standingsClass
        self.knownRosterUrl = knownRosterUrl
        self.driverPhotoUrls = driverPhotoUrls
        self.teamLogoUrls = teamLogoUrls
        self.pulseliveCategoryUuid = pulseliveCategoryUuid
    }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        var livePhotos: [String: String] = [:]
        var liveLogos: [String: String] = [:]
        if let uuid = pulseliveCategoryUuid, let maps = try? await fetchPulseliveMaps(categoryUuid: uuid) {
            livePhotos = maps.photos
            liveLogos = maps.logos
        }
        // El orden importa: el mapa "vivo" tiene prioridad sobre el fijo para la misma clave.
        let effectiveDriverPhotoUrls = driverPhotoUrls.merging(livePhotos) { _, live in live }
        let effectiveTeamLogoUrls = teamLogoUrls.merging(liveLogos) { _, live in live }

        // El equipo es "best effort": si falla, simplemente no hay filas TEAM — nunca
        // debe tumbar la lista de pilotos.
        let teamParsedRows: [ParsedRow]
        if let teamUrl {
            teamParsedRows = (try? await parseTable(url: teamUrl)) ?? []
        } else {
            teamParsedRows = []
        }

        // autosport.com no separa "J. Martin Aprilia Racing Team" en piloto y equipo con
        // ninguna marca HTML — se usan los nombres de equipo reales (ya descargados
        // arriba) para ver si el texto del piloto termina en uno de ellos, del más largo
        // al más corto para que un nombre que sea prefijo de otro más largo no corte mal.
        let knownTeamNames = teamParsedRows
            .map(\.name)
            .filter { !$0.isEmpty }
            .sorted { $0.count > $1.count }

        var knownRiderNames: Set<String> = []
        if let knownRosterUrl {
            knownRiderNames = (try? await RosterNameFilter.fetchKnownNames(knownRosterUrl)) ?? []
        }
        let rawDriverRows = (try? await parseTable(url: driverUrl, knownTeamNames: knownTeamNames)) ?? []
        let driverParsedRows = RosterNameFilter.filterKeepingReal(rawDriverRows, knownNames: knownRiderNames) { $0.name }

        let driverRows: [StandingDraft] = driverParsedRows.enumerated().map { index, row in
            let photoUrl = effectiveDriverPhotoUrls[photoKey(row.name)]
            var updated = row
            updated.photoUrl = photoUrl
            return toDraft(type: .driver, index: index, row: updated, nowUtc: nowUtc)
        }

        let teamRows: [StandingDraft] = teamParsedRows.enumerated().map { index, row in
            let logoUrl = effectiveTeamLogoUrls[TextNormalizer.normalize(row.name)]
            var updated = row
            updated.team = ""
            updated.photoUrl = logoUrl
            return toDraft(type: .team, index: index, row: updated, nowUtc: nowUtc)
        }

        return driverRows + teamRows
    }

    private func toDraft(type: StandingType, index: Int, row: ParsedRow, nowUtc: Date) -> StandingDraft {
        StandingDraft(
            category: category,
            standingsClass: standingsClass,
            type: type,
            entrantKey: "\(category.rawValue)-\(type.rawValue)-\(row.name)",
            position: index + 1,
            name: row.name,
            team: row.team,
            points: row.points,
            photoUrl: row.photoUrl,
            updatedAtUtc: nowUtc
        )
    }

    /// Clave del mapa `driverPhotoUrls`: inicial del nombre de pila + apellido completo,
    /// sin tildes ni signos — autosport.com abrevia el nombre de pila ("J. Martin")
    /// mientras que el mapa usa el nombre completo ("Jorge Martin"); ambos dan "j
    /// martin". Se conserva el apellido ENTERO (no solo la última palabra) para no
    /// romper apellidos compuestos, y la inicial del nombre para distinguir hermanos y
    /// homónimos (Marc/Alex Marquez, Raul/Augusto Fernandez).
    private func photoKey(_ name: String) -> String {
        let parts = TextNormalizer.normalize(name).components(separatedBy: " ").filter { !$0.isEmpty }
        guard parts.count >= 2 else { return parts.joined(separator: " ") }
        let initial = String(parts[0].prefix(1))
        return initial + " " + parts.dropFirst().joined(separator: " ")
    }

    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) — ver
    // MotorsportStandingsHTMLSourceTests.
    struct ParsedRow {
        var name: String
        var team: String
        var points: Double
        var photoUrl: String? = nil
    }

    private func parseTable(url: String, knownTeamNames: [String] = []) async throws -> [ParsedRow] {
        let html = try await HTTPClient.fetchHTML(url)
        return try parseTableHTML(html, knownTeamNames: knownTeamNames)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de parseTable() para poder testear el
    // parsing contra un fixture HTML real sin red — ver MotorsportStandingsHTMLSourceTests.
    func parseTableHTML(_ html: String, knownTeamNames: [String] = []) throws -> [ParsedRow] {
        let doc = try SwiftSoup.parse(html)

        let tables = try doc.select("table").array()
        guard let table = try tables.first(where: { table in
            try table.select("th").array().contains { th in
                let text = try th.text()
                return text.localizedCaseInsensitiveContains("Driver")
                    || text.localizedCaseInsensitiveContains("Team")
                    // MotoGP usa "Rider" en vez de "Driver" — sin esto, esta tabla nunca
                    // se encontraba y la categoría se quedaba sin datos para siempre.
                    || text.localizedCaseInsensitiveContains("Rider")
            }
        }) else {
            return []
        }

        // Localizamos la columna de puntos por su encabezado en vez de adivinar "el
        // primer número tras el nombre" — eso fallaba en Porsche Supercup (cogía el
        // número de coche, que también es un td numérico y va antes que los puntos).
        let headerCells = try table.select("th").array()
        let pointsColumnIndex: Int? = try headerCells.firstIndex { th in
            let text = try th.text()
            return text.localizedCaseInsensitiveContains("Points") || text.localizedCaseInsensitiveContains("Pts")
        }

        let rows = try table.select("tbody tr").array()
        return try rows.compactMap { try parseRow($0, pointsColumnIndex: pointsColumnIndex, knownTeamNames: knownTeamNames) }
    }

    private func parseRow(_ row: Element, pointsColumnIndex: Int?, knownTeamNames: [String]) throws -> ParsedRow? {
        let cells = try row.select("td").array()
        guard !cells.isEmpty else { return nil }

        let nameCellIndex = try cells.firstIndex { try $0.select("a").first() != nil } ?? 1
        guard let nameCell = cells.indices.contains(nameCellIndex) ? cells[nameCellIndex] : nil else { return nil }

        // Heurística: si la celda de nombre tiene varios hijos (ej. un <span> para el
        // piloto y otro para el equipo), se cogen por separado; si no, se intenta cortar
        // por un nombre de equipo conocido, y si ninguno aplica, todo el texto se queda
        // como "name" con equipo vacío.
        let children = nameCell.children().array()
        var name: String
        var team: String
        if children.count >= 2 {
            name = try children[0].text().trimmingCharacters(in: .whitespacesAndNewlines)
            team = try children[1].text().trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            let rawText = try nameCell.text().trimmingCharacters(in: .whitespacesAndNewlines)
            if let matchedTeam = knownTeamNames.first(where: { rawText.hasSuffix($0) && rawText.count > $0.count }) {
                name = String(rawText.dropLast(matchedTeam.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                team = matchedTeam
            } else {
                name = rawText
                team = ""
            }
        }
        guard !name.isEmpty else { return nil }

        // Con la columna de puntos localizada por encabezado, una celda vacía (categoría
        // sin resultados todavía esta temporada) cuenta como 0 en vez de descartar la
        // fila. Si no se localizó la columna, se cae al comportamiento de "primer número
        // tras el nombre".
        let points: Double
        if let pointsColumnIndex, cells.indices.contains(pointsColumnIndex) {
            let text = try cells[pointsColumnIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
            points = Double(text) ?? 0.0
        } else {
            let remaining = cells.indices.contains(nameCellIndex + 1) ? Array(cells[(nameCellIndex + 1)...]) : []
            guard let firstNumeric = try remaining.compactMap({ cell -> Double? in
                Double(try cell.text().trimmingCharacters(in: .whitespacesAndNewlines))
            }).first else {
                return nil
            }
            points = firstNumeric
        }

        return ParsedRow(name: name, team: team, points: points)
    }

    /// Fotos de piloto (clave = `photoKey`) y logos de equipo (clave =
    /// `TextNormalizer.normalize`) de la API interna de motogp.com — solo cuenta a los
    /// pilotos "en parrilla" (`in_grid == true`); esa misma API lista también bajas/
    /// reservas fuera de la temporada actual.
    private func fetchPulseliveMaps(categoryUuid: String) async throws -> (photos: [String: String], logos: [String: String]) {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let url = "https://api.pulselive.motogp.com/motogp/v1/riders?category=\(categoryUuid)&season=\(year)"
        let riders: [PulseliveRider] = try await HTTPClient.fetchJSON(url)

        var photos: [String: String] = [:]
        var logos: [String: String] = [:]
        for rider in riders {
            guard let step = rider.currentCareerStep, step.inGrid == true else { continue }
            let name = rider.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let surname = rider.surname?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !name.isEmpty, !surname.isEmpty,
               let photo = step.pictures?.profile?.main?.trimmingCharacters(in: .whitespacesAndNewlines), !photo.isEmpty {
                photos[photoKey("\(name) \(surname)")] = photo
            }
            if let teamName = step.team?.name?.trimmingCharacters(in: .whitespacesAndNewlines), !teamName.isEmpty,
               let teamLogo = step.team?.picture?.trimmingCharacters(in: .whitespacesAndNewlines), !teamLogo.isEmpty {
                logos[TextNormalizer.normalize(teamName)] = teamLogo
            }
        }
        return (photos, logos)
    }
}

private struct PulseliveRider: Decodable {
    let name: String?
    let surname: String?
    let currentCareerStep: PulseliveCareerStep?

    enum CodingKeys: String, CodingKey {
        case name, surname
        case currentCareerStep = "current_career_step"
    }
}

private struct PulseliveCareerStep: Decodable {
    let inGrid: Bool?
    let team: PulseliveTeamRef?
    let pictures: PulselivePictures?

    enum CodingKeys: String, CodingKey {
        case inGrid = "in_grid"
        case team, pictures
    }
}

private struct PulseliveTeamRef: Decodable {
    let name: String?
    let picture: String?
}

private struct PulselivePictures: Decodable {
    let profile: PulseliveProfilePic?
}

private struct PulseliveProfilePic: Decodable {
    let main: String?
}
