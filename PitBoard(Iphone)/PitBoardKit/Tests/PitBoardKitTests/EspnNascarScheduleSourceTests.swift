import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): espn.com separa fecha/hora y nombre/circuito con
/// `<br>` dentro de la misma celda — el fixture reproduce esa forma (incluido el espacio
/// no separable "&nbsp;" que ESPN usa entre "Wed" y "Feb").
final class EspnNascarScheduleSourceTests: XCTestCase {

    private let source = EspnNascarScheduleSource(series: .nascarCup, slug: "nascar-premier")

    private let fixtureHTML = """
        <html><body>
        <table class="tablehead">
          <tr class="oddrow">
            <td>Wed,&nbsp;Feb&nbsp;4<br>7:30 PM ET</td>
            <td><b>Cook Out Clash</b><br>Bowman Gray Stadium</td>
          </tr>
          <tr class="evenrow">
            <td>Sun,&nbsp;Feb&nbsp;15<br>2:30 PM ET</td>
            <td><b>Daytona 500</b><br>Daytona International Speedway</td>
          </tr>
        </table>
        </body></html>
        """

    func testSplitsRaceNameAndCircuitByLineBreak() throws {
        let events = try source.parseHTML(fixtureHTML)

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events[0].fullTitle.contains("Cook Out Clash"))
        XCTAssertTrue(events[0].fullTitle.contains("Bowman Gray Stadium"))
        XCTAssertTrue(events[1].fullTitle.contains("Daytona 500"))
    }

    func testEverythingIsLabeledAsRaceItDoesNotDistinguishSessions() throws {
        let events = try source.parseHTML(fixtureHTML)

        XCTAssertTrue(events.allSatisfy { $0.inferredBadge == SessionBadgeType.race.rawValue })
    }

    func testResolvesDateAndTimeTheSameWayAsUsScheduleDateParsing() throws {
        let events = try source.parseHTML(fixtureHTML)
        let expected = UsScheduleDateParsing.toDate(dateText: "Feb 4", timeText: "7:30 PM ET")

        XCTAssertEqual(events[0].startTimeUtc, expected)
    }

    func testWithoutARecognizableTableReturnsEmptyInsteadOfThrowing() throws {
        XCTAssertTrue(try source.parseHTML("<html><body><p>Sin calendario</p></body></html>").isEmpty)
    }
}
