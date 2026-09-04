import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): imsa.com mezcla la clase principal (WeatherTech
/// Championship) con las de apoyo en la misma sección de horario, sin más marca que el
/// propio texto del nombre — el fixture reproduce esa mezcla para comprobar el filtro por
/// palabra clave.
final class ImsaScheduleSourceTests: XCTestCase {

    private let source = ImsaScheduleSource()

    private let fixtureHTML = """
        <html><head><title>2026 Rolex 24 At DAYTONA | IMSA</title></head><body>
        <div class="race-event-schedule-container-inner">
          <div class="day-event-header">Friday, January 23, 2026</div>
          <div class="day-event-details-container">
            <div class="event-time">10:05 AM to 11:35 AM ET</div>
            <div class="event-name">WeatherTech Championship Practice</div>
          </div>
          <div class="day-event-details-container">
            <div class="event-time">1:00 PM to 2:00 PM ET</div>
            <div class="event-name">Mazda MX-5 Cup Race</div>
          </div>
          <div class="day-event-header">Saturday, January 24, 2026</div>
          <div class="day-event-details-container">
            <div class="event-time">3:40 PM to 4:00 PM ET</div>
            <div class="event-name">Rolex 24 At Daytona</div>
          </div>
        </div>
        </body></html>
        """

    private func easternDate(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    func testTakesTheRoundNameFromTheTitleStrippingTheSiteSuffixAndYear() throws {
        let events = try source.parseEventHTML(fixtureHTML, eventUrl: "https://www.imsa.com/events/rolex-24/")

        XCTAssertTrue(events.allSatisfy { $0.fullTitle.contains("Rolex 24 At DAYTONA") })
        XCTAssertTrue(events.allSatisfy { !$0.fullTitle.contains("| IMSA") })
    }

    func testDropsSupportClassSessionsByName() throws {
        let events = try source.parseEventHTML(fixtureHTML, eventUrl: "https://www.imsa.com/events/rolex-24/")

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events.allSatisfy { !$0.fullTitle.contains("Mazda MX-5") })
    }

    func testOnlyKeepsTheStartOfTheTimeRangeNotTheEnd() throws {
        let events = try source.parseEventHTML(fixtureHTML, eventUrl: "https://www.imsa.com/events/rolex-24/")

        let practice = try XCTUnwrap(events.first { $0.fullTitle.contains("Practice") })
        XCTAssertEqual(practice.startTimeUtc, easternDate(year: 2026, month: 1, day: 23, hour: 10, minute: 5))
    }

    func testEachDayGroupsItsOwnSessionsTheSecondDayDoesNotInheritTheFirstsDate() throws {
        let events = try source.parseEventHTML(fixtureHTML, eventUrl: "https://www.imsa.com/events/rolex-24/")

        let race = try XCTUnwrap(events.first { $0.fullTitle.contains("Rolex 24 At Daytona") })
        XCTAssertEqual(race.startTimeUtc, easternDate(year: 2026, month: 1, day: 24, hour: 15, minute: 40))
    }
}
