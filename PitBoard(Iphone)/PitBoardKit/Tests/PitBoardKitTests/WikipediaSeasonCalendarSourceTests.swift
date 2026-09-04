import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): esta es la fuente con más bugs reales ya
/// documentados en su propio comentario — los dos fixtures principales reproducen
/// exactamente los dos casos que dejaban a Fórmula E con CERO carreras: la fecha que ya
/// trae el año (se duplicaba: "18 December 2026 2026", ilegible) y el `rowspan` de dos
/// rondas que comparten circuito (desalineaba las columnas de la segunda fila).
final class WikipediaSeasonCalendarSourceTests: XCTestCase {

    private let source = WikipediaSeasonCalendarSource(series: .formulaE, wikipediaSlug: "placeholder")
    private let porscheSource = WikipediaSeasonCalendarSource(series: .porscheSupercup, wikipediaSlug: "placeholder")

    private func noonUTC(year: Int, month: Int, day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12, minute: 0))!
    }

    func testADateThatAlreadyHasTheYearIsNotDuplicatedAndStaysReadable() throws {
        let html = """
            <table class="wikitable">
              <tr><th>Round</th><th>E-Prix</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>10</td><td>London E-Prix</td><td>ExCeL London</td><td>18 December 2026</td></tr>
            </table>
            """

        let events = try source.parseHTML(html, year: 2026)

        XCTAssertEqual(events.count, 1) // antes del fix, esta fila producía CERO eventos
        XCTAssertEqual(events[0].startTimeUtc, noonUTC(year: 2026, month: 12, day: 18))
    }

    func testTwoRoundsSharingACircuitByRowspanDoNotLoseTheNameOnTheSecondRow() throws {
        let html = """
            <table class="wikitable">
              <tr><th>Round</th><th>E-Prix</th><th>Circuit</th><th>Date</th></tr>
              <tr>
                <td rowspan="2">1</td>
                <td rowspan="2">Sao Paulo E-Prix</td>
                <td rowspan="2">Sao Paulo Street Circuit</td>
                <td>31 January 2026</td>
              </tr>
              <tr><td>1 February 2026</td></tr>
            </table>
            """

        let events = try source.parseHTML(html, year: 2026)

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events[1].fullTitle.contains("Sao Paulo E-Prix"))
        XCTAssertTrue(events[1].fullTitle.contains("Sao Paulo Street Circuit"))
        XCTAssertEqual(events[0].startTimeUtc, noonUTC(year: 2026, month: 1, day: 31))
        XCTAssertEqual(events[1].startTimeUtc, noonUTC(year: 2026, month: 2, day: 1))
    }

    func testWithoutAYearInTheSourceDateTheGivenYearParameterIsAppended() throws {
        let html = """
            <table class="wikitable">
              <tr><th>Rnd</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>1</td><td>Imola</td><td>6 April</td></tr>
            </table>
            """

        let events = try porscheSource.parseHTML(html, year: 2026)

        XCTAssertEqual(events[0].startTimeUtc, noonUTC(year: 2026, month: 4, day: 6))
        XCTAssertTrue(events[0].fullTitle.contains("Ronda 1")) // sin columna de nombre de carrera
    }

    func testADateRangeKeepsTheSecondDay() throws {
        let html = """
            <table class="wikitable">
              <tr><th>Rnd</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>1</td><td>Daytona</td><td>January 24-25</td></tr>
            </table>
            """

        let events = try porscheSource.parseHTML(html, year: 2026)

        XCTAssertEqual(events[0].startTimeUtc, noonUTC(year: 2026, month: 1, day: 25))
    }

    func testWithoutAnyWikitableWithDateAndCircuitReturnsEmpty() throws {
        let events = try source.parseHTML("<table><tr><th>Nada</th></tr></table>", year: 2026)

        XCTAssertTrue(events.isEmpty)
    }
}
