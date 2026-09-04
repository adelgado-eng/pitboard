import XCTest
import SwiftSoup
@testable import PitBoardKit

private func rowFrom(_ html: String) throws -> Element {
    try SwiftSoup.parse(html, "https://www.imsa.com/weathertech/standings/").select("tr").first()!
}

/// Fase 1 del diagnóstico (graphify): IMSA es la fuente con más pasos encadenados (API de
/// clases -> tabla AJAX por clase -> ficha de coche) — se testean los tres tramos de
/// parsing puro por separado. El caso más importante es la detección de logo: el propio
/// comentario de la clase documenta que "buscar TeamLogo en la URL" no funcionaba para la
/// mayoría de equipos, y que lo fiable es descartar por posición (1º = logo fijo de la
/// serie, luego placeholder).
final class ImsaStandingsSourceTests: XCTestCase {

    private let source = ImsaStandingsSource()

    func testParseClassIdsResolvesEachClassIdByItsShortcode() throws {
        let json = """
            [{"id":194,"shortcode":"GTP"},{"id":196,"shortcode":"LMP2"},{"id":192,"shortcode":"GTD PRO"},{"id":191,"shortcode":"GTD"}]
            """

        let ids = try source.parseClassIds(json)

        XCTAssertEqual(ids["GTP"], "194")
        XCTAssertEqual(ids["GTD PRO"], "192")
    }

    func testParseTeamRowSplitsCarNumberAndTeamNameFromTheCellText() throws {
        let row = try rowFrom("""
            <table><tr>
                <td class="team-col"><a class="team-name" href="/racing-teams/13-autosport/">#13 13 Autosport</a></td>
                <td class="totalpoints">245</td>
            </tr></table>
            """)

        let teamRow = try XCTUnwrap(source.parseTeamRow(row, standingsClass: .gtp, position: 1))

        XCTAssertEqual(teamRow.carNumber, "13")
        XCTAssertEqual(teamRow.teamName, "13 Autosport")
        XCTAssertEqual(teamRow.points, 245.0)
        XCTAssertEqual(teamRow.teamUrl, "https://www.imsa.com/racing-teams/13-autosport/")
    }

    func testATeamWithoutItsOwnPageYetStillParsesJustWithoutTeamUrl() throws {
        let row = try rowFrom("""
            <table><tr>
                <td class="team-col">#99 Equipo Nuevo</td>
                <td class="totalpoints">10</td>
            </tr></table>
            """)

        let teamRow = try XCTUnwrap(source.parseTeamRow(row, standingsClass: .gtd, position: 5))

        XCTAssertEqual(teamRow.teamName, "Equipo Nuevo")
        XCTAssertNil(teamRow.teamUrl)
    }

    private let teamPageHTML = """
        <html><body>
        <div class="team-logos">
          <img src="/logos/weathertech_championship.png">
          <img src="/logos/13autosport_logo.png">
        </div>
        <div class="imsa-card_item_widget">
          <p class="imsa-ciw-title">Ben Keating</p>
          <img class="imsa-ciw-image" src="/placeholder.gif" data-src="/photos/keating.jpg">
        </div>
        <div class="imsa-card_item_widget">
          <p class="imsa-ciw-title"></p>
          <img class="imsa-ciw-image" src="/placeholder.gif" data-src="/photos/empty.jpg">
        </div>
        </body></html>
        """

    func testDropsTheFixedSeriesLogoAndKeepsTheTeamsOwnLogo() throws {
        let page = try source.parseTeamPage(
            teamPageHTML,
            teamUrl: "https://www.imsa.com/racing-teams/13-autosport/",
            standingsClass: .gtd,
            carNumber: "13",
            nowUtc: Date()
        )

        XCTAssertEqual(page.logoUrl, "https://www.imsa.com/logos/13autosport_logo.png")
    }

    func testWithoutARealTeamLogoNeitherTheFixedOneNorThePlaceholderCountAsLogo() throws {
        let html = """
            <html><body>
            <div class="team-logos">
              <img src="/logos/weathertech_championship.png">
              <img src="/logos/nologo_0.jpg">
            </div>
            </body></html>
            """

        let page = try source.parseTeamPage(html, teamUrl: "https://www.imsa.com/x", standingsClass: .gtd, carNumber: "13", nowUtc: Date())

        XCTAssertNil(page.logoUrl)
    }

    func testDriverPhotoComesFromDataSrcNotThePlaceholderSrc() throws {
        let page = try source.parseTeamPage(
            teamPageHTML,
            teamUrl: "https://www.imsa.com/racing-teams/13-autosport/",
            standingsClass: .gtd,
            carNumber: "13",
            nowUtc: Date()
        )

        XCTAssertEqual(page.drivers.count, 1) // la tarjeta sin nombre se descarta
        XCTAssertEqual(page.drivers[0].name, "Ben Keating")
        XCTAssertEqual(page.drivers[0].photoUrl, "https://www.imsa.com/photos/keating.jpg")
        XCTAssertEqual(page.drivers[0].carNumber, "13")
    }
}
