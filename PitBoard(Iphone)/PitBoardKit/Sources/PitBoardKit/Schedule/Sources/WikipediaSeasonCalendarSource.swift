import Foundation
import SwiftSoup

/// Fuente genérica por Wikipedia — equivalente exacto de
/// `WikipediaSeasonCalendarSource.kt`. Se usa para series cuya web oficial es una SPA sin
/// datos de sesiones en el HTML servido y para las que no hay ninguna otra fuente
/// estructurada con hora exacta (hoy solo Porsche Supercup).
///
/// Wikipedia mantiene un artículo con una tabla de calendario (columnas Round/Rnd./Date/
/// Circuit/Race o similar, detectadas por su encabezado igual que
/// `MotorsportStandingsHTMLSource` hace con las tablas de clasificación).
///
/// HONESTO — dos limitaciones aceptadas conscientemente:
/// 1. Wikipedia da la fecha de la ronda, no la hora de cada sesión — cada ronda se
///    guarda como una única sesión "Carrera" a mediodía UTC (hora de referencia, no
///    real).
/// 2. Cuando la fecha es un rango de dos días se toma el ÚLTIMO día como fecha de
///    carrera — es una heurística, no siempre exacta para eventos con formato atípico.
public final class WikipediaSeasonCalendarSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries

    /// Sufijo del título del artículo, sin el año (ej. "Porsche_Supercup" para
    /// "2026_Porsche_Supercup") — ignorado si se indica `explicitArticleTitle`.
    private let wikipediaSlug: String

    /// Título completo del artículo tal cual, para series cuya temporada cruza dos años
    /// naturales (ej. Fórmula E: "2026–27_Formula_E_World_Championship") y por tanto no
    /// encajan en el patrón "{año} {serie}". HONESTO: a diferencia del cálculo
    /// automático con el año actual, esto hay que actualizarlo a mano cada temporada.
    private let explicitArticleTitle: String?

    public init(series: RaceSeries, wikipediaSlug: String, explicitArticleTitle: String? = nil) {
        self.series = series
        self.wikipediaSlug = wikipediaSlug
        self.explicitArticleTitle = explicitArticleTitle
    }

    public func fetch() async throws -> [EventDraft] {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let title = explicitArticleTitle ?? "\(year)_\(wikipediaSlug)"
        let url = "https://en.wikipedia.org/wiki/\(title)"
        let html = try await HTTPClient.fetchHTML(url)
        return try parseHTML(html, year: year)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear los dos
    // bugs reales que el propio comentario de esta clase documenta como corregidos a mano
    // (año duplicado en la fecha, filas con celdas fusionadas por rowspan) contra un
    // fixture HTML sin red — ver WikipediaSeasonCalendarSourceTests. El año se recibe
    // como parámetro (no calculado con Date() internamente) para que el test sea
    // determinista sin importar cuándo se ejecute.
    func parseHTML(_ html: String, year: Int) throws -> [EventDraft] {
        let url = "https://en.wikipedia.org/wiki/placeholder"
        let doc = try SwiftSoup.parse(html, url)

        let tables = try doc.select("table.wikitable").array()
        guard let table = try tables.first(where: { candidate in
            let headerTexts = try headerRowTexts(candidate)
            let hasDate = headerTexts.contains { $0.localizedCaseInsensitiveContains("date") }
            let hasRaceish = headerTexts.contains {
                $0.localizedCaseInsensitiveContains("circuit")
                    || $0.localizedCaseInsensitiveContains("race")
                    || $0.localizedCaseInsensitiveContains("round")
                    || $0.localizedCaseInsensitiveContains("rnd")
            }
            return hasDate && hasRaceish
        }) else {
            return []
        }

        let headers = try headerRowTexts(table)
        guard let dateIndex = headers.firstIndex(where: { $0.localizedCaseInsensitiveContains("date") }) else { return [] }
        let raceIndex = headers.firstIndex {
            $0.localizedCaseInsensitiveContains("grand prix")
                || $0.localizedCaseInsensitiveContains("e-prix")
                || $0.localizedCaseInsensitiveContains("race")
                || $0.localizedCaseInsensitiveContains("event")
        }
        let circuitIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("circuit") }

        // La primera fila de la rejilla es la propia cabecera — se descarta.
        let grid = Array(try expandToGrid(table).dropFirst())

        var results: [EventDraft] = []
        for (index, row) in grid.enumerated() {
            guard dateIndex < row.count else { continue }
            let dateText = row[dateIndex].trimmingCharacters(in: .whitespacesAndNewlines)
            guard let startTimeUtc = parseRaceDate(dateText, year: year) else { continue }

            let rawRaceName = raceIndex.flatMap { $0 < row.count ? row[$0].trimmingCharacters(in: .whitespacesAndNewlines) : nil } ?? ""
            let raceName = rawRaceName.isEmpty ? "Ronda \(index + 1)" : rawRaceName
            let circuitName = circuitIndex.flatMap { $0 < row.count ? row[$0].trimmingCharacters(in: .whitespacesAndNewlines) : nil } ?? ""

            results.append(EventDraft(
                series: series,
                uid: "\(series.rawValue)-WIKI-\(year)-R\(index + 1)",
                fullTitle: "\(series.displayName) - \(raceName) - \(circuitName) - Carrera",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeType.race.rawValue
            ))
        }
        return results
    }

    /// Cabecera real de la tabla (solo la primera fila) — a diferencia de un
    /// `table.select("th")` a pelo, que también cogería el número de ronda de cada fila
    /// de datos (Wikipedia lo marca como `<th>` de fila, no de columna).
    private func headerRowTexts(_ table: Element) throws -> [String] {
        guard let headerRow = try table.select("tr").first() else { return [] }
        return try headerRow.select("th, td").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    /// Expande la tabla (cabecera incluida, en la posición 0) a una rejilla completa,
    /// rellenando hacia abajo las celdas que una fila posterior "hereda" por `rowspan`
    /// — sin esto, una fila cuyo circuito viene fusionado con la de arriba solo trae sus
    /// propias celdas y todo lo demás se lee desplazado o desaparece. No se contempla
    /// `colspan` — no se ha visto en ninguna tabla de calendario real.
    private func expandToGrid(_ table: Element) throws -> [[String]] {
        let rows = try table.select("tr").array()
        var grid: [[String]] = []
        // Columna -> (filas restantes incluyendo esta, texto) para celdas con rowspan activo.
        var pending: [Int: (remaining: Int, text: String)] = [:]

        for row in rows {
            let cells = try row.select("th, td").array()
            var outputRow: [String] = []
            var cellPtr = 0
            var col = 0

            while cellPtr < cells.count || pending.keys.contains(where: { $0 >= col }) {
                if let active = pending[col] {
                    outputRow.append(active.text)
                    let remaining = active.remaining - 1
                    if remaining <= 0 {
                        pending.removeValue(forKey: col)
                    } else {
                        pending[col] = (remaining, active.text)
                    }
                } else {
                    guard cellPtr < cells.count else { break }
                    let cell = cells[cellPtr]
                    cellPtr += 1
                    let text = try cell.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    outputRow.append(text)
                    let rowspan = Int((try? cell.attr("rowspan")) ?? "") ?? 1
                    if rowspan > 1 {
                        pending[col] = (rowspan - 1, text)
                    }
                }
                col += 1
            }
            grid.append(outputRow)
        }
        return grid
    }

    /// Mediodía UTC del día de carrera resuelto — ver limitación 1 en el comentario de clase.
    private func parseRaceDate(_ raw: String, year: Int) -> Date? {
        guard let date = resolveDate(raw, year: year) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        components.hour = 12
        components.timeZone = TimeZone(identifier: "UTC")
        return calendar.date(from: components)
    }

    private func resolveDate(_ raw: String, year: Int) -> Date? {
        // &nbsp; (U+00A0) y los guiones largos que usa Wikipedia para separar rangos de
        // fecha se normalizan por punto de código, no por el carácter literal.
        var text = raw.replacingOccurrences(of: "\\[[0-9]+\\]", with: "", options: .regularExpression)
        text = String(text.unicodeScalars.map { scalar -> Character in
            switch scalar.value {
            case 0x00A0: return " "
            case 0x2013, 0x2014: return "-"
            default: return Character(scalar)
            }
        })
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        // "6/7 April" (ELMS): el día de carrera es el segundo número, mismo mes.
        if let regex = try? NSRegularExpression(pattern: "(\\d{1,2})/(\\d{1,2})\\s+([A-Za-z]+)"),
           let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..<text.endIndex, in: text)),
           match.numberOfRanges >= 4,
           let dayRange = Range(match.range(at: 2), in: text),
           let monthRange = Range(match.range(at: 3), in: text) {
            text = "\(text[dayRange]) \(text[monthRange])"
        } else if text.contains("-") {
            // "January 24-25" o "27 Feb-1 Mar": nos quedamos con el segundo tramo; si no
            // trae su propio mes, se lo tomamos prestado del primero.
            let parts = text.components(separatedBy: "-").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            let first = parts.first ?? ""
            let last = parts.count > 1 ? parts[1] : ""
            if last.contains(where: { $0.isLetter }) {
                text = last
            } else if let monthRange = first.range(of: "[A-Za-z]+", options: .regularExpression) {
                text = "\(last) \(first[monthRange])"
            } else {
                text = last
            }
        }

        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        // Porsche Supercup nunca trae el año en la celda ("6 April"), pero otras series sí
        // ("18 December 2026") — añadir el año siempre duplicaría ese caso. Solo se añade
        // si el texto no termina ya en un año de 4 cifras.
        let hasYearSuffix = text.range(of: "\\d{4}$", options: .regularExpression) != nil
        let candidate = hasYearSuffix ? text : "\(text) \(year)"

        for format in Self.dateFormats {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = format
            formatter.timeZone = TimeZone(identifier: "UTC")
            if let date = formatter.date(from: candidate) {
                return date
            }
        }
        return nil
    }

    private static let dateFormats = ["d MMMM yyyy", "MMMM d yyyy", "d MMM yyyy", "MMM d yyyy"]
}
