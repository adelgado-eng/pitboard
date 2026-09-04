import Foundation
import SwiftSoup

/// nascar.com es JavaScript puro y además bloquea peticiones sin navegador real (403) —
/// mismo problema que resuelve `NascarStandingsSource` pasándose a espn.com. Equivalente
/// exacto de `EspnNascarScheduleSource.kt`:
/// espn.com/racing/schedule/_/series/{slug} es HTML clásico (tabla sin JS).
///
/// HONESTO: esta tabla es la agenda de EMISIÓN (carreras, incluidos "Duels"/exhibiciones),
/// no distingue libres/clasificación como sesiones aparte — todo se etiqueta como Carrera.
public final class EspnNascarScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries
    private let espnSeriesSlug: String

    public init(series: RaceSeries, slug: String) {
        self.series = series
        self.espnSeriesSlug = slug
    }

    public func fetch() async throws -> [EventDraft] {
        let url = "https://www.espn.com/racing/schedule/_/series/\(espnSeriesSlug)"
        let html = try await HTTPClient.fetchHTML(url)
        return try parseHTML(html)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // troceo de celdas por <br> contra un fixture HTML sin red — ver
    // EspnNascarScheduleSourceTests.
    func parseHTML(_ html: String) throws -> [EventDraft] {
        let doc = try SwiftSoup.parse(html)
        guard let table = try doc.select("table.tablehead").first() else { return [] }

        let rows = try table.select("tr.oddrow, tr.evenrow").array()
        var results: [EventDraft] = []

        for (index, row) in rows.enumerated() {
            let cells = try row.select("td").array()
            guard cells.count > 1 else { continue }
            let dateCell = cells[0]
            let raceCell = cells[1]

            let dateLines = try cellLines(dateCell)
            guard let firstDateLine = dateLines.first else { continue }
            let dateText: String
            if let commaIndex = firstDateLine.firstIndex(of: ",") {
                dateText = String(firstDateLine[firstDateLine.index(after: commaIndex)...])
                    .trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                dateText = firstDateLine
            }
            let timeText = dateLines.count > 1 ? dateLines[1] : nil

            let raceLines = try cellLines(raceCell)
            let boldText = try raceCell.select("b").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard let raceName = (boldText?.isEmpty == false ? boldText : nil) ?? raceLines.first else { continue }
            let circuitName = raceLines.first { $0 != raceName && !$0.hasPrefix("*") } ?? ""

            guard let startTimeUtc = UsScheduleDateParsing.toDate(dateText: dateText, timeText: timeText) else { continue }

            // Nota: `hashValue` de Swift no coincide con `String.hashCode()` de la JVM —
            // no importa, solo hace falta que el uid sea estable DENTRO de esta misma
            // fuente (nunca se compara entre plataformas).
            results.append(EventDraft(
                series: series,
                uid: "\(series.rawValue)-\(index)-\(raceName.hashValue)",
                fullTitle: "\(series.displayName) - \(raceName) - \(circuitName) - Carrera",
                startTimeUtc: startTimeUtc,
                timeZoneId: UsScheduleDateParsing.eastZoneId(),
                inferredBadge: SessionBadgeType.race.rawValue
            ))
        }
        return results
    }

    /// ESPN separa fecha/hora y nombre/circuito con `<br>` dentro de la misma celda, sin
    /// ninguna otra marca — `Element.text()` los uniría en una sola línea con espacios,
    /// así que hay que trocear a mano por cada salto de línea.
    private func cellLines(_ cell: Element) throws -> [String] {
        var lines: [String] = [""]
        for node in cell.getChildNodes() {
            if let element = node as? Element {
                if element.tagName() == "br" {
                    lines.append("")
                } else {
                    lines[lines.count - 1] += try element.text()
                }
            } else if let textNode = node as? TextNode {
                lines[lines.count - 1] += textNode.text()
            }
        }
        // ESPN separa "Wed" de "Feb" de "4" con &nbsp; (U+00A0) en vez de un espacio
        // normal — se normaliza explícitamente por punto de código.
        return lines
            .map { line in
                String(String.UnicodeScalarView(line.unicodeScalars.map { $0.value == 0x00A0 ? " " : $0 }))
            }
            .map { $0.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}
