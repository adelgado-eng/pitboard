import XCTest
@testable import PitBoardKit

final class WidgetSupportTests: XCTestCase {

    func testEventDisplayNameStripsSessionWordAndSeriesPrefix() {
        let name = widgetEventDisplayName(
            fullTitle: "Formula 1 - GP de Italia - Monza - Carrera",
            seriesDisplayName: "Formula 1",
            wordLimit: 8
        )
        XCTAssertEqual(name, "GP de Italia - Monza")
    }

    func testEventDisplayNameTruncatesToWordLimit() {
        let name = widgetEventDisplayName(
            fullTitle: "Formula 1 - Gran Premio de la Ciudad de Mexico - Carrera",
            seriesDisplayName: "Formula 1",
            wordLimit: 3
        )
        XCTAssertTrue(name.hasSuffix("…"))
        // 3 palabras + el propio "…" pegado a la última.
        XCTAssertEqual(name.replacingOccurrences(of: "…", with: "").split(separator: " ").count, 3)
    }

    func testTrackTimeLabelNilWhenSameAsDeviceTimeZone() {
        XCTAssertNil(widgetTrackTimeLabel(startTimeUtc: Date(), timeZoneId: TimeZone.current.identifier))
    }

    func testTrackTimeLabelNilForInvalidZone() {
        XCTAssertNil(widgetTrackTimeLabel(startTimeUtc: Date(), timeZoneId: "No/Existe"))
    }

    func testTrackTimeLabelFormatsHourMinute() {
        var components = DateComponents()
        components.timeZone = TimeZone(identifier: "America/New_York")
        components.year = 2026; components.month = 6; components.day = 15
        components.hour = 14; components.minute = 30
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        let date = calendar.date(from: components)!

        let label = widgetTrackTimeLabel(startTimeUtc: date, timeZoneId: "America/New_York")
        // Si el dispositivo de test ya está en America/New_York, la función devuelve nil
        // a propósito (no aporta mostrar la misma hora dos veces) — se comprueba ese caso
        // aparte, aquí solo cuando de verdad difiere.
        if TimeZone.current.identifier != "America/New_York" {
            XCTAssertEqual(label, "14:30")
        }
    }

    func testDaysUntilCountsWholeCalendarDays() {
        let calendar = Calendar(identifier: .gregorian)
        let inThreeDays = calendar.date(byAdding: .day, value: 3, to: Date())!
        XCTAssertEqual(widgetDaysUntil(inThreeDays), 3)
    }
}
