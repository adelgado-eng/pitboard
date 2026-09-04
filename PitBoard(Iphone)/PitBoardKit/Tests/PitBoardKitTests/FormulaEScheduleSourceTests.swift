import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): fiaformulae.com/en/calendar sirve un único JSON-LD
/// ItemList con toda la temporada — algunas rondas traen `subEvent` con sesiones, las más
/// lejanas todavía no. El fixture reproduce ambos casos.
final class FormulaEScheduleSourceTests: XCTestCase {

    private let source = FormulaEScheduleSource()

    private let fixtureHTML = """
        <html><body>
        <script type="application/ld+json">
        {"itemListElement":[
          {"item":{"name":"Sao Paulo E-Prix","startDate":"2026-01-31T18:00:00Z","location":{"name":"Sao Paulo Street Circuit"},"subEvent":[
            {"name":"Free Practice","startDate":"2026-01-31T13:00:00Z"},
            {"name":"Qualifying","startDate":"2026-01-31T15:00:00Z"},
            {"name":"Race","startDate":"2026-01-31T18:00:00Z"}
          ]}},
          {"item":{"name":"Mexico City E-Prix","startDate":"2026-02-14T20:00:00Z","location":{"name":"Autodromo Hermanos Rodriguez"}}}
        ]}
        </script>
        </body></html>
        """

    func testARoundWithSubEventBreaksDownItsSessions() throws {
        let events = try source.parseHTML(fixtureHTML)

        XCTAssertEqual(events.filter { $0.fullTitle.contains("Sao Paulo") }.count, 3)
    }

    func testARoundWithoutSubEventYetIsStillSavedAsASingleRaceSession() throws {
        let events = try source.parseHTML(fixtureHTML)

        let mexico = events.filter { $0.fullTitle.contains("Mexico City") }
        XCTAssertEqual(mexico.count, 1)
        XCTAssertTrue(mexico[0].fullTitle.hasSuffix("Race"))

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        XCTAssertEqual(mexico[0].startTimeUtc, formatter.date(from: "2026-02-14T20:00:00Z"))
    }

    func testWithoutAnyRecognizableJsonLdReturnsEmptyInsteadOfThrowing() throws {
        XCTAssertTrue(try source.parseHTML("<html><body><p>Sin datos</p></body></html>").isEmpty)
    }
}
