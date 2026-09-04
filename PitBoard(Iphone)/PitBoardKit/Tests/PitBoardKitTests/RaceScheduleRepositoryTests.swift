import XCTest
import SwiftData
@testable import PitBoardKit

private struct FakeScheduleSource: RaceScheduleSource, Sendable {
    let series: RaceSeries
    let result: @Sendable () throws -> [EventDraft]
    func fetch() async throws -> [EventDraft] { try result() }
}

/// Cubre exactamente el comportamiento que los comentarios del `RaceScheduleRepository.kt`
/// original describían como crítico pero nunca tenía test: aislamiento de fallos entre
/// fuentes, deduplicación defensiva, y sustitución completa de una serie en cada sync
/// (`replaceSeries`).
final class RaceScheduleRepositoryTests: XCTestCase {

    func testPartialFailureIsolation() async {
        let container = makeInMemoryContainer()
        let okSource = FakeScheduleSource(series: .f1) {
            [makeEvent(series: .f1, startTimeUtc: Date())]
        }
        let failingSource = FakeScheduleSource(series: .motoGp) {
            throw URLError(.timedOut)
        }

        let repository = RaceScheduleRepository(sources: [okSource, failingSource], modelContainer: container)
        let result = await repository.syncAll()

        XCTAssertEqual(Set(result.succeeded), [.f1])
        XCTAssertEqual(Set(result.failed), [.motoGp])
    }

    func testEmptyResultCountsAsFailureButDoesNotThrow() async {
        let container = makeInMemoryContainer()
        let emptySource = FakeScheduleSource(series: .f1) { [] }

        let repository = RaceScheduleRepository(sources: [emptySource], modelContainer: container)
        let result = await repository.syncAll()

        XCTAssertEqual(result.failed, [.f1])
        XCTAssertEqual(result.outcomes.first?.sessionCount, 0)
    }

    func testDuplicateEventsAreDeduplicatedByTitleAndStartTime() async {
        let container = makeInMemoryContainer()
        let start = Date()
        let duplicateSource = FakeScheduleSource(series: .f1) {
            [
                makeEvent(series: .f1, uid: "uid-1", fullTitle: "Formula 1 - GP - Carrera", startTimeUtc: start),
                // Mismo título y misma hora, `uid` distinto — el caso real que motivó el
                // filtro (ver comentario de `RaceScheduleRepository.swift`).
                makeEvent(series: .f1, uid: "uid-2", fullTitle: "Formula 1 - GP - Carrera", startTimeUtc: start)
            ]
        }

        let repository = RaceScheduleRepository(sources: [duplicateSource], modelContainer: container)
        let result = await repository.syncAll()

        XCTAssertEqual(result.outcomes.first?.sessionCount, 1)
        let stored = try! ModelContext(container).fetch(FetchDescriptor<EventModel>())
        XCTAssertEqual(stored.count, 1)
    }

    func testSyncReplacesStaleEventsForThatSeriesOnly() async {
        let container = makeInMemoryContainer()
        let context = ModelContext(container)
        context.insert(EventModel(series: .f1, uid: "stale", fullTitle: "Sesión ya cancelada", startTimeUtc: Date(), inferredBadge: ""))
        context.insert(EventModel(series: .motoGp, uid: "untouched", fullTitle: "No debe tocarse", startTimeUtc: Date(), inferredBadge: ""))
        try! context.save()

        let freshSource = FakeScheduleSource(series: .f1) {
            [makeEvent(series: .f1, uid: "fresh", fullTitle: "Sesión nueva", startTimeUtc: Date())]
        }

        let repository = RaceScheduleRepository(sources: [freshSource], modelContainer: container)
        _ = await repository.syncAll()

        let stored = try! ModelContext(container).fetch(FetchDescriptor<EventModel>())
        // F1 se sustituyó entera (la sesión "stale" desaparece); MotoGP, que no
        // sincronizó en este ciclo, se queda intacta.
        XCTAssertEqual(Set(stored.map(\.uid)), ["fresh", "untouched"])
    }
}
