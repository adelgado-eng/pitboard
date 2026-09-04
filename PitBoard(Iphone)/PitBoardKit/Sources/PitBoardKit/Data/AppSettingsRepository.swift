import Foundation
import Observation

public enum AppTheme: String, Codable, Sendable, CaseIterable {
    case light = "LIGHT"
    case dark = "DARK"
    case system = "SYSTEM"
}

/// Preferencias del usuario — equivalente exacto de `AppSettingsRepository.kt`
/// (DataStore). Usa `UserDefaults(suiteName:)` sobre el mismo App Group que la base de
/// datos: a diferencia de Android (donde DataStore vive solo en el proceso de la app),
/// aquí el widget corre en un proceso separado y también necesita leer, como mínimo, el
/// tema y qué series están activas — compartir el suite evita duplicar estado.
///
/// Reactividad: en vez de exponer cada preferencia como un `Flow` (necesario en Android
/// para que Compose se recomponga), esta clase es `@Observable` — cualquier vista SwiftUI
/// que lea una de estas propiedades se actualiza sola cuando cambia, sin envoltorio
/// adicional. Las escrituras son funciones síncronas (UserDefaults ya es E/S local
/// rápida); se mantienen como funciones con nombre explícito (`setX`), igual que los
/// `suspend fun setX` de Kotlin, en vez de `didSet` implícito, para que quede clara la
/// intención en cada punto de llamada.
@Observable
public final class AppSettingsRepository: @unchecked Sendable {

    public static let defaultMinutesBefore = 60
    public static let validMinutes: Set<Int> = [15, 30, 60]

    private let defaults: UserDefaults

    public private(set) var notificationsEnabled: Bool
    public private(set) var notificationPermissionRequested: Bool
    public private(set) var notificationMinutesBefore: Int
    public private(set) var competitiveNotificationsEnabled: Bool
    public private(set) var practiceNotificationsEnabled: Bool
    /// A diferencia de los avisos, por defecto está DESACTIVADO — requiere internet y es
    /// opt-in explícito, igual que en Android.
    public private(set) var standingsEnabled: Bool
    public private(set) var appTheme: AppTheme
    /// Series sin avisos. Vacío = ninguna excluida (todas notifican).
    public private(set) var notificationDisabledSeries: Set<RaceSeries>
    /// Series activas en el filtro rápido de Eventos. Vacío = todas.
    public private(set) var eventScreenActiveSeries: Set<RaceSeries>
    /// Tipos de sesión activos en el filtro rápido de Eventos (rawValue de
    /// SessionBadgeType). Vacío = todos.
    public private(set) var eventScreenActiveSessionTypes: Set<String>
    /// Se marca cuando termina (con éxito o sin él) la primera sincronización de arranque
    /// de Eventos + Clasificaciones — mientras sea `false`, cada apertura repite esa
    /// sincronización completa con pantalla de carga (ver MainActivity en Android).
    public private(set) var hasCompletedFirstSync: Bool

    public init(suiteName: String = AppDatabase.appGroupId) {
        let defaults = UserDefaults(suiteName: suiteName) ?? .standard
        self.defaults = defaults

        self.notificationsEnabled = defaults.object(forKey: Keys.notificationsEnabled) as? Bool ?? true
        self.notificationPermissionRequested = defaults.bool(forKey: Keys.notificationPermissionRequested)
        let storedMinutes = defaults.object(forKey: Keys.notificationMinutes) as? Int ?? Self.defaultMinutesBefore
        self.notificationMinutesBefore = Self.validMinutes.contains(storedMinutes) ? storedMinutes : Self.defaultMinutesBefore
        self.competitiveNotificationsEnabled = defaults.object(forKey: Keys.competitiveNotificationsEnabled) as? Bool ?? true
        self.practiceNotificationsEnabled = defaults.object(forKey: Keys.practiceNotificationsEnabled) as? Bool ?? false
        self.standingsEnabled = defaults.object(forKey: Keys.standingsEnabled) as? Bool ?? false
        self.appTheme = (defaults.string(forKey: Keys.appTheme)).flatMap(AppTheme.init(rawValue:)) ?? .system
        self.notificationDisabledSeries = Set(
            (defaults.stringArray(forKey: Keys.notificationDisabledSeries) ?? []).compactMap(RaceSeries.init(rawValue:))
        )
        self.eventScreenActiveSeries = Set(
            (defaults.stringArray(forKey: Keys.eventScreenSeries) ?? []).compactMap(RaceSeries.init(rawValue:))
        )
        self.eventScreenActiveSessionTypes = Set(defaults.stringArray(forKey: Keys.eventScreenSessionTypes) ?? [])
        self.hasCompletedFirstSync = defaults.bool(forKey: Keys.hasCompletedFirstSync)
    }

