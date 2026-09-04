import Foundation
import SwiftSoup

/// IMSA WeatherTech SportsCar Championship — equivalente exacto de
/// `ImsaStandingsSource.kt`. Igual que ELMS, se trata "por coche" dentro de sus 4 clases
/// (GTP, LMP2, GTD Pro, GTD).
///
/// No conforma `StandingsSource` (misma razón que en Android): el logo de equipo va EN
/// las filas de clasificación, pero sale de la MISMA página que los pilotos — separar
/// "clasificación" y "pilotos" en dos fuentes obligaría a visitar las ~50 páginas de
/// equipo dos veces. `StandingsRepository` la trata como una rama de sincronización
/// aparte, con el mismo aislamiento de fallos que las demás.
///
/// 1) CLASIFICACIÓN POR COCHE — widget AJAX de WordPress:
///    `POST https://www.imsa.com/wp-admin/admin-ajax.php` con
///    `action=getImsaStandings&standings=team&seriesId=1&currentYear={año}&classId={id}`.
///    El `classId` de cada clase se resuelve por su `shortcode` contra una API JSON
///    pública y estable; si esa API fallara se cae a `fallbackClassIds`.
/// 2) PILOTOS + LOGO POR COCHE — cada fila enlaza a `imsa.com/racing-teams/{slug}/`, con
///    `.team-logos img` (se descartan el logo fijo de la serie y el placeholder
///    "nologo", se queda con la siguiente) y tarjetas `div.imsa-card_item_widget` con
///    `img[data-src]` (foto real — el `src` es un placeholder de carga perezosa) y
///    `p.imsa-ciw-title` (nombre).
public final class ImsaStandingsSource: @unchecked Sendable {

    public let category: StandingsCategory = .imsa

    public init() {}

    public func fetch(nowUtc: Date) async throws -> ImsaFetchResult {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let classIds = await resolveClassIds(year: year)

        var teamRows: [TeamRow] = []
        for (standingsClass, shortcode) in Self.classShortcodes {
            let classId = classIds[shortcode] ?? Self.fallbackClassIds[standingsClass] ?? ""
            guard !classId.isEmpty else { continue }
            if let rows = try? await fetchClassTeamRows(standingsClass: standingsClass, classId: classId, year: year) {
                teamRows.append(contentsOf: rows)
            }
        }

        let teamPages = await withBoundedConcurrency(teamRows, limit: Self.teamPageConcurrency) { row -> TeamPage? in
            guard let teamUrl = row.teamUrl else { return nil }
            return await self.fetchTeamPage(teamUrl: teamUrl, row: row, nowUtc: nowUtc)
        }

        var pageByRowIndex: [Int: TeamPage] = [:]
        for (index, page) in teamPages.enumerated() where page != nil {
            pageByRowIndex[index] = page
        }

        let standings: [StandingDraft] = teamRows.enumerated().map { index, row in
            let logoUrl = pageByRowIndex[index]?.logoUrl
            return StandingDraft(
                category: category,
                standingsClass: row.standingsClass,
                type: .team,
                entrantKey: "\(category.rawValue)-\(row.standingsClass.rawValue)-TEAM-\(row.teamName)-\(row.carNumber)",
                position: row.position,
                name: "#\(row.carNumber)",
                team: row.teamName,
                points: row.points,
                photoUrl: logoUrl,
                updatedAtUtc: nowUtc
            )
        }

        let carDrivers = pageByRowIndex.values.flatMap(\.drivers)

        return ImsaFetchResult(standings: standings, carDrivers: carDrivers)
    }

