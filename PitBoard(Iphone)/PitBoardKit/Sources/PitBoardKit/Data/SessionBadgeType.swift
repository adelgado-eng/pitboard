import Foundation

/// Q / S / C / L / "" — equivalente exacto de `SessionBadgeType.kt`. Se guarda como
/// String en `EventModel.inferredBadge` (igual que en Room), no como este enum
/// directamente, para que sea trivial que una fuente escriba un valor y la UI lo lea sin
/// pasar por un enum desconocido si alguna vez apareciera un badge no contemplado aquí.
public enum SessionBadgeType: String, CaseIterable, Codable, Sendable {
    case race = "C"
    case qualy = "Q"
    case sprint = "S"
    case practice = "L"
    case other = ""

    public var label: String {
        switch self {
        case .race: "Carrera"
        case .qualy: "Clasificación"
        case .sprint: "Sprint"
        case .practice: "Libres"
        case .other: "Otros"
        }
    }

    /// Misma etiqueta que `label` pero como clave de `Strings` — `label` se deja igual a
    /// propósito (lo sigue usando `NotificationScheduler` para el texto de los avisos) y
    /// esta clave nueva es solo para quien quiera traducirla (ver EventsScreen.swift).
    public var labelKey: String {
        switch self {
        case .race: "session_race"
        case .qualy: "session_qualy"
        case .sprint: "session_sprint"
        case .practice: "session_practice"
        case .other: "session_other"
        }
    }

    public var defaultMinutes: Int {
        switch self {
        case .race: 60
        case .qualy, .sprint: 30
        case .practice: 15
        case .other: 30
        }
    }
}

/// Configuración de avisos para un tipo de sesión (badge inferido del título) —
/// equivalente de `BadgeNotificationSetting` en Android.
public struct BadgeNotificationSetting: Codable, Sendable, Hashable {
    public var enabled: Bool
    public var minutesBefore: Int

    public init(enabled: Bool, minutesBefore: Int) {
        self.enabled = enabled
        self.minutesBefore = minutesBefore
    }
}