    public func setNotificationsEnabled(_ enabled: Bool) {
        notificationsEnabled = enabled
        defaults.set(enabled, forKey: Keys.notificationsEnabled)
    }

    public func setNotificationPermissionRequested(_ requested: Bool) {
        notificationPermissionRequested = requested
        defaults.set(requested, forKey: Keys.notificationPermissionRequested)
    }

    public func setNotificationMinutesBefore(_ minutes: Int) {
        precondition(Self.validMinutes.contains(minutes), "Antelación no soportada: \(minutes)")
        notificationMinutesBefore = minutes
        defaults.set(minutes, forKey: Keys.notificationMinutes)
    }

    public func setCompetitiveNotificationsEnabled(_ enabled: Bool) {
        competitiveNotificationsEnabled = enabled
        defaults.set(enabled, forKey: Keys.competitiveNotificationsEnabled)
    }

    public func setPracticeNotificationsEnabled(_ enabled: Bool) {
        practiceNotificationsEnabled = enabled
        defaults.set(enabled, forKey: Keys.practiceNotificationsEnabled)
    }

    public func setStandingsEnabled(_ enabled: Bool) {
        standingsEnabled = enabled
        defaults.set(enabled, forKey: Keys.standingsEnabled)
    }

    public func setAppTheme(_ theme: AppTheme) {
        appTheme = theme
        defaults.set(theme.rawValue, forKey: Keys.appTheme)
    }

    public func setNotificationDisabledSeries(_ series: Set<RaceSeries>) {
        notificationDisabledSeries = series
        if series.isEmpty {
            defaults.removeObject(forKey: Keys.notificationDisabledSeries)
        } else {
            defaults.set(series.map(\.rawValue), forKey: Keys.notificationDisabledSeries)
        }
    }

    public func setEventScreenActiveSeries(_ series: Set<RaceSeries>) {
        eventScreenActiveSeries = series
        defaults.set(series.map(\.rawValue), forKey: Keys.eventScreenSeries)
    }

    public func setEventScreenActiveSessionTypes(_ sessionTypes: Set<String>) {
        eventScreenActiveSessionTypes = sessionTypes
        defaults.set(Array(sessionTypes), forKey: Keys.eventScreenSessionTypes)
    }

    public func setHasCompletedFirstSync(_ completed: Bool) {
        hasCompletedFirstSync = completed
        defaults.set(completed, forKey: Keys.hasCompletedFirstSync)
    }

    private enum Keys {
        static let notificationsEnabled = "notifications_enabled"
        static let notificationMinutes = "notification_minutes_before"
        static let notificationPermissionRequested = "notification_permission_requested"
        static let competitiveNotificationsEnabled = "competitive_notifications_enabled"
        static let practiceNotificationsEnabled = "practice_notifications_enabled"
        static let standingsEnabled = "standings_enabled"
        static let appTheme = "app_theme"
        static let notificationDisabledSeries = "notification_disabled_series"
        static let eventScreenSeries = "event_screen_series"
        static let eventScreenSessionTypes = "event_screen_session_types"
        static let hasCompletedFirstSync = "has_completed_first_sync"
    }
}
