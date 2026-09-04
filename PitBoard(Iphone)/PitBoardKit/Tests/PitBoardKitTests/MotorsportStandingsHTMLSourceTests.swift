import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): MotorsportStandingsHTMLSource es la clase que
/// resuelve MotoGP, Moto2 y Moto3 (misma clase `final`, distinta configuración por
/// serie — no hay subclases en Swift, ver comentario de la propia clase) — probarla una
/// vez cubre el parsing común de las tres. El fixture reproduce la forma que el propio
/// código documenta: cabecera "Rider" (no "Driver") y celda de nombre con un único `<a>`,
/// sin separación HTML de piloto/equipo.
final class MotorsportStandingsHTMLSourceTests: XCTestCase {

    private let source = MotorsportStandingsHTMLSource(category: .motoGp, driverUrl: "unused", teamUrl: nil)

    private let fixtureHTML = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Rider</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/driver/1">F. Bagnaia</a> Ducati Team</td><td>310</td></tr>
            <tr><td>2</td><td><a href="/driver/2">M. Marquez</a> Gresini Racing</td><td>295</td></tr>
            <tr><td>3</td><td><a href="/driver/3">J. Martin</a> Pramac Racing</td><td>280</td></tr>
          </tbody>
        </table>
        </body></html>
        """

    private let knownTeamNames = ["Ducati Team", "Gresini Racing", "Pramac Racing"]

    func testFindsTableByRiderHeaderNotOnlyDriver() throws {
        let rows = try source.parseTableHTML(fixtureHTML, knownTeamNames: knownTeamNames)
        XCTAssertEqual(rows.count, 3)
    }

    func testSplitsDriverAndTeamByKnownTeamSuffix() throws {
        let rows = try source.parseTableHTML(fixtureHTML, knownTeamNames: knownTeamNames)
        XCTAssertEqual(rows[0].name, "F. Bagnaia")
        XCTAssertEqual(rows[0].team, "Ducati Team")
        XCTAssertEqual(rows[0].points, 310.0)
    }

    func testWithoutKnownTeamNamesTeamStaysEmpty() throws {
        let rows = try source.parseTableHTML(fixtureHTML, knownTeamNames: [])
        XCTAssertEqual(rows[0].name, "F. Bagnaia Ducati Team")
        XCTAssertEqual(rows[0].team, "")
    }

    func testLocatesPointsColumnByHeaderNotFixedPosition() throws {
        let rows = try source.parseTableHTML(fixtureHTML, knownTeamNames: knownTeamNames)
        XCTAssertEqual(rows[1].points, 295.0)
        XCTAssertEqual(rows[2].points, 280.0)
    }

    func testNoRecognizableTableReturnsEmptyInsteadOfThrowing() throws {
        let rows = try source.parseTableHTML("<html><body><p>Sin resultados todavía</p></body></html>")
        XCTAssertTrue(rows.isEmpty)
    }
}
