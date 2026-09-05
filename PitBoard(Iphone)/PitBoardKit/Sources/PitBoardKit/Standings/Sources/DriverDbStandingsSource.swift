import Foundation
import SwiftSoup

/// driverdb.com: una sola tabla de pilotos por categoría, con foto, equipo y puntos en
/// la misma fila — equivalente exacto de `DriverDbStandingsSource.kt`. Usada por F1,
/// NASCAR Cup, IndyCar, Porsche Supercup y F1 Academy.
///
/// driverdb no publica una tabla de equipos aparte: la clasificación de equipos se
/// calcula agregando los puntos de sus pilotos (columna "Team" de cada fila) — coincide
/// con la fórmula real en todas estas categorías.
public final class DriverDbStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory
    private let slug: String
    /// Si se indica, descarta cualquier piloto que no aparezca en esta página de
    /// referencia — evita reservas/test que driverdb sí lista con 0 puntos.
    private let knownRosterUrl: String?
    private let teamLogoUrls: [String: String]
    /// Fotos de piloto por nombre normalizado, de respaldo cuando driverdb no trae una
    /// foto real. Solo se usa si driverdb no dio ya una foto válida.
    private let driverPhotoUrls: [String: String]
    /// Plantilla de ficha propia por piloto ("{slug}") para resolver fotos EN VIVO
    /// (F2/F3) en vez de un mapa hardcodeado — solo para quien siga sin foto tras
    /// driverdb y `driverPhotoUrls`.
    private let officialProfileUrlTemplate: String?

    public init(
        category: StandingsCategory,
        slug: String,
        knownRosterUrl: String? = nil,
        teamLogoUrls: [String: String] = [:],
        driverPhotoUrls: [String: String] = [:],
        officialProfileUrlTemplate: String? = nil
    ) {
        self.category = category
        self.slug = slug
        self.knownRosterUrl = knownRosterUrl
        self.teamLogoUrls = teamLogoUrls
        self.driverPhotoUrls = driverPhotoUrls
        self.officialProfileUrlTemplate = officialProfileUrlTemplate
    }

    private var standingsUrl: String {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        return "https://www.driverdb.com/championships/\(slug)/\(year)/standings"
    }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let html = try await HTTPClient.fetchHTML(standingsUrl)
        let doc = try SwiftSoup.parse(html, standingsUrl)
        let tables = try doc.select("table").array()
        guard let table = try tables.first(where: { table in
            try table.select("th").array().contains { try $0.text().localizedCaseInsensitiveContains("Driver") }
        }) else {
            return []
        }

        let parsedRows = try parseDriverRows(table: table, nowUtc: nowUtc)

        // Filtro de reservas: mejor esfuerzo, si no se pudo obtener la parrilla de
        // referencia no se filtra nada.
        var knownNames: Set<String> = []
        if let knownRosterUrl {
            knownNames = (try? await RosterNameFilter.fetchKnownNames(knownRosterUrl)) ?? []
        }
        let filteredRows0 = RosterNameFilter.filterKeepingReal(parsedRows, knownNames: knownNames) { $0.name }

        // Fotos oficiales EN VIVO para quien siga sin foto — en paralelo, tope 6.
        let filteredRows: [StandingDraft]
        if let template = officialProfileUrlTemplate {
            filteredRows = await withBoundedConcurrency(filteredRows0, limit: Self.maxParallelPhotoRequests) { row -> StandingDraft in
                if row.photoUrl != nil { return row }
                let url = template.replacingOccurrences(of: "{slug}", with: TextNormalizer.slugify(row.name))
                guard let photo = try? await self.fetchOfficialPhoto(url) else { return row }
                var updated = row
                updated.photoUrl = photo
                return updated
            }
        } else {
            filteredRows = filteredRows0
        }

        // No nos fiamos de la posición que trae driverdb (empates a puntos repiten
        // número, y tras filtrar reservas quedan huecos) — se renumera secuencialmente
        // según el orden ya listado.
        let driverRows: [StandingDraft] = filteredRows.enumerated().map { index, row in
            var updated = row
            updated.position = index + 1
            return updated
        }

        var totalsByTeam: [String: Double] = [:]
        var teamOrder: [String] = []
        for row in driverRows where !row.team.isEmpty {
            if totalsByTeam[row.team] == nil { teamOrder.append(row.team) }
            totalsByTeam[row.team, default: 0] += row.points
        }
        let teamRows: [StandingDraft] = teamOrder
            .map { ($0, totalsByTeam[$0] ?? 0) }
            .sorted { $0.1 > $1.1 }
            .enumerated()
            .map { index, entry in
                let (teamName, points) = entry
                return StandingDraft(
                    category: category,
                    type: .team,
                    entrantKey: "\(category.rawValue)-TEAM-\(teamName)",
                    position: index + 1,
                    name: teamName,
                    team: "",
                    points: points,
                    photoUrl: teamLogoUrls[TextNormalizer.normalize(teamName)],
                    updatedAtUtc: nowUtc
                )
            }

        return driverRows + teamRows
    }

    /// "right.webp" (F2) o "right-1.webp"/"right-1.jpg" (F3) al final de la URL — cada
    /// ficha de fiaformula2.com/fiaformula3.com repite la misma foto de estudio varias
    /// veces; se prefiere a `meta[og:image]`, que ahí es una tarjeta 1200x630 con
    /// relleno de color y se ve mal recortada en el círculo de la fila.
    private func fetchOfficialPhoto(_ url: String) async throws -> String? {
        let html = try await HTTPClient.fetchHTML(url)
        let doc = try SwiftSoup.parse(html, url)
        let images = try doc.select("img").array()
        for image in images {
            let src = (try? image.absUrl("src")) ?? ""
            if src.range(of: "right(-\\d+)?\\.(webp|jpg)$", options: [.regularExpression, .caseInsensitive]) != nil {
                return src
            }
        }
        return nil
    }

    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) — ya tomaba un Element en
    // vez de una URL, así que no hace falta separar nada más. Ver
    // DriverDbStandingsSourceTests.
    func parseDriverRows(table: Element, nowUtc: Date) throws -> [StandingDraft] {
        let headers = try table.select("th").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }

        let posIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Pos") }
        let driverIndex = headers.firstIndex {
            $0.localizedCaseInsensitiveContains("Driver")
                && !$0.localizedCaseInsensitiveContains("No")
                && !$0.localizedCaseInsensitiveContains("Rating")
        } ?? 1
        let teamIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Team") }
        guard let pointsIndex = headers.firstIndex(where: {
            $0.localizedCaseInsensitiveContains("Points")
                || $0.localizedCaseInsensitiveContains("Pts")
                || $0.localizedCaseInsensitiveContains("Champ")
        }) else {
            return []
        }

        let rows = try table.select("tbody tr").array()
        var result: [StandingDraft] = []
        for (index, row) in rows.enumerated() {
            let cells = try row.select("td").array()
            guard !cells.isEmpty, cells.indices.contains(driverIndex) else { continue }
            let driverCell = cells[driverIndex]

            let linkText = try driverCell.select("a").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            // 04/09/2026: "try" tiene que cubrir toda la expresión "??", no solo el lado
            // derecho entre paréntesis — si no, el compilador lo rechaza con "Operator
            // can throw but expression is not marked with 'try'" (lo detectó el CI de
            // GitHub Actions al llegar por fin a compilar este archivo).
            let rawName = try (linkText?.isEmpty == false ? linkText : nil) ?? driverCell.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let name = rawName.replacingOccurrences(of: "^#?\\d+\\s+", with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { continue }

            // driverdb sirve las imágenes vía su optimizador Next.js
            // ("/_next/image?url=<url codificada>&w=..."), así que el placeholder llega
            // como "...%2Fdefault%2Fdriver-profile.png..." — se comprueban ambas formas.
            var photoUrl: String? = try driverCell.select("img").first().flatMap { try? $0.absUrl("src") }
            if let url = photoUrl,
               url.isEmpty
                || url.localizedCaseInsensitiveContains("default/driver-profile")
                || url.localizedCaseInsensitiveContains("default%2Fdriver-profile") {
                photoUrl = nil
            }
            if photoUrl == nil {
                photoUrl = driverPhotoUrls[TextNormalizer.normalize(name)]
            }

            var team = ""
            if let teamIndex, cells.indices.contains(teamIndex) {
                let raw = try cells[teamIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
                team = raw.range(of: "^[-–—]+$", options: .regularExpression) != nil ? "" : raw
            }

            let points = cells.indices.contains(pointsIndex)
                ? (Double(try cells[pointsIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0.0)
                : 0.0
            let position = posIndex.flatMap { idx -> Int? in
                guard cells.indices.contains(idx) else { return nil }
                return Int((try? cells[idx].text().trimmingCharacters(in: .whitespacesAndNewlines)) ?? "")
            } ?? (index + 1)

            result.append(StandingDraft(
                category: category,
                type: .driver,
                entrantKey: "\(category.rawValue)-DRIVER-\(name)",
                position: position,
                name: name,
                team: team,
                points: points,
                photoUrl: photoUrl,
                updatedAtUtc: nowUtc
            ))
        }
        return result
    }

    private static let maxParallelPhotoRequests = 6
}
