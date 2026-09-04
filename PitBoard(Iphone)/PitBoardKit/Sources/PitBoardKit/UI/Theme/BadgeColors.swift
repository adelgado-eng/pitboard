import SwiftUI

/// Colores de las insignias de sesión (Carrera/Clasificación/Sprint/Libres) — equivalente
/// exacto de `BadgeColors.kt`. Un único punto compartido entre las pantallas y el futuro
/// widget, igual que en Android.
public enum BadgeColors {
    public static let race = Color(hex: "#E23E7A")!
    public static let qualy = Color(hex: "#F2A93B")!
    public static let sprint = Color(hex: "#2E6DE8")!
    public static let practice = Color(hex: "#6B7280")!
    public static let fallback = Color(hex: "#5F6570")!

    public static func forBadge(_ badge: String) -> Color {
        switch badge {
        case SessionBadgeType.race.rawValue: race
        case SessionBadgeType.qualy.rawValue: qualy
        case SessionBadgeType.sprint.rawValue: sprint
        case SessionBadgeType.practice.rawValue: practice
        default: fallback
        }
    }
}
