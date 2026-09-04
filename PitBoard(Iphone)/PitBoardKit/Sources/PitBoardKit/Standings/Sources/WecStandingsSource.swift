import Foundation
import SwiftSoup

/// WEC (FIA World Endurance Championship): igual que ELMS, se trata "por coche" — pero
/// solo tiene 2 clases puntuables: **Hypercar** y **LMGT3** (LMP2 se retiró como clase de
/// campeonato de WEC tras 2023). Equivalente exacto de `WecStandingsSource.kt`.
///
/// Fuente: fiawec.com/en/page/manufacturers-classification — una sola página con varias
/// tablas colapsables, cada una con su propio `id` enlazado desde el botón que la
/// despliega. Se busca cada tabla por el TEXTO EXACTO de su botón y se entra por ese
/// `id` (no por posición en el documento).
///
/// Las dos clases comparten forma de fila (Pos. | logo | N° | ... | Total points), pero
/// la columna 4 significa cosas distintas: en Hypercar es el nombre del PILOTO (esa tabla
/// puntúa pilotos, no coches — un mismo número de coche aparece dos veces si un piloto se
/// perdió alguna carrera; se agrupa por número de coche y se toma el piloto con más
/// puntos como representante) y el "equipo" guardado es el FABRICANTE; en LMGT3 es el
/// nombre de equipo real, con el logo del fabricante del coche (esta web no publica un
/// logo de equipo aparte en esa tabla).
public final class WecStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory = .wec
    private let pageUrl = "https://www.fiawec.com/en/page/manufacturers-classification"

    public init() {}

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let html = try await HTTPClient.fetchHTML(pageUrl)
        let doc = try SwiftSoup.parse(html, pageUrl)

        var result: [StandingDraft] = []
        if let hypercarSection = try findSection(doc, buttonText: "FIA Hypercar World Endurance Drivers Championship") {
            result += try parseRows(hypercarSection, standingsClass: .hypercar, nowUtc: nowUtc) { cells in
                guard cells.indices.contains(1) else { return nil }
                return try cells[1].select("img").first()?.attr("alt")
            }
        }
        if let lmgt3Section = try findSection(doc, buttonText: "FIA Endurance Trophy for LMGT3 Teams") {
            result += try parseRows(lmgt3Section, standingsClass: .lmgt3, nowUtc: nowUtc) { cells in
                guard cells.indices.contains(3) else { return nil }
                return try cells[3].text()
            }
        }
        return result
    }

    /// Busca el botón cuyo texto sea EXACTAMENTE `buttonText` y entra a su sección
    /// colapsable por el `id` que declara en `data-bs-target` — nil si esta temporada
    /// cambiara el texto del botón o desapareciera esa sección.
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

    private struct ParsedRow {
        var carNumber: String
        var team: String
        var logoUrl: String?
        var points: Double
    }

    /// Fila común a las dos tablas: Pos. | logo (columna 1) | N° (columna 2) | ... |
    /// Total points (última celda) — `teamText` decide qué poner como "equipo" según la
    /// tabla.
    private func parseRows(
        _ section: Element,
        standingsClass: StandingsClass,
        nowUtc: Date,
        teamText: (_ cells: [Element]) throws -> String?
    ) throws -> [StandingDraft] {
        guard let table = try section.select("table").first() else { return [] }

        let rows = try table.select("tbody tr").array()
        var parsed: [ParsedRow] = []
        for row in rows {
            let cells = try row.select("td").array()
            guard cells.count >= 4 else { continue }

            let rawCarNumber = try cells[2].text().trimmingCharacters(in: .whitespacesAndNewlines)
            let carNumber = rawCarNumber.hasPrefix("#") ? String(rawCarNumber.dropFirst()) : rawCarNumber
            guard !carNumber.isEmpty else { continue }

            guard let team = try teamText(cells)?.trimmingCharacters(in: .whitespacesAndNewlines), !team.isEmpty else { continue }

            let logoUrl = try cells[1].select("img").first().flatMap { try? $0.absUrl("src") }.flatMap { $0.isEmpty ? nil : $0 }
            let points = cells.last.flatMap { try? $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
                .flatMap(Double.init) ?? 0.0

            parsed.append(ParsedRow(carNumber: carNumber, team: team, logoUrl: logoUrl, points: points))
        }

        // Un coche por fila: si el mismo número aparece más de una vez (Hypercar), se
        // queda el piloto con más puntos, y se reordena por puntos descendente (quitar
        // duplicados puede alterar el orden original de la tabla).
        var bestByCarNumber: [String: ParsedRow] = [:]
        for row in parsed {
            if let existing = bestByCarNumber[row.carNumber], existing.points >= row.points { continue }
            bestByCarNumber[row.carNumber] = row
        }
        let deduped = bestByCarNumber.values.sorted { $0.points > $1.points }

        return deduped.enumerated().map { index, row in
            StandingDraft(
                category: category,
                standingsClass: standingsClass,
                type: .team,
                entrantKey: "\(category.rawValue)-\(standingsClass.rawValue)-TEAM-\(row.team)-\(row.carNumber)",
                position: index + 1,
                name: "#\(row.carNumber)",
                team: row.team,
                points: row.points,
                photoUrl: row.logoUrl,
                updatedAtUtc: nowUtc
            )
        }
    }
}
