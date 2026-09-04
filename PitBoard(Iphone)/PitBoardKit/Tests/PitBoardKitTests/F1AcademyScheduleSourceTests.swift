import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): f1academy.com es una SPA en Next.js que sí incrusta
/// la temporada completa en el script `#__NEXT_DATA__` — el fixture reproduce esa forma
/// exacta.
final class F1AcademyScheduleSourceTests: XCTestCase {

    private let source = F1AcademyScheduleSource()

    func testReadsSessionsFromTheNextDataBlockAndPrefersTheShortCircuitName() throws {
        let html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageData":{"Races":[
              {"RoundNumber":1,"CircuitName":"Albert Park Circuit","CircuitShortName":"Melbourne","Sessions":[
                {"SessionName":"Practice 1","SessionStartTime":"2026-03-06T02:30:00Z"},
                {"SessionName":"Race 1","SessionStartTime":"2026-03-07T05:00:00Z"}
              ]}
            ]}}}}
            </script>
            </body></html>
            """

        let events = try source.parseHTML(html)

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events[0].fullTitle.contains("Melbourne"))

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        XCTAssertEqual(events[0].startTimeUtc, formatter.date(from: "2026-03-06T02:30:00Z"))
    }

    func testWithoutAShortCircuitNameItFallsBackToTheFullName() throws {
        let html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageData":{"Races":[
              {"RoundNumber":2,"CircuitName":"Shanghai International Circuit","Sessions":[
                {"SessionName":"Race 1","SessionStartTime":"2026-03-14T05:00:00Z"}
              ]}
            ]}}}}
            </script>
            </body></html>
            """

        let events = try source.parseHTML(html)

        XCTAssertTrue(events[0].fullTitle.contains("Shanghai International Circuit"))
    }

    func testWithoutTheNextDataBlockReturnsEmptyInsteadOfThrowing() throws {
        XCTAssertTrue(try source.parseHTML("<html><body><p>SPA sin hidratar</p></body></html>").isEmpty)
    }
}
