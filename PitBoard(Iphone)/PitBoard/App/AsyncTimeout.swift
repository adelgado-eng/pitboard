import Foundation

/// Equivalente de `kotlinx.coroutines.withTimeoutOrNull`: corre `operation` y, si no ha
/// terminado pasados `seconds`, sigue adelante igualmente — el resultado de `operation` se
/// descarta si llega tarde (igual que el original: en `RootTabView` solo importa no
/// bloquear el arranque indefinidamente, no el valor en sí). Extraído a un fichero propio
/// (en vez de quedarse como función `private` dentro de `RootTabView.swift`) para que
/// `PitBoardTests` pueda verificar su comportamiento de corte sin arrastrar toda la vista.
func withTimeout(seconds: TimeInterval, operation: @escaping @Sendable () async -> Void) async {
    await withTaskGroup(of: Void.self) { group in
        group.addTask { await operation() }
        group.addTask {
            // `_ =` evita la ambigüedad de tipo entre `Void?` (lo que devuelve `try?`
            // sobre una llamada que lanza y devuelve `Void`) y el `Void` que espera
            // `addTask` — ver la misma nota donde vivía esta función antes de extraerla.
            _ = try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
        }
        await group.next()
        group.cancelAll()
    }
}
