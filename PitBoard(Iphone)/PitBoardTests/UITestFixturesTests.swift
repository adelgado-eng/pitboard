import XCTest
import SwiftData
import PitBoardKit
@testable import PitBoard

/// Verifica que los datos que `PitBoardUITests` da por hechos (2 eventos, 1 líder de F1
/// con nombre concreto, series ya configuradas) de verdad los siembra
/// `UITestFixtures.seedIfNeeded` — contra un `ModelContainer` en memoria, nunca el store
/// real de la app.
final class UITestFixturesTests: XCTestCase {

    func testSeedsExactlyTheEventsPitBoardUITestsExpects() async {
        let container = makeInMemoryContainer()
        let context = await UITestFixtures.seedIfNeeded(in: container)

        let events = (try? context.fetch(FetchDescriptor<EventModel>())) ?? []
        XCTAssertEqual(Set(events.map(\.uid)), ["UITEST-F1-RACE", "UITEST-MGP-QUALY"])

        let f1Event = events.first { $0.uid == "UITEST-F1-RACE" }
        XCTAssertEqual(f1Event?.series, .f1)
        XCTAssertEqual(f1Event?.inferredBadge, SessionBadgeType.race.rawValue)

        let motoGpEvent = events.first { $0.uid == "UITEST-MGP-QUALY" }
        XCTAssertEqual(motoGpEvent?.series, .motoGp)
        XCTAssertEqual(motoGpEvent?.inferredBadge, SessionBadgeType.qualy.rawValue)
    }

    func testSeedsAnF1LeaderNamedForEventsScreenUITests() async {
        let container = makeInMemoryContainer()
        let context = await UITestFixtures.seedIfNeeded(in: container)

        let standings = (try? context.fetch(FetchDescriptor<StandingModel>())) ?? []
        let leader = standings.first { $0.category == .f1 && $0.position == 1 }
        XCTAssertEqual(leader?.name, "Piloto de Prueba", "StandingsScreenUITests busca este nombre exacto tras navegar a F1.")
    }

    func testSeedsOneSeriesConfigPerRaceSeries() async {
        let container = makeInMemoryContainer()
        let context = await UITestFixtures.seedIfNeeded(in: container)

        let configs = (try? context.fetch(FetchDescriptor<SeriesConfigModel>())) ?? []
        XCTAssertEqual(configs.count, RaceSeries.allCases.count)
    }

    func testRunningTwiceDoesNotAccumulateStaleRows() async {
        let container = makeInMemoryContainer()
        _ = await UITestFixtures.seedIfNeeded(in: container)
        let context = await UITestFixtures.seedIfNeeded(in: container)

        // Si el borrado previo al sembrado fallara, una segunda ejecución duplicaría
        // filas — cada test de UI debe partir del mismo estado, no de uno acumulado.
        let events = (try? context.fetch(FetchDescriptor<EventModel>())) ?? []
        XCTAssertEqual(events.count, 2)

        let configs = (try? context.fetch(FetchDescriptor<SeriesConfigModel>())) ?? []
        XCTAssertEqual(configs.count, RaceSeries.allCases.count)
    }
}

final class UITestSupportTests: XCTestCase {

    func testDetectsTheLaunchArgument() {
        XCTAssertTrue(UITestSupport.isUITesting(arguments: ["-uiTesting"]))
        XCTAssertTrue(UITestSupport.isUITesting(arguments: ["/path/to/app", "-uiTesting", "-otherFlag"]))
    }

    func testFalseWithoutTheLaunchArgument() {
        XCTAssertFalse(UITestSupport.isUITesting(arguments: []))
        XCTAssertFalse(UITestSupport.isUITesting(arguments: ["/path/to/app", "-someOtherFlag"]))
    }
}
