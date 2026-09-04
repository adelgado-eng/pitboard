import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): AcoCarDriversSource es la fuente de pilotos
/// compartida por WEC y Le Mans Cup (misma plantilla ACO). Se instancia aquí igual que la
/// usa WecStandingsSource en producción: con sus propios classMatchers por el badge de
/// clase.
final class AcoCarDriversSourceTests: XCTestCase {

    private let source = AcoCarDriversSource(
        category: .wec,
        listingUrl: "https://www.fiawec.com/en/page/grid",
        classMatchers: [
            (.hypercar, { $0.contains("Hypercar") }),
            (.lmgt3, { $0.contains("LMGT3") })
        ]
    )

    func testResolvesClassByTheBadgeAndDropsCardsOfUnknownClass() throws {
        let html = """
            <html><body>
            <div class="card-team"><span class="fs-11">Hypercar</span><a class="stretched-link" href="/en/car/2026/50"></a></div>
            <div class="card-team"><span class="fs-11">LMGT3</span><a class="stretched-link" href="/en/car/2026/33"></a></div>
            <div class="card-team"><span class="fs-11">LMP2 (no cubierta este año)</span><a class="stretched-link" href="/en/car/2026/99"></a></div>
            </body></html>
            """

        let refs = try source.parseCarRefs(html)

        XCTAssertEqual(refs.count, 2)
        XCTAssertEqual(refs[0].standingsClass, .hypercar)
        XCTAssertEqual(refs[0].carUrl, "https://www.fiawec.com/en/car/2026/50")
        XCTAssertEqual(refs[1].standingsClass, .lmgt3)
        XCTAssertEqual(refs[1].carUrl, "https://www.fiawec.com/en/car/2026/33")
    }

    func testReadsNameAndRealPhotoOfEachDriverDroppingCardsWithoutName() throws {
        let html = """
            <html><body>
            <a class="card-driver"><div class="py-4">Antonio Fuoco</div><img src="/photos/fuoco.jpg"></a>
            <a class="card-driver"><div class="py-4"></div><img src="/photos/empty.jpg"></a>
            </body></html>
            """

        let drivers = try source.parseCarPage(
            html,
            carUrl: "https://www.fiawec.com/en/car/2026/50",
            carNumber: "50",
            standingsClass: .hypercar,
            nowUtc: Date()
        )

        XCTAssertEqual(drivers.count, 1)
        XCTAssertEqual(drivers[0].name, "Antonio Fuoco")
        XCTAssertEqual(drivers[0].carNumber, "50")
        XCTAssertEqual(drivers[0].photoUrl, "https://www.fiawec.com/photos/fuoco.jpg")
        XCTAssertEqual(drivers[0].category, .wec)
    }
}
