import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): las 4 variantes de GT World Challenge comparten
/// esta misma clase, parametrizada por serie y dominio — el fixture reproduce la sección
/// "Timetable" real (día en el `<caption>`, columna GMT usada directamente como UTC).
final class GtWorldChallengeScheduleSourceTests: XCTestCase {

    private let source = GtWorldChallengeScheduleSource(series: .gtChallengeEurope, baseUrl: "https://www.gt-world-challenge-europe.com")

    private func utcDate(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    func testExtractItemListUrlsTakesTheUrlOfEachEventFromTheJsonLd() throws {
        let html = """
            <html><body>
            <script type="application/ld+json">
            {"itemListElement":[
              {"url":"https://www.gt-world-challenge-europe.com/events/spa-24-hours"},
              {"url":"https://www.gt-world-challenge-europe.com/events/monza"}
            ]}
            </script>
            </body></html>
            """

        let urls = try source.extractItemListUrls(html)

        XCTAssertEqual(urls, [
            "https://www.gt-world-challenge-europe.com/events/spa-24-hours",
            "https://www.gt-world-challenge-europe.com/events/monza"
        ])
    }

    func testAnEmptySessionCellIsLabeledSessionNInsteadOfBeingLost() throws {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let html = """
            <html><body>
            <h1>Spa 24 Hours</h1>
            <table class="timetable__table">
              <caption class="timetable__caption"><span>Friday, 18 September</span></caption>
              <thead><tr><th>Session</th><th>Local</th><th>GMT</th></tr></thead>
              <tbody>
                <tr><td>Free Practice 1</td><td>10:00</td><td>8:00</td></tr>
                <tr><td></td><td>14:00</td><td>12:00</td></tr>
              </tbody>
            </table>
            </body></html>
            """

        let events = try source.parseEventHTML(html, eventUrl: "https://www.gt-world-challenge-europe.com/events/spa-24-hours")

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events[0].fullTitle.contains("Free Practice 1"))
        XCTAssertTrue(events[1].fullTitle.contains("Sesión 2"))
        XCTAssertEqual(events[0].startTimeUtc, utcDate(year: year, month: 9, day: 18, hour: 8, minute: 0))
    }

    func testTheGmtColumnIsUsedDirectlyAsUtcWithoutConvertingTimeZone() throws {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let html = """
            <html><body>
            <h1>Monza</h1>
            <table class="timetable__table">
              <caption class="timetable__caption"><span>Saturday, 20 June</span></caption>
              <thead><tr><th>Session</th><th>GMT</th></tr></thead>
              <tbody><tr><td>Qualifying</td><td>15:30</td></tr></tbody>
            </table>
            </body></html>
            """

        let events = try source.parseEventHTML(html, eventUrl: "https://www.gt-world-challenge-europe.com/events/monza")

        XCTAssertEqual(events[0].startTimeUtc, utcDate(year: year, month: 6, day: 20, hour: 15, minute: 30))
    }
}
