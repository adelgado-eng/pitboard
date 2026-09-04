import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): ElmsDriversSource asocia cada piloto a un coche
/// recordando el último `<h2>` de clase visto mientras recorre el documento en orden — el
/// fixture reproduce dos clases seguidas para comprobar que el piloto de la segunda
/// tarjeta no hereda la clase de la primera.
final class ElmsDriversSourceTests: XCTestCase {

    private let source = ElmsDriversSource()

    private let fixtureHTML = """
        <html><body>
        <h2 class="h3 text-center">LMP2</h2>
        <div class="card-driver">
          <div class="driver-thumb"><img src="/photos/chatin.jpg"></div>
          <div class="driver-name">Paul-Loup Chatin</div>
          <div class="driver-team">IDEC SPORT #18</div>
        </div>
        <h2 class="h3 text-center">LMGT3</h2>
        <div class="card-driver">
          <div class="driver-thumb"><img src="/photos/keating.jpg"></div>
          <div class="driver-name">Ben Keating</div>
          <div class="driver-team">TF SPORT #33</div>
        </div>
        </body></html>
        """

    func testEachDriverBelongsToTheClassOfItsOwnSectionNotThePrevious() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows.first { $0.name == "Paul-Loup Chatin" }?.standingsClass, .lmp2)
        XCTAssertEqual(rows.first { $0.name == "Ben Keating" }?.standingsClass, .lmgt3)
    }

    func testExtractsCarNumberFromTheEndOfTheTeamText() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(rows.first { $0.name == "Paul-Loup Chatin" }?.carNumber, "18")
        XCTAssertEqual(rows.first { $0.name == "Ben Keating" }?.carNumber, "33")
    }

    func testResolvesPhotoToAnAbsoluteUrl() throws {
        let rows = try source.parseHTML(fixtureHTML, nowUtc: Date())

        XCTAssertEqual(
            rows.first { $0.name == "Paul-Loup Chatin" }?.photoUrl,
            "https://www.europeanlemansseries.com/photos/chatin.jpg"
        )
    }

    func testACardWithoutCarNumberIsDroppedInsteadOfMisassociated() throws {
        let html = """
            <html><body>
            <h2 class="h3 text-center">LMP2</h2>
            <div class="card-driver">
              <div class="driver-name">Piloto Sin Coche</div>
              <div class="driver-team">EQUIPO SIN NUMERO</div>
            </div>
            </body></html>
            """

        let rows = try source.parseHTML(html, nowUtc: Date())

        XCTAssertTrue(rows.isEmpty)
    }
}
