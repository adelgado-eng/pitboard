import SwiftUI
import PitBoardKit

/// `BackgroundSyncManager` no es `@Observable` (no tiene estado que observar, solo
/// orquesta), así que se inyecta con una `EnvironmentKey` clásica en vez del atajo
/// `.environment(_:)` de tipos `@Observable` — mismo patrón, un paso más explícito.
private struct SyncManagerKey: EnvironmentKey {
    static let defaultValue: BackgroundSyncManager? = nil
}

extension EnvironmentValues {
    var syncManager: BackgroundSyncManager? {
        get { self[SyncManagerKey.self] }
        set { self[SyncManagerKey.self] = newValue }
    }
}
