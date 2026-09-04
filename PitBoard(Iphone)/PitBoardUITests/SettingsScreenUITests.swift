import XCTest

/// Equivalente de recorrer a mano `SettingsScreen.kt`: interruptores de notificaciones y
/// clasificaciones, selector de tema.
final class SettingsScreenUITests: PitBoardUITestCase {

    private func openSettings() {
        XCTAssertTrue(app.tabBars.buttons["Ajustes"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Ajustes"].tap()
        XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 5))
    }

    func testTogglingNotificationsSwitchChangesItsValue() {
        openSettings()

        let toggle = app.switches["settings.notificationsToggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        let initialValue = toggle.value as? String

        toggle.tap()

        // El valor accesible de un Toggle es "0"/"1" — se espera a que cambie en vez de
        // comprobarlo al instante, porque `handleNotificationsToggle` es async (consulta
        // el permiso real del sistema antes de aplicar el cambio).
        let changed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value != %@", initialValue ?? ""),
            object: toggle
        )
        wait(for: [changed], timeout: 5)
    }

    func testDisablingStandingsHidesItsTab() {
        openSettings()

        // UITestFixtures.seedIfNeeded() activa Clasificaciones al arrancar en modo test.
        XCTAssertTrue(app.tabBars.buttons["Clasificaciones"].waitForExistence(timeout: 5))

        let toggle = app.switches["settings.standingsToggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        toggle.tap()

        let tabGone = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: app.tabBars.buttons["Clasificaciones"]
        )
        wait(for: [tabGone], timeout: 5)
    }

    func testSelectingDarkThemeKeepsScreenUsable() {
        openSettings()

        let darkChip = app.buttons["settings.theme.DARK"]
        XCTAssertTrue(darkChip.waitForExistence(timeout: 5))
        darkChip.tap()

        // No hay forma directa de leer el `ColorScheme` resuelto desde XCUITest — esto es
        // una comprobación de humo (el cambio no rompe la pantalla); la apariencia real
        // se revisa a mano en el simulador.
        XCTAssertTrue(app.navigationBars["Ajustes"].exists)
        XCTAssertTrue(darkChip.exists)
    }
}
