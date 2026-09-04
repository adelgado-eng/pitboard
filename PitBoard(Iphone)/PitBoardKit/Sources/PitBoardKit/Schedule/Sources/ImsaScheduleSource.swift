import Foundation
import SwiftSoup

/// imsa.com bloquea peticiones sospechosas en algunas rutas, pero la página del
/// calendario y las de cada evento son HTML normal (WordPress) sin JavaScript —
/// equivalente exacto de `ImsaScheduleSource.kt`. Dos pasos:
/// 1. `weathertech/weathertech-{año}-schedule/` trae los enlaces "Event Details" de
///    cada ronda.
/// 2. Cada evento (`imsa.com/events/{slug}/`) tiene una sección "Event Schedule" en
///    HTML plano: un `.day-event-header` por día seguido de varios
///    `.day-event-details-container`, cada uno con la hora ("10:05 AM to 11:35 AM ET")
///    y el nombre de sesión.
///
/// HONESTO: esa sección de horario mezcla la clase principal (WeatherTech
/// Championship) con las de apoyo (Mazda MX-5 Cup, Michelin Pilot Challenge...) sin
/// ningún marcado que las separe más que el propio texto del nombre — se filtra por
/// palabras clave, así que una sesión de una clase de apoyo con un nombre atípico
/// podría colarse. Solo se guarda la hora de inicio del rango, no el final.
public final class ImsaScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries = .imsa

    public init() {}

    public func fetch() async throws -> [EventDraft] {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let listingUrl = "https://www.imsa.com/weathertech/weathertech-\(year)-schedule/"
        let listingHtml = try await HTTPClient.fetchHTML(listingUrl)
        let listingDoc = try SwiftSoup.parse(listingHtml, listingUrl)

        let links = try listingDoc.select("a:contains(Event Details)").array()
        var seen = Set<String>()
        var eventUrls: [String] = []
        for link in links {
            guard let href = try? link.attr("abs:href"), !href.isEmpty, seen.insert(href).inserted else { continue }
            eventUrls.append(href)
        }

        var allSessions: [EventDraft] = []
        for url in eventUrls {
            if let sessions = try? await sessionsForEvent(url) {
                allSessions.append(contentsOf: sessions)
            }
        }
        return allSessions
    }

    private func sessionsForEvent(_ eventUrl: String) async throws -> [EventDraft] {
        let html = try await HTTPClient.fetchHTML(eventUrl)
        let doc = try SwiftSoup.parse(html, eventUrl)

        let trimmed = eventUrl.hasSuffix("/") ? String(eventUrl.dropLast()) : eventUrl
        let slug = trimmed.components(separatedBy: "/").last ?? ""

        // La página no tiene <h1> — el nombre de la ronda sale del <title> ("2026 Rolex
        // 24 At DAYTONA | IMSA"), quitando el sufijo del sitio y el año inicial.
        let titleText = (try? doc.title()) ?? ""
        let beforePipe = titleText.components(separatedBy: "|").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let roundName = beforePipe.replacingOccurrences(of: "^\\d{4}\\s+", with: "", options: .regularExpression)

        guard let container = try doc.select(".race-event-schedule-container-inner").first() else { return [] }

        var currentDate: DateComponents?
        var results: [EventDraft] = []
        var index = 0

        let children = container.children().array()
        for child in children {
            if child.hasClass("day-event-header") {
                currentDate = parseHeaderDate(try child.text())
            } else if child.hasClass("day-event-details-container") {
                guard let date = currentDate else { continue }
                let timeText = try child.select(".event-time").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !timeText.isEmpty else { continue }
                let name = try child.select(".event-name").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !name.isEmpty, isMainClassSession(name) else { continue }
                guard let time = parseRangeStartTime(timeText) else { continue }

                var components = date
                components.hour = time.hour
                components.minute = time.minute
                var calendar = Calendar(identifier: .gregorian)
                calendar.timeZone = Self.eastern
                guard let startTimeUtc = calendar.date(from: components) else { continue }

                results.append(EventDraft(
                    series: .imsa,
                    uid: "IMSA-\(slug)-\(index)",
                    fullTitle: "\(RaceSeries.imsa.displayName) - \(roundName) - \(name)",
                    startTimeUtc: startTimeUtc,
                    timeZoneId: Self.eastern.identifier,
                    inferredBadge: SessionBadgeMatcher.match(name)
                ))
                index += 1
            }
        }

        return results
    }

    /// Las clases de apoyo se nombran explícitamente ("... - Mazda MX-5 Cup") — si el
    /// nombre no menciona ninguna de las conocidas, se asume que es de la clase
    /// principal (WeatherTech Championship), que a veces aparece sin sufijo.
    private func isMainClassSession(_ name: String) -> Bool {
        let t = name.lowercased()
        return !Self.supportSeries.contains { t.contains($0) }
    }

    private func parseHeaderDate(_ text: String) -> DateComponents? {
        guard let commaRange = text.range(of: ",") else { return nil }
        let cleaned = String(text[commaRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "MMMM d, yyyy"
        formatter.timeZone = TimeZone(identifier: "UTC")
        guard let date = formatter.date(from: cleaned) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.dateComponents([.year, .month, .day], from: date)
    }

    /// "10:05 AM to 11:35 AM ET" -> 10:05 AM (solo el inicio).
    private func parseRangeStartTime(_ text: String) -> (hour: Int, minute: Int)? {
        let start = text.components(separatedBy: " to ").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? text
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h:mm a"
        formatter.timeZone = TimeZone(identifier: "UTC")
        guard let date = formatter.date(from: start) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let comps = calendar.dateComponents([.hour, .minute], from: date)
        guard let hour = comps.hour, let minute = comps.minute else { return nil }
        return (hour, minute)
    }

    private static let eastern = TimeZone(identifier: "America/New_York")!
    private static let supportSeries = [
        "mazda mx-5 cup",
        "michelin pilot challenge",
        "vp racing sportscar challenge",
        "porsche carrera cup",
        "ferrari challenge",
        "lamborghini",
        "radical cup"
    ]
}
