import Foundation
import SwiftData
@testable import PitBoardKit

/// `ModelContainer` en memoria (nunca toca disco ni el App Group real) — cada test que lo
/// use parte de una base vacía y aislada de las demás.
func makeInMemoryContainer() -> ModelContainer {
    let schema = Schema([EventModel.self, SeriesConfigModel.self, StandingModel.self, CarDriverModel.self])
    let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
    return try! ModelContainer(for: schema, configurations: [configuration])
}

func makeEvent(
    series: RaceSeries = .f1,
    uid: String = UUID().uuidString,
    fullTitle: String = "Formula 1 - GP de Ejemplo - Carrera",
    startTimeUtc: Date,
    timeZoneId: String? = nil,
    inferredBadge: String = SessionBadgeType.race.rawValue
) -> EventDraft {
    EventDraft(series: series, uid: uid, fullTitle: fullTitle, startTimeUtc: startTimeUtc, timeZoneId: timeZoneId, inferredBadge: inferredBadge)
}
