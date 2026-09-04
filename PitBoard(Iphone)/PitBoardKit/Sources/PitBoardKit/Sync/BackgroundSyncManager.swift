import Foundation
import BackgroundTasks
import WidgetKit

/// Orquesta la sincronización en segundo plano — equivalente conjunto de `SyncWorker.kt`,
/// `RaceScheduleSyncWorker.kt`, `StandingsSyncWorker.kt`, `StandingsScheduler.kt` y
/// `RaceScheduleScheduler.kt`.
///
/// DIFERENCIA DE PLATAFORMA IMPORTANTE (léela antes de asumir paridad con Android):
/// `WorkManager` en Android GARANTIZA que un trabajo periódico se ejecute
/// aproximadamente en su intervalo declarado (30 min / 1 día / 7 días), incluso con la
/// app cerrada. `BGTaskScheduler` en iOS NO da esa garantía — `earliestBeginDate` es
/// solo un mínimo; el sistema decide si y cuándo ejecuta la tarea según el patrón de uso
/// real de la app (una app que casi no se abre puede no recibir NINGÚN ciclo en
/// segundo plano durante días). Por eso:
///   1) cada handler vuelve a programarse a sí mismo nada más empezar (no al terminar),
///      igual de "siempre hay una petición viva" que el `KEEP` de Android;
///   2) `PitBoardApp`/`RootTabView` sigue haciendo su propia sincronización directa al
///      abrir la app (ver `syncScheduleNow`/`syncStandingsNow`) — no se puede depender
///      solo de estas tareas para tener datos frescos, tal como advierte la documentación
///      de Apple para `BGTaskScheduler`.
public final class BackgroundSyncManager: @unchecked Sendable {

    public static let scheduleSyncTaskId = "com.pitboard.app.sync.schedule"
    public static let standingsSyncTaskId = "com.pitboard.app.sync.standings"
    public static let refreshTaskId = "com.pitboard.app.sync.reminders"

    private let settings: AppSettingsRepository
    private let scheduleRepository: RaceScheduleRepository
    private let standingsRepository: StandingsRepository

    public init(
        settings: AppSettingsRepository,
        scheduleRepository: RaceScheduleRepository = RaceScheduleRepository(),
        standingsRepository: StandingsRepository = StandingsRepository()
    ) {
        self.settings = settings
        self.scheduleRepository = scheduleRepository
        self.standingsRepository = standingsRepository
    }

    // MARK: - Registro (llamar una única vez, pronto en el arranque — ver PitBoardApp.init)

    public func registerTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.scheduleSyncTaskId, using: nil) { [weak self] task in
            guard let self, let task = task as? BGProcessingTask else { task.setTaskCompleted(success: false); return }
            self.handleScheduleSync(task)
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.standingsSyncTaskId, using: nil) { [weak self] task in
            guard let self, let task = task as? BGProcessingTask else { task.setTaskCompleted(success: false); return }
            self.handleStandingsSync(task)
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTaskId, using: nil) { [weak self] task in
            guard let self, let task = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            self.handleRefresh(task)
        }
    }

    // MARK: - Programación

    /// Calendario base: funcionalidad esencial de la app (ya no un interruptor opt-in), así
    /// que se programa sola al arrancar — equivalente de `RaceScheduleScheduler.schedulePeriodic`.
    public func scheduleDailyScheduleSync() {
        let request = BGProcessingTaskRequest(identifier: Self.scheduleSyncTaskId)
        request.requiresNetworkConnectivity = true
        request.earliestBeginDate = Calendar.current.date(byAdding: .day, value: 1, to: Date())
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Clasificaciones: opt-in — solo se programa cuando el usuario activa el interruptor
    /// de Ajustes (ver `StandingsScheduler.schedule`, que se llama para el mismo lunes
    /// 12:00 mediodía hora local).
    public func scheduleWeeklyStandingsSync() {
        let request = BGProcessingTaskRequest(identifier: Self.standingsSyncTaskId)
        request.requiresNetworkConnectivity = true
        request.earliestBeginDate = Self.nextMondayNoon()
        try? BGTaskScheduler.shared.submit(request)
    }

    public func cancelStandingsSync() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.standingsSyncTaskId)
    }

    /// Repinta el widget y reprograma avisos — equivalente del ciclo de 30 min de
    /// `SyncWorker`. En iOS, `BGAppRefreshTask` es más oportunista todavía que
    /// `BGProcessingTask` (pensada para ráfagas cortas, sin red garantizada), así que el
    /// intervalo de 30 min es solo el "cuanto antes" — el sistema decide el resto.
    public func scheduleRefreshCycle() {
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshTaskId)
        request.earliestBeginDate = Calendar.current.date(byAdding: .minute, value: 30, to: Date())
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: - Handlers

    private func handleScheduleSync(_ task: BGProcessingTask) {
        scheduleDailyScheduleSync()
        let work = Task {
            let result = await syncScheduleNow()
            task.setTaskCompleted(success: !result.succeeded.isEmpty || result.failed.isEmpty)
        }
        task.expirationHandler = { work.cancel() }
    }

    private func handleStandingsSync(_ task: BGProcessingTask) {
        scheduleWeeklyStandingsSync()
        let work = Task {
            let result = await standingsRepository.syncAll()
            task.setTaskCompleted(success: !result.succeeded.isEmpty || result.failed.isEmpty)
        }
        task.expirationHandler = { work.cancel() }
    }

    private func handleRefresh(_ task: BGAppRefreshTask) {
        scheduleRefreshCycle()
        let work = Task {
            let scheduler = NotificationScheduler(settings: settings)
            await scheduler.rescheduleAllUpcoming()
            reloadWidgets()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { work.cancel() }
    }

    // MARK: - Sincronización inmediata bajo demanda (botón "Actualizar", arranque de la app)

    @discardableResult
    public func syncScheduleNow() async -> RaceScheduleRepository.SyncResult {
        let result = await scheduleRepository.syncAll()
        let scheduler = NotificationScheduler(settings: settings)
        await scheduler.rescheduleAllUpcoming()
        reloadWidgets()
        return result
    }

    @discardableResult
    public func syncStandingsNow() async -> StandingsRepository.SyncResult {
        await standingsRepository.syncAll()
    }

    private func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func nextMondayNoon() -> Date {
        let calendar = Calendar(identifier: .gregorian)
        let now = Date()
        let currentWeekday = calendar.component(.weekday, from: now) // domingo=1 ... lunes=2 ... sábado=7
        let daysUntilMonday = (2 - currentWeekday + 7) % 7

        let mondayStart = calendar.date(byAdding: .day, value: daysUntilMonday, to: calendar.startOfDay(for: now)) ?? now
        var candidate = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: mondayStart) ?? mondayStart

        // Si ya es lunes pasadas las 12:00, el "próximo lunes" es el de la semana siguiente.
        if candidate <= now {
            candidate = calendar.date(byAdding: .day, value: 7, to: candidate) ?? candidate
        }
        return candidate
    }
}
