import XCTest
@testable import PitBoardKit

final class SeasonWindowTests: XCTestCase {

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    func testEndOfCurrentYearIsDec31AtEndOfDayUtc() {
        let now = date(year: 2026, month: 6, day: 15)
        let end = SeasonWindow.endOfCurrentYearUtc(nowUtc: now)
        let components = utcCalendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: end)

        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 12)
        XCTAssertEqual(components.day, 31)
        XCTAssertEqual(components.hour, 23)
        XCTAssertEqual(components.minute, 59)
        XCTAssertEqual(components.second, 59)
    }

    func testMovesToNextYearAutomatically() {
        let now = date(year: 2027, month: 1, day: 1)
        let end = SeasonWindow.endOfCurrentYearUtc(nowUtc: now)
        XCTAssertEqual(utcCalendar.component(.year, from: end), 2027)
    }

    private func date(year: Int, month: Int, day: Int) -> Date {
        var components = DateComponents()
        components.timeZone = TimeZone(identifier: "UTC")
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        return utcCalendar.date(from: components)!
    }
}
