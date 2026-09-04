import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): Le Mans Cup se reescribió tras comprobar que su
/// tabla NO trae columna de logo (a diferencia de WEC, misma organización/plantilla) — el
/// logo se cruza aparte por número de coche desde otra página. El fixture reproduce esa
/// forma de fila de 5 columnas sin logo, con y sin coincidencia en el mapa de logos.
final class LeMansCupStandingsSourceTests: XCTestCase {

    private let source = LeMansCupStandingsSource()

    private func section(_ id: String, _ buttonText: String, _ rowsHTML: String) -> String {
        """
        <button data-bs-target="#\(id)">\(buttonText)</button>
        <div id="\(id)"><table><tbody>\(rowsHTML)</tbody></table></div>
        """
    }

    private lazy var classificationHTML: String = """
        <html><body>
        \(section("s1", "LMP3 Pro/Am Teams Classification", "<tr><td>1</td><td>#7</td><td>TEAM VIRAGE</td><td>10</td><td>55</td></tr>"))
        \(section("s2", "LMP3 Teams Classification", "<tr><td>1</td><td>#85</td><td>R-ACE GP</td><td>12</td><td>60</td></tr>"))
        \(section("s3", "GT3 Teams Classification", "<tr><td>1</td><td>#33</td><td>TF SPORT</td><td>15</td><td>70</td></tr>"))
        </body></html>
        """

    func testReadsTheThreeLeMansCupClassesWithoutALogoColumnInTheTableItself() throws {
        let rows = try source.parseClassificationHTML(classificationHTML, logoByCarNumber: [:], nowUtc: Date())

        XCTAssertEqual(rows.count, 3)
        XCTAssertEqual(rows.first { $0.standingsClass == .lmp3 }?.team, "R-ACE GP")
        XCTAssertEqual(rows.first { $0.standingsClass == .lmp3 }?.points, 60.0)
        XCTAssertEqual(rows.first { $0.standingsClass == .lmp3ProAm }?.team, "TEAM VIRAGE")
        XCTAssertEqual(rows.first { $0.standingsClass == .gt3 }?.team, "TF SPORT")
    }

    func testCrossesTheLogoByCarNumberWhenPresentInTheMap() throws {
        let rows = try source.parseClassificationHTML(
            classificationHTML,
            logoByCarNumber: ["85": "https://www.lemanscup.com/logos/race-gp.png"],
            nowUtc: Date()
        )

        XCTAssertEqual(rows.first { $0.standingsClass == .lmp3 }?.photoUrl, "https://www.lemanscup.com/logos/race-gp.png")
        XCTAssertNil(rows.first { $0.standingsClass == .lmp3ProAm }?.photoUrl)
    }

    func testParseLogosByCarNumberTakesTheCarNumberFromTheCarPageUrlNotFromText() throws {
        let html = """
            <html><body>
            <div class="card-team">
              <a class="stretched-link" href="/en/car/2026/85"></a>
              <div class="brand-logo"><img src="/logos/race-gp.png"></div>
            </div>
            <div class="card-team">
              <a class="stretched-link" href="/en/car/2026/33"></a>
            </div>
            </body></html>
            """

        let logos = try source.parseLogosByCarNumber(html)

        XCTAssertEqual(logos, ["85": "https://www.lemanscup.com/logos/race-gp.png"])
    }
}
