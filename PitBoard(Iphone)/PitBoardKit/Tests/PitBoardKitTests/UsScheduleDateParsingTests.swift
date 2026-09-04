import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): utilidad compartida por IndyCar y NASCAR (vía ESPN)
/// — ya era pura, sin red, así que se testea directamente sin ningún refactor.
final class UsScheduleDateParsingTests: XCTestCase {

    private let eastern = TimeZone(identifier: "America/New_York")!

    private func easternDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = eastern
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    func testADateThatAlreadyPassedThisYearResolvesToNextYear() throws {
        let today = easternDate(year: 2026, month: 12, day: 20)

        let result = UsScheduleDateParsing.resolveUpcomingMonthDay("Jan 15", today: today)

        XCTAssertEqual(result?.year, 2027)
        XCTAssertEqual(result?.month, 1)
        XCTAssertEqual(result?.day, 15)
    }

    func testADateThatHasNotPassedYetStaysInThisYear() throws {
        let today = easternDate(year: 2026, month: 3, day: 1)

        let result = UsScheduleDateParsing.resolveUpcomingMonthDay("Sep 6", today: today)

        XCTAssertEqual(result?.year, 2026)
        XCTAssertEqual(result?.month, 9)
        XCTAssertEqual(result?.day, 6)
    }

    func testParseTimeOfDayUnderstandsNoonAndMidnightBesidesNumericTime() throws {
        XCTAssertEqual(UsScheduleDateParsing.parseTimeOfDay("Noon ET"), DateComponents(hour: 12, minute: 0))
        XCTAssertEqual(UsScheduleDateParsing.parseTimeOfDay("Midnight"), DateComponents(hour: 0, minute: 0))
        XCTAssertEqual(UsScheduleDateParsing.parseTimeOfDay("2:30 PM ET"), DateComponents(hour: 14, minute: 30))
    }

    func testWithoutTimeToDateUsesEasternNoonAsAPlaceholder() throws {
        let today = easternDate(year: 2026, month: 3, day: 1)

        let date = try XCTUnwrap(UsScheduleDateParsing.toDate(dateText: "Sep 6", timeText: nil, today: today))

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = eastern
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)

        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 9)
        XCTAssertEqual(components.day, 6)
        XCTAssertEqual(components.hour, 12)
        XCTAssertEqual(components.minute, 0)
    }

    func testEastZoneIdReturnsEasternTimeZone() {
        XCTAssertEqual(UsScheduleDateParsing.eastZoneId(), "America/New_York")
    }
}
