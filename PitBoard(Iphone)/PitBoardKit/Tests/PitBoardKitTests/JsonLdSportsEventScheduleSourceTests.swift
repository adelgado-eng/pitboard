import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): fuente genérica reusada por F2, F3 y ELMS —
/// probarla una vez cubre el patrón común de las tres. El fixture usa la configuración
/// real de F2 (listado + exclusión de días de test).
final class JsonLdSportsEventScheduleSourceTests: XCTestCase {

    private let source = JsonLdSportsEventScheduleSource(
        series: .f2,
        baseUrl: "https://www.fiaformula2.com",
        listingUrlTemplate: "https://www.fiaformula2.com/en/racing/{year}",
        roundHrefPrefixTemplate: "/en/racing/{year}/",
        excludeSlugContaining: ["test"]
    )

    func testExtractRoundUrlsDropsTestDaysBySlug() throws {
        let html = """
            <html><body>
            <a href="/en/racing/2026/bahrain">Bahrain</a>
            <a href="/en/racing/2026/jeddah-test">Jeddah Test</a>
            <a href="/other/page">Other</a>
            </body></html>
            """

        let urls = try source.extractRoundUrls(html, roundHrefPrefix: "/en/racing/2026/")

        XCTAssertEqual(urls, ["https://www.fiaformula2.com/en/racing/2026/bahrain"])
    }

    func testRoundNameComesFromTheShortPartOfTheSubEventNotTheFullTitle() throws {
        let html = """
            <html><body>
            <script type="application/ld+json">
            {"name":"FORMULA 2 BAHRAIN GRAND PRIX 2026","location":{"name":"Bahrain International Circuit"},"subEvent":[
              {"name":"Practice - Bahrain Grand Prix","startDate":"2026-03-06T10:00:00Z"},
              {"name":"Qualifying - Bahrain Grand Prix","startDate":"2026-03-06T14:00:00Z"}
            ]}
            </script>
            </body></html>
            """

        let events = try source.parseRoundHTML(html, roundUrl: "https://www.fiaformula2.com/en/racing/2026/bahrain")

        XCTAssertEqual(events.count, 2)
        XCTAssertTrue(events[0].fullTitle.contains("Bahrain Grand Prix"))
        XCTAssertTrue(events[0].fullTitle.contains("Bahrain International Circuit"))
        XCTAssertTrue(events[0].fullTitle.hasSuffix("Practice"))
        XCTAssertTrue(events[1].fullTitle.hasSuffix("Qualifying"))
    }

    func testWithoutJsonLdWithSubEventReturnsEmptyInsteadOfThrowing() throws {
        let events = try source.parseRoundHTML(
            "<html><body><p>Sin datos</p></body></html>",
            roundUrl: "https://www.fiaformula2.com/en/racing/2026/bahrain"
        )

        XCTAssertTrue(events.isEmpty)
    }
}
