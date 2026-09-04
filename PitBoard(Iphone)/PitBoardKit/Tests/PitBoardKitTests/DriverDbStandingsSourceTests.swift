import XCTest
import SwiftSoup
@testable import PitBoardKit

private func tableFrom(_ html: String) throws -> Element {
    try SwiftSoup.parse(html).select("table").first()!
}

/// Fase 1 del diagnóstico (graphify): DriverDbStandingsSource es la clase compartida por
/// F1 Academy, F2, F3 y Porsche Supercup (4 fuentes reales) — probarla una vez cubre el
/// parsing común de las cuatro. `parseDriverRows` ya tomaba un `Element` en vez de una
/// URL, así que no hizo falta separar nada más que su visibilidad.
final class DriverDbStandingsSourceTests: XCTestCase {

    private let source = DriverDbStandingsSource(category: .porscheSupercup, slug: "porsche-supercup")

    private let fixtureTable = try! tableFrom("""
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Team</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a>Bastian Buus</a></td><td>Schumacher CLRT</td><td>320</td></tr>
            <tr><td>2</td><td><a>Julien Andlauer</a><img src="https://www.driverdb.com/_next/image?url=%2Fdefault%2Fdriver-profile.png&w=128"></td><td>—</td><td>295</td></tr>
          </tbody>
        </table>
        """)

    func testReadsNameTeamAndPointsFromRow() throws {
        let rows = try source.parseDriverRows(table: fixtureTable, nowUtc: Date())

        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows[0].name, "Bastian Buus")
        XCTAssertEqual(rows[0].team, "Schumacher CLRT")
        XCTAssertEqual(rows[0].points, 320.0)
        XCTAssertEqual(rows[0].position, 1)
    }

    func testEncodedPlaceholderPhotoIsNotSavedAsReal() throws {
        let rows = try source.parseDriverRows(table: fixtureTable, nowUtc: Date())
        XCTAssertNil(rows[1].photoUrl)
    }

    func testLongDashInTeamCellMeansNoTeam() throws {
        let rows = try source.parseDriverRows(table: fixtureTable, nowUtc: Date())
        XCTAssertEqual(rows[1].team, "")
    }

    func testNoRecognizablePointsColumnReturnsEmptyInsteadOfThrowing() throws {
        let noPoints = try tableFrom("""
            <table>
              <thead><tr><th>Pos</th><th>Driver</th></tr></thead>
              <tbody><tr><td>1</td><td><a>Piloto</a></td></tr></tbody>
            </table>
            """)

        let rows = try source.parseDriverRows(table: noPoints, nowUtc: Date())
        XCTAssertTrue(rows.isEmpty)
    }
}
