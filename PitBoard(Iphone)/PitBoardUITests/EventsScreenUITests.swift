import XCTest

/// Equivalente de recorrer a mano `EventsScreen.kt`: lista con los eventos sembrados por
/// `UITestFixtures`, detalle al tocar uno, panel de filtro/búsqueda.
final class EventsScreenUITests: PitBoardUITestCase {

    func testSeededEventsAreListed() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        let f1Row = app.buttons["events.row.UITEST-F1-RACE"]
        XCTAssertTrue(
            f1Row.waitForExistence(timeout: 10),
            "El evento de F1 sembrado por UITestFixtures.seedIfNeeded() debería aparecer en la lista."
        )
    }

    func testTappingEventOpensDetailsSheet() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        let f1Row = app.buttons["events.row.UITEST-F1-RACE"]
        XCTAssertTrue(f1Row.waitForExistence(timeout: 10))
        f1Row.tap()

        // EventDetailsSheet muestra el nombre de la serie en mayúsculas como primera línea.
        XCTAssertTrue(
            app.staticTexts["FORMULA 1"].waitForExistence(timeout: 5),
            "El detalle del evento debería mostrar \"FORMULA 1\" (RaceSeries.displayName en mayúsculas)."
        )
    }

    func testFilterPanelSearchNarrowsTheList() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        app.buttons["events.filter"].tap()
        let searchField = app.textFields["events.searchField"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))

        searchField.tap()
        searchField.typeText("MotoGP")

        XCTAssertTrue(
            app.buttons["events.row.UITEST-MGP-QUALY"].waitForExistence(timeout: 5),
            "El evento de MotoGP debería seguir visible al buscar \"MotoGP\"."
        )
        XCTAssertFalse(
            app.buttons["events.row.UITEST-F1-RACE"].exists,
            "El evento de F1 no debería seguir visible al filtrar por \"MotoGP\" (ni el título ni la serie lo contienen)."
        )
    }

    func testRefreshButtonDoesNotBreakTheScreen() {
        XCTAssertTrue(app.navigationBars["Eventos"].waitForExistence(timeout: 10))

        let refresh = app.buttons["events.refresh"]
        XCTAssertTrue(refresh.waitForExistence(timeout: 5))
        refresh.tap()

        // Sin red real en el simulador de test: solo se comprueba que tocar el botón no
        // deja la pantalla en un estado roto (el propio `refreshNow()` ya descarta la
        // llamada si `ConnectivityMonitor` no está online).
        XCTAssertTrue(app.navigationBars["Eventos"].exists)
    }
}
