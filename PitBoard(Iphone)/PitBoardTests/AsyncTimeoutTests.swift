import XCTest
@testable import PitBoard

/// Cubre el hueco que dejaba `withTimeout` sin test cuando vivía como función `private`
/// dentro de `RootTabView.swift` — equivalente de `withTimeoutOrNull` de Kotlin, usado en
/// la sincronización de arranque para no dejar la pantalla de carga colgada.
final class AsyncTimeoutTests: XCTestCase {

    func testReturnsPromptlyWhenOperationFinishesBeforeTheDeadline() async {
        let start = Date()
        var operationRan = false

        await withTimeout(seconds: 5) {
            operationRan = true
        }

        // La operación termina casi al instante — `withTimeout` no debería esperar los
        // 5 s completos del límite.
        XCTAssertTrue(operationRan)
        XCTAssertLessThan(Date().timeIntervalSince(start), 2)
    }

    func testDoesNotHangPastTheDeadlineWhenOperationNeverFinishes() async {
        let start = Date()

        await withTimeout(seconds: 1) {
            // Simula una operación que nunca termina dentro del margen (ej. una petición
            // de red colgada) — `withTimeout` debe seguir adelante igualmente.
            try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
        }

        let elapsed = Date().timeIntervalSince(start)
        XCTAssertLessThan(elapsed, 5, "withTimeout no debería esperar más allá de su propio límite (1 s) aunque la operación siga en marcha.")
    }
}
