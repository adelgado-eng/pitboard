import Foundation
import SwiftSoup

/// indycar.com/schedule es una web normal (no una SPA), sin año en la URL. Equivalente
/// exacto de `IndyCarScheduleSource.kt`.
///
/// HONESTO: la tarjeta solo trae la hora de la carrera principal, no libres/clasificación
/// por separado (viven en la página propia de cada evento, un salto que no se hace aquí)
/// — por ahora IndyCar solo aporta la sesión de carrera.
public final class IndyCarScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries = .indycar

    public init() {}

    public func fetch() async throws -> [EventDraft] {
        let html = try await HTTPClient.fetchHTML("https://www.indycar.com/schedule")
        let doc = try SwiftSoup.parse(html)
        let cards = try doc.select("div.event-card").array()

        var results: [EventDraft] = []
        for (index, card) in cards.enumerated() {
            // Las tarjetas de carreras ya disputadas llevan "completed" en la clase — no
            // interesan (el resto de la app ya filtra por fecha, pero estas ni siquiera
            // traen countdown).
            let classes = try card.className()
            if classes.contains("completed") { continue }

            guard let dateText = try card.select(".event-card-header-date").first()?.text() else { continue }
            let timeText = try card.select(".event-card-header-time").first()?.text()
            let rawTitle = try card.select(".event-card-title").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !rawTitle.isEmpty else { continue }
            let trackName = try card.select(".event-card-track-name").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

            guard let startTimeUtc = UsScheduleDateParsing.toDate(dateText: dateText, timeText: timeText) else { continue }

            results.append(EventDraft(
                series: .indycar,
                uid: "INDYCAR-\(index)-\(rawTitle.hashValue)",
                fullTitle: "\(RaceSeries.indycar.displayName) - \(rawTitle) - \(trackName) - Carrera",
                startTimeUtc: startTimeUtc,
                timeZoneId: UsScheduleDateParsing.eastZoneId(),
                inferredBadge: SessionBadgeType.race.rawValue
            ))
        }
        return results
    }
}
