import XCTest

/// Arranque y navegación entre pestañas — equivalente de comprobar a mano
/// `PitBoardApp()`/`PitBoardBottomBar()` (MainActivity.kt) en Android.
final class AppLaunchUITests: PitBoardUITestCase {

    func testEventsTabIsShownAfterLaunch() {
        XCTAssertTrue(
            app.navigationBars["Eventos"].waitForExistence(timeout: 10),
            "La pantalla de Eventos debería verse nada más terminar el arranque (modo test: sin pantalla de carga real)."
        )
    }

    func testAllThreeTabsAreVisible() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        let tabBar = app.tabBars.firstMatch
        XCTAssertTrue(tabBar.buttons["Eventos"].exists)
        // Clasificaciones solo aparece si el interruptor de Ajustes está activado —
        // `UITestFixtures.seedIfNeeded()` lo activa a propósito para que esta pestaña sea
        // recorrible desde el primer test.
        XCTAssertTrue(tabBar.buttons["Clasificaciones"].waitForExistence(timeout: 5))
        XCTAssertTrue(tabBar.buttons["Ajustes"].exists)
    }

    func testSwitchingTabsUpdatesTheVisibleScreen() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        app.tabBars.buttons["Ajustes"].tap()
        XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Clasificaciones"].tap()
        XCTAssertTrue(app.navigationBars["Clasificaciones"].waitForExistence(timeout: 5))

        app.tabBars.buttons["Eventos"].tap()
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 5))
    }
}
