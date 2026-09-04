import XCTest

/// Caso base compartido por todos los tests de UI: lanza la app con el argumento
/// "-uiTesting", que activa `UITestSupport.isUITesting` en el target de la app (ver
/// `PitBoard/App/UITestSupport.swift`) — sin red, sin diálogo real de permisos, con datos
/// fijos sembrados por `UITestFixtures` para que cada test parta del mismo estado.
///
/// HONESTO: los tests de UI corren en un proceso SEPARADO del de la app (a diferencia de
/// los tests unitarios de `PitBoardTests`, que se inyectan dentro de ella) — por eso este
/// target no puede hacer `import PitBoard`/`PitBoardKit` ni llamar a `UITestSupport`
/// directamente. La única forma de comunicarse con la app es a través del árbol de
/// accesibilidad (`XCUIApplication`), por lo que el string "-uiTesting" está DUPLICADO a
/// propósito en `UITestSupport.launchArgument` — si cambias uno, cambia el otro.
class PitBoardUITestCase: XCTestCase {
    private static let uiTestingLaunchArgument = "-uiTesting"

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += [Self.uiTestingLaunchArgument]
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }
}
