import Foundation
import SwiftSoup

/// Le Mans Cup (Michelin Le Mans Cup): misma organización que WEC (ACO) y mismo
/// mecanismo para encontrar cada tabla — por el texto exacto del botón que la despliega,
/// entrando por su `id` (`data-bs-target`). Equivalente exacto de
/// `LeMansCupStandingsSource.kt`.
///
/// A diferencia de WEC, esta tabla NO trae columna de logo: la cabecera es literalmente
/// "Pos. | N° | Team | ... | Total points" (sin la columna de logo entre "Pos." y "N°"
/// que sí tiene WEC) — los índices son distintos de `WecStandingsSource` a propósito, no
/// es un error. Como no hay logo en esta tabla, se saca de otra página —
/// lemanscup.com/en/car/{año}, el mismo listado que usa `AcoCarDriversSource` para los
/// pilotos — cruzando por número de coche.
///
/// Solo tiene LMP3, LMP3 Pro/Am y GT3 esta temporada (no LMP2, a diferencia de ELMS).
public final class LeMansCupStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory = .lemansCup
    private let classificationUrl = "https://www.lemanscup.com/en/page/classification"
    private var gridUrl: String {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        return "https://www.lemanscup.com/en/car/\(year)"
    }

    private let sections: [(String, StandingsClass)] = [
        ("LMP3 Pro/Am Teams Classification", .lmp3ProAm),
        ("LMP3 Teams Classification", .lmp3),
        ("GT3 Teams Classification", .gt3)
    ]

    public init() {}

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let classificationHtml = try await HTTPClient.fetchHTML(classificationUrl)
        let classificationDoc = try SwiftSoup.parse(classificationHtml, classificationUrl)
        let logoByCarNumber = (try? await fetchLogosByCarNumber()) ?? [:]

        var result: [StandingDraft] = []
        for (buttonText, standingsClass) in sections {
            if let section = try findSection(classificationDoc, buttonText: buttonText) {
                result += try parseRows(section, standingsClass: standingsClass, logoByCarNumber: logoByCarNumber, nowUtc: nowUtc)
            }
        }
        return result
    }

    /// Logo de equipo por número de coche, sacado de la página de listado — mapa vacío
    /// (nunca lanza) si esa página fallara, para no tumbar toda la clasificación solo
    /// porque los logos no se pudieran obtener.
    private func fetchLogosByCarNumber() async throws -> [String: String] {
        let html = try await HTTPClient.fetchHTML(gridUrl)
        let doc = try SwiftSoup.parse(html, gridUrl)
        let cards = try doc.select("div.card-team").array()

        var result: [String: String] = [:]
        for card in cards {
            let carUrl: String = (try? card.select("a.stretched-link").first()?.attr("href")).flatMap { $0 } ?? ""
            let trimmed = carUrl.hasSuffix("/") ? String(carUrl.dropLast()) : carUrl
            let carNumber = trimmed.components(separatedBy: "/").last ?? ""
            guard Int(carNumber) != nil else { continue }
            guard let logoUrl = try card.select("div.brand-logo img").first().flatMap({ try? $0.absUrl("src") }), !logoUrl.isEmpty else {
                continue
            }
            result[carNumber] = logoUrl
        }
        return result
    }

    private func findSection(_ doc: Document, buttonText: String) throws -> Element? {
        let buttons = try doc.select("button[data-bs-target]").array()
        guard let button = try buttons.first(where: { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) == buttonText }) else {
            return nil
        }
        let target = try button.attr("data-bs-target")
        let targetId = target.hasPrefix("#") ? String(target.dropFirst()) : target
        guard !targetId.isEmpty else { return nil }
        return try doc.getElementById(targetId)
    }

    /// Fila real: Pos. (0) | N° (1) | Team (2) | ... puntos por carrera ... | Total
    /// points (última celda) — SIN columna de logo, a diferencia de `WecStandingsSource`.
    private func parseRows(
        _ section: Element,
        standingsClass: StandingsClass,
        logoByCarNumber: [String: String],
        nowUtc: Date
    ) throws -> [StandingDraft] {
        guard let table = try section.select("table").first() else { return [] }

        let rows = try table.select("tbody tr").array()
        var parsed: [(carNumber: String, team: String, points: Double)] = []
        for row in rows {
            let cells = try row.select("td").array()
            guard cells.count >= 3 else { continue }

            let rawCarNumber = try cells[1].text().trimmingCharacters(in: .whitespacesAndNewlines)
            let carNumber = rawCarNumber.hasPrefix("#") ? String(rawCarNumber.dropFirst()) : rawCarNumber
            guard !carNumber.isEmpty else { continue }

            let team = try cells[2].text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard !team.isEmpty else { continue }

            let points = cells.last.flatMap { try? $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
                .flatMap(Double.init) ?? 0.0

            parsed.append((carNumber, team, points))
        }

        return parsed.enumerated().map { index, entry in
            StandingDraft(
                category: category,
                standingsClass: standingsClass,
                type: .team,
                entrantKey: "\(category.rawValue)-\(standingsClass.rawValue)-TEAM-\(entry.team)-\(entry.carNumber)",
                position: index + 1,
                name: "#\(entry.carNumber)",
                team: entry.team,
                points: entry.points,
                photoUrl: logoByCarNumber[entry.carNumber],
                updatedAtUtc: nowUtc
            )
        }
    }
}