    /// Resuelve el classId de cada shortcode contra la API pública de clases — mapa
    /// vacío (nunca lanza) si esa API fallara, para caer al fallback.
    private func resolveClassIds(year: Int) async -> [String: String] {
        let url = "https://dvw6yynr86g3k.cloudfront.net/galaxy/api/classes?series_id=\(Self.seriesId)&season_id=\(year)"
        guard let html = try? await HTTPClient.fetchHTML(url) else { return [:] }
        return (try? parseClassIds(html)) ?? [:]
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de resolveClassIds() para poder
    // testear el parsing contra un fixture JSON real sin red — ver
    // ImsaStandingsSourceTests.
    func parseClassIds(_ json: String) throws -> [String: String] {
        let classes = try JSONDecoder().decode([ImsaGalaxyClass].self, from: Data(json.utf8))
        var result: [String: String] = [:]
        for entry in classes {
            guard let shortcode = entry.shortcode else { continue }
            result[shortcode.uppercased()] = String(entry.id)
        }
        return result
    }

    private func fetchClassTeamRows(standingsClass: StandingsClass, classId: String, year: Int) async throws -> [TeamRow] {
        let html = try await HTTPClient.postForm(
            Self.ajaxUrl,
            fields: [
                "action": "getImsaStandings",
                "standings": "team",
                "seriesId": Self.seriesId,
                "currentYear": String(year),
                "classId": classId
            ],
            referer: "https://www.imsa.com/weathertech/standings/"
        )

        let doc = try SwiftSoup.parse(html)
        // Solo la tabla "desktop" trae puntos (td.totalpoints) — la "mobile" repite las
        // mismas filas sin esa columna.
        let rows = try doc.select("tr:has(td.totalpoints)").array()
        return try rows.enumerated().compactMap { index, row in
            try parseTeamRow(row, standingsClass: standingsClass, position: index + 1)
        }
    }

    /// "#04 Crowdstrike Racing by APR" -> número de coche + nombre de equipo. Algunas
    /// filas no traen el `<a>` de enlace (equipo sin ficha propia) — se lee igual el
    /// texto de la celda, esa fila se queda sin logo ni pilotos.
    // internal (no private): expuesta a test — ver ImsaStandingsSourceTests. TeamRow pasa
    // a internal por el mismo motivo.
    func parseTeamRow(_ row: Element, standingsClass: StandingsClass, position: Int) throws -> TeamRow? {
        guard let cell = try row.select("td.team-col").first() else { return nil }
        let text = try cell.text().trimmingCharacters(in: .whitespacesAndNewlines)
        guard let match = text.range(of: "^#(\\d+)\\s+(.+)$", options: .regularExpression) else { return nil }
        let matched = String(text[match])
        guard let carNumber = Self.firstGroup(of: "^#(\\d+)", in: matched),
              let teamName = Self.firstGroup(of: "^#\\d+\\s+(.+)$", in: matched)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !teamName.isEmpty else {
            return nil
        }

        let points = try row.select("td.totalpoints").first().flatMap { try? $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(Double.init) ?? 0.0
        let teamUrl = try cell.select("a.team-name").first().flatMap { try? $0.absUrl("href") }.flatMap { $0.isEmpty ? nil : $0 }

        return TeamRow(standingsClass: standingsClass, position: position, carNumber: carNumber, teamName: teamName, points: points, teamUrl: teamUrl)
    }

    private func fetchTeamPage(teamUrl: String, row: TeamRow, nowUtc: Date) async -> TeamPage? {
        do {
            // El Referer evita algún 403 puntual — basta con que la petición diga venir
            // de la propia imsa.com.
            let html = try await HTTPClient.fetchHTML(teamUrl, referer: "https://www.imsa.com/weathertech/teams/")
            return try parseTeamPage(html, teamUrl: teamUrl, standingsClass: row.standingsClass, carNumber: row.carNumber, nowUtc: nowUtc)
        } catch {
            return nil
        }
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetchTeamPage() para poder testear
    // la detección de logo (descarta el logo fijo de la serie y el placeholder "nologo",
    // ninguno de los dos por posición fija) contra un fixture HTML sin red — ver
    // ImsaStandingsSourceTests.
    func parseTeamPage(_ html: String, teamUrl: String, standingsClass: StandingsClass, carNumber: String, nowUtc: Date) throws -> TeamPage {
        let doc = try SwiftSoup.parse(html, teamUrl)

        let logoCandidates = try doc.select(".team-logos img").array().compactMap { try? $0.absUrl("src") }
        let logoUrl = logoCandidates.first {
            !$0.localizedCaseInsensitiveContains("weathertech_championship") && !$0.localizedCaseInsensitiveContains("nologo")
        }

        let cards = try doc.select("div.imsa-card_item_widget").array()
        let drivers: [CarDriverDraft] = try cards.compactMap { card -> CarDriverDraft? in
            let name = try card.select("p.imsa-ciw-title").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !name.isEmpty else { return nil }
            let photoUrl = try card.select("img.imsa-ciw-image").first().flatMap { try? $0.absUrl("data-src") }.flatMap { $0.isEmpty ? nil : $0 }

            return CarDriverDraft(
                category: .imsa,
                standingsClass: standingsClass,
                carNumber: carNumber,
                entryKey: TextNormalizer.normalize(name),
                name: name,
                photoUrl: photoUrl,
                updatedAtUtc: nowUtc
            )
        }

        return TeamPage(logoUrl: logoUrl, drivers: drivers)
    }

    private static func firstGroup(of pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: range), match.numberOfRanges > 1,
              let group = Range(match.range(at: 1), in: text) else {
            return nil
        }
        return String(text[group])
    }

    // internal (no private): expuestas a test — ver parseTeamRow/parseTeamPage arriba.
    struct TeamRow: Sendable {
        var standingsClass: StandingsClass
        var position: Int
        var carNumber: String
        var teamName: String
        var points: Double
        var teamUrl: String?
    }

    struct TeamPage: Sendable {
        var logoUrl: String?
        var drivers: [CarDriverDraft]
    }

    private static let ajaxUrl = "https://www.imsa.com/wp-admin/admin-ajax.php"
    /// WeatherTech SportsCar Championship (id 1; 2 y 3 son las otras dos series de IMSA).
    private static let seriesId = "1"
    /// No más de 8 páginas de equipo a la vez (de las ~45-50 en total).
    private static let teamPageConcurrency = 8

    private static let classShortcodes: [(StandingsClass, String)] = [
        (.gtp, "GTP"),
        (.lmp2, "LMP2"),
        (.gtdPro, "GTD PRO"),
        (.gtd, "GTD")
    ]

    /// Ids comprobados a mano para la temporada 2026 — solo se usan si la API de clases
    /// no responde.
    private static let fallbackClassIds: [StandingsClass: String] = [
        .gtp: "194",
        .lmp2: "196",
        .gtdPro: "192",
        .gtd: "191"
    ]
}

public struct ImsaFetchResult: Sendable {
    public var standings: [StandingDraft]
    public var carDrivers: [CarDriverDraft]
}

struct ImsaGalaxyClass: Decodable, Sendable {
    let id: Int
    let shortcode: String?
}
