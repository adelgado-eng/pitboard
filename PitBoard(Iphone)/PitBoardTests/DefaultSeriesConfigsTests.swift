import XCTest
import SwiftData
import PitBoardKit
@testable import PitBoard

/// `makeDefaultSeriesConfigs()` alimenta tanto el sembrado real de arranque
/// (`RootTabView.seedSeriesConfigIfNeeded`) como el de test de UI (`UITestFixtures`) —
/// un fallo aquí afectaría a los dos a la vez, así que merece su propio test.
final class DefaultSeriesConfigsTests: XCTestCase {

    func testProducesExactlyOneConfigPerRaceSeries() {
        let configs = makeDefaultSeriesConfigs()
        XCTAssertEqual(configs.count, RaceSeries.allCases.count)
        XCTAssertEqual(Set(configs.map(\.series)), Set(RaceSeries.allCases))
    }

    func testEachConfigUsesThatSeriesOwnDefaults() {
        let configsBySeries = Dictionary(uniqueKeysWithValues: makeDefaultSeriesConfigs().map { ($0.series, $0) })

        for series in RaceSeries.allCases {
            guard let config = configsBySeries[series] else {
                XCTFail("Falta SeriesConfigModel para \(series)")
                continue
            }
            XCTAssertEqual(config.tag, series.defaultTag)
            XCTAssertEqual(config.colorHex, series.defaultColorHex)
        }
    }

    func testResultIsNotAttachedToAnyModelContext() {
        // Las instancias deben poder insertarse tal cual en cualquier ModelContext —
        // si `makeDefaultSeriesConfigs()` alguna vez las devolviera ya insertadas en
        // otro, esta inserción fallaría o duplicaría filas.
        let container = makeInMemoryContainer()
        let context = ModelContext(container)
        for config in makeDefaultSeriesConfigs() {
            context.insert(config)
        }
        XCTAssertNoThrow(try context.save())

        let stored = try? context.fetch(FetchDescriptor<SeriesConfigModel>())
        XCTAssertEqual(stored?.count, RaceSeries.allCases.count)
    }
}
