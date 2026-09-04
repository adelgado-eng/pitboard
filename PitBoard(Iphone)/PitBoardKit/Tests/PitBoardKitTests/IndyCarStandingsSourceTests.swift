import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): indycar.com/Standings trae la celda de piloto con
/// varias imágenes a la vez (foto + endplate del coche) y el equipo a veces solo como
/// logo (sin texto) — el fixture reproduce ambos casos documentados en el propio
/// comentario de la clase.
final class IndyCarStandingsSourceTests: XCTestCase {

    private let source = IndyCarStandingsSource()

    private let fixtureHTML = """
        <html><body>
        <table>
          <thead><tr><th>Rank</th><th>Driver</th><th>Team</th><th>Points</th></tr></thead>
          <tbody>
            <tr>
              <td>1</td>
              <td><a>Alex Palou</a><img src="/-/media/IndyCar/Headshot/palou.png?w=80"><img src="/-/media/IndyCar/Endplate/palou.png"></td>
              <td>Chip Ganassi Racing</td>
              <td>589</td>
            </tr>
            <tr>
              <td>2</td>
              <td><a>Pato O'Ward</a><img src="/-/media/IndyCar/Headshot/oward.png?w=80"></td>
              <td><img alt="Arrow McLaren Logo " src="/-/media/IndyCar/Team/ArrowMcLaren.png"></td>
              <td>560</td>
            </tr>
          </tbody>
        </table>
        </body></html>
        """

    func testPicksTheHeadshotNotTheCarEndplateAndStripsTheWidthParameter() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        let palou = try XCTUnwrap(rows.first { $0.name == "Alex Palou" })
        XCTAssertEqual(palou.photoUrl, "https://www.indycar.com/-/media/IndyCar/Headshot/palou.png")
    }

    func testWithoutTeamTextItFallsBackToTheLogoAltWithoutTheWordLogo() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(rows.first { $0.name == "Pato O'Ward" }?.team, "Arrow McLaren")
    }

    func testGroupsDriversByTeamForTheTeamStandings() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        let teams = rows.filter { $0.type == .team }.sorted { $0.position < $1.position }
        XCTAssertEqual(teams.map(\.name), ["Chip Ganassi Racing", "Arrow McLaren"])
        XCTAssertEqual(teams[0].points, 589.0)
    }

    func testTwoDriversAndTwoTeamsInTotal() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(rows.filter { $0.type == .driver }.count, 2)
        XCTAssertEqual(rows.filter { $0.type == .team }.count, 2)
        XCTAssertTrue(rows.allSatisfy { !$0.name.isEmpty })
    }
}
