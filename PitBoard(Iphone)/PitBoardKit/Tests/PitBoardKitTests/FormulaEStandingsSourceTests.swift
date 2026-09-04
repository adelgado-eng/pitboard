import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): a diferencia del resto de fuentes, Fórmula E lee
/// JSON de la API interna de Pulselive en vez de HTML — el fixture reproduce su forma real
/// (ver comentario de la clase).
final class FormulaEStandingsSourceTests: XCTestCase {

    private let source = FormulaEStandingsSource()

    func testPicksTheChampionshipWithStatusPresentNotTheFirstInTheList() throws {
        let json = """
            { "championships": [
                { "id": "11", "status": "Past" },
                { "id": "12", "status": "Present" }
            ] }
            """

        XCTAssertEqual(try source.parseChampionshipsJSON(json), "12")
    }

    func testWithoutAnyPresentFallsBackToTheLastOneInTheList() throws {
        let json = """
            { "championships": [ { "id": "10", "status": "Past" }, { "id": "11", "status": "Past" } ] }
            """

        XCTAssertEqual(try source.parseChampionshipsJSON(json), "11")
    }

    func testJoinsFirstAndLastNameOfTheDriver() throws {
        let json = """
            [
              { "driverId": "d1", "driverTeamName": "Jaguar TCS Racing", "driverFirstName": "Nick", "driverLastName": "Cassidy", "driverPoints": 210 },
              { "driverId": "d2", "driverTeamName": "DS Penske", "driverFirstName": "Jean-Eric", "driverLastName": "Vergne", "driverPoints": 195 }
            ]
            """

        let rows = try source.parseDriverRowsJSON(json)

        XCTAssertEqual(rows[0].name, "Nick Cassidy")
        XCTAssertEqual(rows[0].team, "Jaguar TCS Racing")
        XCTAssertEqual(rows[0].points, 210.0)
    }

    func testBuildsDriverPhotoUrlWithTheCurrentChampionshipId() throws {
        let rows = [FormulaEStandingsSource.Row(key: "d1", name: "Nick Cassidy", team: "Jaguar TCS Racing", points: 210.0, imageId: "d1")]

        let entities = source.buildDriverEntities(rows, championshipId: "12", nowUtc: Date())

        XCTAssertEqual(entities[0].photoUrl, "https://static-files.formula-e.pulselive.com/drivers/12/right/large/d1.png")
    }

    func testWithoutImageIdThereIsNoPhotoInsteadOfABrokenUrl() throws {
        let rows = [FormulaEStandingsSource.Row(key: "d1", name: "Nick Cassidy", team: "", points: 210.0, imageId: nil)]

        let entities = source.buildDriverEntities(rows, championshipId: "12", nowUtc: Date())

        XCTAssertNil(entities[0].photoUrl)
    }

    func testEntitiesAreReorderedByPointsAndRenumberedRegardlessOfSourceOrder() throws {
        let rows = [
            FormulaEStandingsSource.Row(key: "a", name: "A", team: "", points: 50.0, imageId: nil),
            FormulaEStandingsSource.Row(key: "b", name: "B", team: "", points: 300.0, imageId: nil)
        ]

        let entities = source.buildTeamEntities(rows, championshipId: "12", nowUtc: Date())

        XCTAssertEqual(entities[0].name, "B")
        XCTAssertEqual(entities[0].position, 1)
        XCTAssertEqual(entities[1].name, "A")
        XCTAssertEqual(entities[1].position, 2)
    }
}
