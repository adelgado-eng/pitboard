import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): OfficialRosterStandingsSource es la base compartida
/// de F1 y NASCAR Cup (por composición, no herencia — ver comentario en
/// NascarStandingsSource.swift). El bug histórico más importante — el nombre de piloto
/// pegado al código de 3 letras ("Kimi AntonelliANT") — y el extractor de foto de NASCAR
/// son comportamiento real de producción, documentados en el propio código.
final class OfficialRosterStandingsSourceTests: XCTestCase {

    private let f1 = OfficialRosterStandingsSource(category: .f1, rosterUrl: "unused")
    private let nascar = NascarStandingsSource()

    private let f1RosterHTML = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/drivers/antonelli">Kimi AntonelliANT</a></td><td>410</td></tr>
            <tr><td>2</td><td><a href="/drivers/verstappen">Max VerstappenVER</a></td><td>395</td></tr>
          </tbody>
        </table>
        </body></html>
        """

    func testStripsThreeLetterCodeGluedToSurname() throws {
        let rows = try f1.parseRosterHTML(f1RosterHTML)
        XCTAssertEqual(rows[0].name, "Kimi Antonelli")
        XCTAssertEqual(rows[1].name, "Max Verstappen")
    }

    func testReadsPointsFromAuthorityTable() throws {
        let rows = try f1.parseRosterHTML(f1RosterHTML)
        XCTAssertEqual(rows[0].points, 410.0)
        XCTAssertEqual(rows[1].points, 395.0)
    }

    private let nascarRosterHTML = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/racing/driver/_/id/4531/ryan-blaney">Ryan Blaney</a></td><td>2050</td></tr>
          </tbody>
        </table>
        </body></html>
        """

    func testExtractsEspnIdFromHrefAndBuildsCdnPhotoUrl() throws {
        let rows = try nascar.inner.parseRosterHTML(nascarRosterHTML)
        XCTAssertEqual(
            rows[0].photoUrl,
            "https://a.espncdn.com/combiner/i?img=/i/headshots/rpm/players/full/4531.png&w=500&h=500"
        )
    }

    private let driverDbHTML = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Team</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a>Jimmie Johnson</a></td><td>—</td></tr>
            <tr><td>2</td><td><a>B.J. McLeod</a><img src="/default/driver-profile.png"></td><td>Live Fast Motorsports</td></tr>
          </tbody>
        </table>
        </body></html>
        """

    func testLongDashInTeamCellDoesNotCountAsTeamName() throws {
        let enrichment = try f1.parseDriverDbHTML(driverDbHTML, url: "https://www.driverdb.com/x")
        XCTAssertEqual(enrichment["Jimmie Johnson"]?.team, "")
    }

    func testDriverDbPlaceholderPhotoIsNotSavedAsReal() throws {
        let enrichment = try f1.parseDriverDbHTML(driverDbHTML, url: "https://www.driverdb.com/x")
        XCTAssertNil(enrichment["B.J. McLeod"]?.photoUrl)
        XCTAssertEqual(enrichment["B.J. McLeod"]?.team, "Live Fast Motorsports")
    }
}
