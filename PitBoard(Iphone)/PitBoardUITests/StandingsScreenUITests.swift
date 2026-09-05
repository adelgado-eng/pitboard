import XCTest

/// Equivalente de recorrer a mano `StandingsScreen.kt` + `CategoryStandingsScreen.kt`:
/// lista de categorías con el líder de cada una, navegación al detalle y vuelta atrás.
final class StandingsScreenUITests: PitBoardUITestCase {

    func testNavigatingIntoACategoryAndBack() {
        let standingsTab = app.tabBars.buttons["Clasificaciones"]
        XCTAssertTrue(
            standingsTab.waitForExistence(timeout: 10),
            "UITestFixtures.seedIfNeeded() activa Clasificaciones al arrancar en modo test."
        )
        standingsTab.tap()
        XCTAssertTrue(app.navigationBars["Clasificaciones"].waitForExistence(timeout: 5))

        // `CategoryRow` no es un `Button` (usa `.onTapGesture` sobre un `HStack`), así que
        // su identificador de accesibilidad puede exponerse como un tipo de elemento
        // distinto según la versión de SwiftUI — `descendants(matching: .any)` lo
        // encuentra sin depender de si el sistema lo clasifica como botón u otro tipo.
        let f1Row = app.descendants(matching: .any).matching(identifier: "standings.row.F1").firstMatch
        XCTAssertTrue(
            f1Row.waitForExistence(timeout: 5),
            "La fila de F1 (sembrada con un líder por UITestFixtures) debería estar en la lista."
        )
        f1Row.tap()

        XCTAssertTrue(
            app.staticTexts["Formula 1"].waitForExistence(timeout: 5),
            "CategoryStandingsScreen debería mostrar el nombre de la categoría en la barra superior."
        )
        // `StandingRowView` aplica `.accessibilityElement(children: .combine)` (para que
        // VoiceOver lea posición/nombre/equipo/puntos como un solo elemento) — eso funde el
        // `Text` del nombre dentro de un elemento combinado, así que ya no existe como
        // `staticTexts` independiente. Mismo problema que la fila de la lista de categorías
        // (comentario más arriba): hay que buscar por `label` en vez de por tipo exacto.
        let driverRow = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Piloto de Prueba"))
            .firstMatch
        XCTAssertTrue(
            driverRow.waitForExistence(timeout: 5),
            "El piloto sembrado en posición 1 (StandingModel de UITestFixtures) debería verse en la lista."
        )

        // El botón atrás nativo de NavigationStack se etiqueta con el título de la
        // pantalla anterior ("Clasificaciones") — más fiable que asumir que es "el primer
        // botón de la barra".
        let backButton = app.navigationBars.buttons["Clasificaciones"]
        XCTAssertTrue(backButton.waitForExistence(timeout: 5))
        backButton.tap()

        XCTAssertTrue(app.navigationBars["Clasificaciones"].waitForExistence(timeout: 5))
    }
}
