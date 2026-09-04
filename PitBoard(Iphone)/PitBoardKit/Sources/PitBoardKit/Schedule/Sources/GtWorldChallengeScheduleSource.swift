import Foundation
import SwiftSoup

/// Las 4 variantes de GT World Challenge (Europa/América/Asia/Australia) las organiza el
/// mismo promotor (SRO Motorsports Group) y comparten literalmente la misma web, solo
/// con el dominio cambiado por región — equivalente exacto de
/// `GtWorldChallengeScheduleSource.kt`.
///
/// 1. `{baseUrl}/calendar` trae un JSON-LD `ItemList` con la URL de cada evento de la
///    temporada actual.
/// 2. Cada evento tiene una sección "Timetable" en HTML normal (sin JS): una tabla por
///    día, con el día en el `<caption>` ("Friday, 18 September") y filas Sesión, Hora
///    local y GMT. Se usa la columna GMT directamente como hora UTC.
///
/// HONESTO: el nombre de sesión a veces viene vacío en la web de origen — esas filas se
/// guardan igual con una etiqueta genérica "Sesión N" en vez de descartarse, para no
/// perder la hora.
public final class GtWorldChallengeScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries
    private let baseUrl: String

    public init(series: RaceSeries, baseUrl: String) {
        self.series = series
        self.baseUrl = baseUrl
    }

    public func fetch() async throws -> [EventDraft] {
        let calendarHtml = try await HTTPClient.fetchHTML("\(baseUrl)/calendar")
        let eventUrls = try extractItemListUrls(calendarHtml)

        var allSessions: [EventDraft] = []
        for url in eventUrls {
            if let sessions = try? await sessionsForEvent(url) {
                allSessions.append(contentsOf: sessions)
            }
        }
        return allSessions
    }

    private func extractItemListUrls(_ calendarHtml: String) throws -> [String] {
        let doc = try SwiftSoup.parse(calendarHtml, baseUrl)
        let scripts = try doc.select("script[type=application/ld+json]").array()
        for script in scripts {
            guard let data = script.data().data(using: .utf8) else { continue }
            guard let list = try? JSONDecoder().decode(JsonLdItemList.self, from: data),
                  let items = list.itemListElement else { continue }
            return items.compactMap(\.url)
        }
        return []
    }

    private func sessionsForEvent(_ eventUrl: String) async throws -> [EventDraft] {
        let html = try await HTTPClient.fetchHTML(eventUrl)
        let doc = try SwiftSoup.parse(html, eventUrl)

        let trimmed = eventUrl.hasSuffix("/") ? String(eventUrl.dropLast()) : eventUrl
        let slug = trimmed.components(separatedBy: "/").last ?? ""
        let roundName = try doc.select("h1").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())

        var sessions: [(name: String, startTimeUtc: Date)] = []
        let tables = try doc.select("table.timetable__table").array()
        for table in tables {
            let captionText = try table.select(".timetable__caption span").first()?.text()
                ?? (try table.select("caption").first()?.text())
            guard let captionText, let dateComponents = parseCaptionDate(captionText, year: year) else { continue }

            let headers = try table.select("thead th").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
            guard let gmtIndex = headers.firstIndex(where: { $0.localizedCaseInsensitiveContains("GMT") }) else { continue }

            let rows = try table.select("tbody tr").array()
            for (rowIndex, row) in rows.enumerated() {
                let cells = try row.select("td").array()
                guard cells.indices.contains(gmtIndex) else { continue }
                let gmtText = try cells[gmtIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
                guard let time = parseHourMinute(gmtText) else { continue }
                let rawName = try cells.first?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let sessionName = rawName.isEmpty ? "Sesión \(rowIndex + 1)" : rawName

                var components = dateComponents
                components.hour = time.hour
                components.minute = time.minute
                var calendar = Calendar(identifier: .gregorian)
                calendar.timeZone = TimeZone(identifier: "UTC")!
                guard let startTimeUtc = calendar.date(from: components) else { continue }

                sessions.append((sessionName, startTimeUtc))
            }
        }

        return sessions.enumerated().map { index, entry in
            EventDraft(
                series: series,
                uid: "\(series.rawValue)-\(slug)-\(index)",
                fullTitle: "\(series.displayName) - \(roundName) - \(entry.name)",
                startTimeUtc: entry.startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeMatcher.match(entry.name)
            )
        }
    }

    /// "Friday, 18 September" -> componentes de fecha del año en curso (estas webs solo
    /// publican la temporada actual, no hace falta lógica de "saltar al año siguiente").
    private func parseCaptionDate(_ text: String, year: Int) -> DateComponents? {
        guard let commaRange = text.range(of: ",") else { return nil }
        let dayMonth = String(text[commaRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "d MMMM yyyy"
        formatter.timeZone = TimeZone(identifier: "UTC")
        guard let date = formatter.date(from: "\(dayMonth) \(year)") else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.dateComponents([.year, .month, .day], from: date)
    }

    private func parseHourMinute(_ text: String) -> (hour: Int, minute: Int)? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "H:mm"
        formatter.timeZone = TimeZone(identifier: "UTC")
        guard let date = formatter.date(from: text.trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let comps = calendar.dateComponents([.hour, .minute], from: date)
        guard let hour = comps.hour, let minute = comps.minute else { return nil }
        return (hour, minute)
    }
}

private struct JsonLdItemList: Decodable {
    let itemListElement: [JsonLdListItem]?
}

private struct JsonLdListItem: Decodable {
    let url: String?
}
