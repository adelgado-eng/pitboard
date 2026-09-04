import SwiftUI
import PitBoardKit

/// Punto de entrada de la app — equivalente conjunto de `PitBoardApplication.onCreate`
/// (registro del canal de notificaciones, programación de la sincronización periódica) y
/// `MainActivity.onCreate` (arranca la UI). El registro de `BGTaskScheduler` ocurre aquí,
/// en `init()`, porque Apple exige que se haga pronto en el arranque — antes de que el
/// sistema considere que la app ha terminado de lanzarse.
@main
struct PitBoardApp: App {
    @State private var settings: AppSettingsRepository
    private let syncManager: BackgroundSyncManager

    init() {
        let settings = AppSettingsRepository()
        let syncManager = BackgroundSyncManager(settings: settings)
        syncManager.registerTasks()
        syncManager.scheduleDailyScheduleSync()

        self._settings = State(initialValue: settings)
        self.syncManager = syncManager
    }

    var body: some Scene {
        WindowGroup {
            PitBoardTheme(appTheme: settings.appTheme) {
                RootTabView(settings: settings, syncManager: syncManager)
            }
            .modelContainer(AppDatabase.container)
        }
    }
}
