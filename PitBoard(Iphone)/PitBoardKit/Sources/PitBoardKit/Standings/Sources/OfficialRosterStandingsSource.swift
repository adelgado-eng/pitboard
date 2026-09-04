import Foundation
import SwiftSoup

/// Combina dos fuentes: una tabla "de autoridad" (`rosterUrl`) con posición, piloto y
/// puntos ya correctos — sin reservas, con el desempate real aplicado — y, opcionalmente,
/// driverdb.com, que solo se usa para emparejar foto y equipo por nombre. Equivalente
/// exacto de `OfficialRosterStandingsSource.kt`. Si driverdb no tiene un piloto que sí
/// está en la tabla de autoridad (o el nombre no coincide lo bastante), esa fila sale sin
/// foto/equipo — pero nunca desaparece: quién sale y en qué puntos/posición viene siempre
/// de la tabla de autoridad.
///
/// Usada por F1 (formula1.com) y NASCAR Cup.
public final class OfficialRosterStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory
    private let rosterUrl: String
    private let driverDbSlug: String?
    /// Plantilla de perfil oficial por piloto, con "{slug}" (ej.
    /// "https://www.formula1.com/en/drivers/{slug}"). Su foto (og:image) tiene prioridad
    /// sobre driverdb si se indica. Solo la usa F1 — NASCAR Cup usa
    /// `rosterPhotoUrlExtractor` en su lugar (ver comentario en el Kotlin original sobre
    /// por qué nascar.com resultó poco fiable página a página).
    private let officialProfileUrlTemplate: String?
    /// Extrae la foto directamente de la celda de nombre de la tabla de autoridad, sin
    /// petición HTTP extra — tiene prioridad sobre `officialProfileUrlTemplate`. La usa
    /// NASCAR Cup (id de ESPN + su CDN de fotos).
    private let rosterPhotoUrlExtractor: (@Sendable (Element) -> String?)?

    public init(
        category: StandingsCategory,
        rosterUrl: String,
        driverDbSlug: String? = nil,
        officialProfileUrlTemplate: String? = nil,
        rosterPhotoUrlExtractor: (@Sendable (Element) -> String?)? = nil
    ) {
        self.category = category
        self.rosterUrl = rosterUrl
        self.driverDbSlug = driverDbSlug
        self.officialProfileUrlTemplate = officialProfileUrlTemplate
        self.rosterPhotoUrlExtractor = rosterPhotoUrlExtractor
    }

    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) — ver
    // OfficialRosterStandingsSourceTests.
    struct RosterRow {
        var name: String
        var points: Double
        var photoUrl: String? = nil
    }

    struct Enrichment {
        var photoUrl: String?
        var team: String
    }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let rosterRows = try await fetchRoster()
        if rosterRows.isEmpty { return [] }

        var enrichment: [String: Enrichment] = [:]
        if let driverDbSlug {
            enrichment = (try? await fetchDriverDbEnrichment(slug: driverDbSlug)) ?? [:]
        }

        // Fotos oficiales en paralelo (tope 6 a la vez) — mismo motivo que en Android:
        // pedirlas en serie (23 peticiones a formula1.com una detrás de otra) podía
        // comerse el tiempo entero de la sincronización.
        let officialPhotos: [String?]
        if rosterRows.contains(where: { $0.photoUrl != nil }) {
            // Ya vienen extraídas de la propia tabla de autoridad — sin peticiones extra.
            officialPhotos = rosterRows.map(\.photoUrl)
        } else if let template = officialProfileUrlTemplate {
            officialPhotos = await withBoundedConcurrency(rosterRows, limit: Self.maxParallelPhotoRequests) { row -> String? in
                let url = template.replacingOccurrences(of: "{slug}", with: TextNormalizer.slugify(row.name))
                return try? await self.fetchProfilePhoto(url)
            }
        } else {
            officialPhotos = Array(repeating: nil, count: rosterRows.count)
        }

        // La posición se renumera 1..N según el orden en que ya vienen listadas (el
        // orden real de la tabla de autoridad) en vez de fiarse de la columna "Pos" del
        // HTML de origen, que puede traer huecos o repetidos en caso de empate.
        let driverRows: [StandingDraft] = rosterRows.enumerated().map { index, row in
            let match = enrichment.first { key, _ in namesMatch(row.name, key) }?.value
            let officialPhoto = officialPhotos[index] ?? nil
            return StandingDraft(
                category: category,
                type: .driver,
                entrantKey: "\(category.rawValue)-DRIVER-\(row.name)",
                position: index + 1,
                name: row.name,
                team: match?.team ?? "",
                points: row.points,
                photoUrl: officialPhoto ?? match?.photoUrl,
                updatedAtUtc: nowUtc
            )
        }

        // Equipos: se agrupan los pilotos por el equipo emparejado desde driverdb y se
        // suman sus puntos — ninguna de las dos fuentes trae una tabla de constructores
        // separada en esta plantilla.
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
                    photoUrl: Self.teamLogoUrls[TextNormalizer.normalize(teamName)],
                    updatedAtUtc: nowUtc
                )
            }

        return driverRows + teamRows
    }

    /// Cabeceras de una tabla: `<th>` si los hay, y si no, la primera fila cuyas celdas
    /// `<td>` incluyan la palabra "Driver" (caso espn.com/racing/standings: su tabla no
    /// usa `<th>`, y la primera fila real es un título con una sola celda "Standings").
    private func headerCells(_ table: Element) throws -> [Element] {
        let ths = try table.select("th").array()
        if !ths.isEmpty { return ths }
        let row = try table.select("tr").array().first { row in
            try row.select("td").array().contains { try $0.text().localizedCaseInsensitiveContains("Driver") }
        }
        return try row?.select("td").array() ?? []
    }

    private func fetchRoster() async throws -> [RosterRow] {
        try parseRosterHTML(try await fetchHtml(rosterUrl))
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetchRoster() para poder testear el
    // parsing (incluida la limpieza del código de piloto pegado al nombre, ver
    // cleanDriverName) contra un fixture HTML sin red — ver
    // OfficialRosterStandingsSourceTests.
    func parseRosterHTML(_ html: String) throws -> [RosterRow] {
        let doc = try SwiftSoup.parse(html, rosterUrl)
        let tables = try doc.select("table").array()
        guard let table = try tables.first(where: { table in
            try headerCells(table).contains { try $0.text().localizedCaseInsensitiveContains("Driver") }
        }) else {
            return []
        }

        let headers = try headerCells(table).map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
        let nameIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Driver") } ?? 1
        guard let pointsIndex = headers.firstIndex(where: {
            $0.localizedCaseInsensitiveContains("Points") || $0.localizedCaseInsensitiveContains("Pts")
        }) else {
            return []
        }

        let rows = try table.select("tbody tr").array()
        return try rows.compactMap { row -> RosterRow? in
            let cells = try row.select("td").array()
            guard !cells.isEmpty, cells.indices.contains(nameIndex) else { return nil }

            let nameCell = cells[nameIndex]
            let linkText = try nameCell.select("a").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let rawName = (linkText?.isEmpty == false ? linkText : nil) ?? (try nameCell.text().trimmingCharacters(in: .whitespacesAndNewlines))
            let name = cleanDriverName(rawName)
            guard !name.isEmpty else { return nil }

            // Exigir que los puntos sean interpretables como número descarta de forma
            // natural la propia fila de cabecera cuando la tabla no tiene <th>.
            guard cells.indices.contains(pointsIndex),
                  let points = Double(try cells[pointsIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)) else {
                return nil
            }

            let photoUrl = rosterPhotoUrlExtractor?(nameCell)
            return RosterRow(name: name, points: points, photoUrl: photoUrl)
        }
    }

    private func fetchDriverDbEnrichment(slug: String) async throws -> [String: Enrichment] {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let url = "https://www.driverdb.com/championships/\(slug)/\(year)/standings"
        return try parseDriverDbHTML(try await fetchHtml(url), url: url)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetchDriverDbEnrichment() para
    // poder testear el parsing (incluido el filtro del guion largo "—" como equipo)
    // contra un fixture HTML sin red — ver OfficialRosterStandingsSourceTests.
    func parseDriverDbHTML(_ html: String, url: String) throws -> [String: Enrichment] {
        let doc = try SwiftSoup.parse(html, url)
        let tables = try doc.select("table").array()
        guard let table = try tables.first(where: { table in
            try table.select("th").array().contains { try $0.text().localizedCaseInsensitiveContains("Driver") }
        }) else {
            return [:]
        }

        let headers = try table.select("th").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
        let driverIndex = headers.firstIndex {
            $0.localizedCaseInsensitiveContains("Driver")
                && !$0.localizedCaseInsensitiveContains("No")
                && !$0.localizedCaseInsensitiveContains("Rating")
        } ?? 1
        let teamIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Team") }

        var result: [String: Enrichment] = [:]
        let rows = try table.select("tbody tr").array()
        for row in rows {
            let cells = try row.select("td").array()
            guard !cells.isEmpty, cells.indices.contains(driverIndex) else { continue }
            let driverCell = cells[driverIndex]

            let linkText = try driverCell.select("a").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let rawName = (linkText?.isEmpty == false ? linkText : nil) ?? (try driverCell.text().trimmingCharacters(in: .whitespacesAndNewlines))
            let name = cleanDriverName(rawName)
            guard !name.isEmpty else { continue }

            let photoUrl = try driverCell.select("img").first()
                .flatMap { try? $0.absUrl("src") }
                .flatMap { $0.isEmpty || $0.localizedCaseInsensitiveContains("default/driver-profile") ? nil : $0 }

            // driverdb muestra un guion largo ("—") en la celda de equipo cuando no
            // tiene ese dato — texto no vacío, así que sin este filtro se agrupaba como
            // si "—" fuera un nombre de equipo real.
            var team = ""
            if let teamIndex, cells.indices.contains(teamIndex) {
                let raw = try cells[teamIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
                team = raw.range(of: "^[-–—]+$", options: .regularExpression) != nil ? "" : raw
            }

            result[name] = Enrichment(photoUrl: photoUrl, team: team)
        }
        return result
    }

    /// Quita el prefijo de dorsal ("#4 ") y el código de 3 letras mayúsculas que algunas
    /// webs pegan sin espacio tras el apellido (ej. "Kimi AntonelliANT" ->
    /// "Kimi Antonelli"). Solo se quita si va pegado a una letra minúscula justo antes.
    private func cleanDriverName(_ raw: String) -> String {
        var result = raw.replacingOccurrences(of: "^#?\\d+\\s+", with: "", options: .regularExpression)
        result = result.replacingOccurrences(
            of: "(?<=[a-zà-öø-ÿ])[A-Z]{3}$",
            with: "",
            options: .regularExpression
        )
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func fetchHtml(_ url: String) async throws -> String {
        try await HTTPClient.fetchHTML(url)
    }

    /// Lee la página de perfil oficial y extrae la foto de `meta[property=og:image]` —
    /// presente en el HTML servido de las páginas individuales, a diferencia del listado
    /// general.
    private func fetchProfilePhoto(_ url: String) async throws -> String? {
        let html = try await fetchHtml(url)
        let doc = try SwiftSoup.parse(html, url)
        let content = try doc.select("meta[property=og:image]").first()?.attr("content")
        return content?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    /// Emparejamiento "mejor esfuerzo" entre dos webs distintas (ej. "Kimi Antonelli" vs
    /// "Andrea Kimi Antonelli"): normaliza y acepta si uno contiene al otro; como último
    /// recurso compara también sin espacios (iniciales con puntuación distinta entre
    /// páginas, ej. "AJ Allmendinger" vs "A. J. Allmendinger").
    private func namesMatch(_ a: String, _ b: String) -> Bool {
        let na = TextNormalizer.normalize(a)
        let nb = TextNormalizer.normalize(b)
        guard !na.isEmpty, !nb.isEmpty else { return false }
        if na == nb || na.contains(nb) || nb.contains(na) { return true }
        let ca = na.replacingOccurrences(of: " ", with: "")
        let cb = nb.replacingOccurrences(of: " ", with: "")
        return ca == cb || ca.contains(cb) || cb.contains(ca)
    }

    private static let maxParallelPhotoRequests = 6

    /// Logos oficiales de equipo por nombre normalizado — compartido entre F1 y NASCAR
    /// Cup (las claves de una y otra categoría no chocan entre sí).
    private static let teamLogoUrls: [String: String] = [
        "mercedes": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/mercedes/2026mercedeslogo.webp",
        "ferrari": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/ferrari/2026ferrarilogo.webp",
        "mclaren": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/mclaren/2026mclarenlogo.webp",
        "red bull": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/redbullracing/2026redbullracinglogo.webp",
        "alpine": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/alpine/2026alpinelogo.webp",
        "racing bulls": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/racingbulls/2026racingbullslogo.webp",
        "haas": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/haasf1team/2026haasf1teamlogo.webp",
        "audi": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/audi/2026audilogo.webp",
        "williams": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/williams/2026williamslogo.webp",
        "aston martin": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/astonmartin/2026astonmartinlogo.webp",
        "cadillac": "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/cadillac/2026cadillaclogo.webp",
        "23xi racing": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/23XI-Solid-Racing-Red.png",
        "front row motorsports": "https://www.nascar.com/wp-content/uploads/sites/7/2026/04/01/FRM-logo-full-color.png",
        "haas factory team": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/Haas-Factory-Team-1.png",
        "hendrick": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Hendrick_Motorsports_Logo.svg.png",
        "hyak motorsports": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/HYAK_Final81.jpg",
        "joe gibbs": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/JGR-Block-Logo.png",
        "kaulig racing": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Black-Stacked.png",
        "legacy": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/27/LegacyMC_Global_OnWhite-RGB_2026.png",
        "richard childress": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/RCR_Updated-2.png",
        "rick ware": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/RWRwithText.png.png",
        "rfk racing": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/RFK_logo-443x189-1.jpg",
        "spire motorsports": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Spire_HorizontalBadge.png",
        "trackhouse": "https://www.nascar.com/wp-content/uploads/sites/7/2021/01/02/Trackhouse_light.png",
        "team penske": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/Penskelogo1.png",
        "wood brothers": "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/WoodBrothersPrimary-Logo.png"
    ]
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
