import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): WEC agrupa Hypercar por número de coche (esa tabla
/// puntúa pilotos, no coches — un mismo coche aparece dos veces con puntos distintos si un
/// piloto se perdió alguna carrera) — el fixture reproduce justo ese caso: dos filas de
/// Hypercar con el MISMO número de coche, que debe colapsar en una sola quedándose con la
/// de más puntos.
final class WecStandingsSourceTests: XCTestCase {

    private let source = WecStandingsSource()

    private let fixtureHTML = """
        <html><body>
        <button data-bs-target="#results-1">FIA Hypercar World Endurance Drivers Championship</button>
        <div id="results-1">
          <table><tbody>
            <tr><td>1</td><td><img alt="Ferrari" src="/logos/ferrari.png"></td><td>#50</td><td>A. Fuoco</td><td>210</td></tr>
            <tr><td>2</td><td><img alt="Ferrari" src="/logos/ferrari.png"></td><td>#50</td><td>N. Nielsen</td><td>195</td></tr>
            <tr><td>3</td><td><img alt="Toyota" src="/logos/toyota.png"></td><td>#7</td><td>K. Kobayashi</td><td>180</td></tr>
          </tbody></table>
        </div>
        <button data-bs-target="#results-2">FIA Endurance Trophy for LMGT3 Teams</button>
        <div id="results-2">
          <table><tbody>
            <tr><td>1</td><td><img alt="Corvette" src="/logos/corvette.png"></td><td>#33</td><td>TF Sport</td><td>150</td></tr>
            <tr><td>2</td><td><img alt="BMW" src="/logos/bmw.png"></td><td>#46</td><td>Team WRT</td><td>140</td></tr>
          </tbody></table>
        </div>
        </body></html>
        """

    func testTwoDriversOfSameCarCollapseIntoOneRowWithTheBestPoints() throws {
        let hypercar = try source.parseHTML(fixtureHTML, nowUtc: Date()).filter { $0.standingsClass == .hypercar }

        XCTAssertEqual(hypercar.count, 2) // 3 filas de origen, 2 coches reales
        let car50 = hypercar.first { $0.name == "#50" }
        XCTAssertEqual(car50?.points, 210.0) // se queda Fuoco (210), no Nielsen (195)
        XCTAssertEqual(car50?.team, "Ferrari")
    }

    func testHypercarAndLmgt3ArePositionedIndependentlyAfterGrouping() throws {
        let hypercar = try source.parseHTML(fixtureHTML, nowUtc: Date())
            .filter { $0.standingsClass == .hypercar }
            .sorted { $0.position < $1.position }

        XCTAssertEqual(hypercar.map(\.name), ["#50", "#7"])
        XCTAssertEqual(hypercar.map(\.position), [1, 2])
    }

    func testLmgt3UsesRealTeamNameNotManufacturer() throws {
        let lmgt3 = try source.parseHTML(fixtureHTML, nowUtc: Date())
            .filter { $0.standingsClass == .lmgt3 }
            .sorted { $0.position < $1.position }

        XCTAssertEqual(lmgt3.map(\.team), ["TF Sport", "Team WRT"])
        XCTAssertEqual(lmgt3.map(\.points), [150.0, 140.0])
    }

    func testAllRowsAreTeamType() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(rows.count, 4)
        XCTAssertTrue(rows.allSatisfy { $0.type == .team })
    }

    func testIfSiteChangesButtonTextThatSectionComesBackEmptyInsteadOfThrowing() throws {
        let htmlWithoutButton = fixtureHTML.replacingOccurrences(
            of: "FIA Hypercar World Endurance Drivers Championship",
            with: "Hypercar Championship (nuevo texto)"
        )

        let hypercar = try source.parseHTML(htmlWithoutButton, nowUtc: Date()).filter { $0.standingsClass == .hypercar }

        XCTAssertTrue(hypercar.isEmpty)
    }
}
