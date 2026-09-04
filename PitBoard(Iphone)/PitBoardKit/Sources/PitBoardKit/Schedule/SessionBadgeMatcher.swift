import Foundation

/// Traduce el nombre de una sesión (tal como lo da cada fuente: "Qualifying", "Free
/// Practice 2", "Sprint Race", "Race"...) al badge Q/S/C/L — equivalente exacto de
/// `SessionBadgeMatcher.kt`. Lo usan las fuentes cuyo dato de origen distingue el tipo de
/// sesión por nombre (JSON-LD de F2/F3/ELMS, tablas de horario de IMSA/GT World
/// Challenge) — MotoGP no lo necesita porque la API de Pulselive ya da un "kind" explícito.
public enum SessionBadgeMatcher {
    public static func match(_ sessionName: String) -> String {
        let t = sessionName.lowercased()
        if t.contains("qualifying") || t.contains("quali") || t.contains("shootout") {
            return SessionBadgeType.qualy.rawValue
        }
        if t.contains("sprint") {
            return SessionBadgeType.sprint.rawValue
        }
        if t.contains("practice") || t.contains("warm up") || t.contains("warmup") || t.hasPrefix("fp") {
            return SessionBadgeType.practice.rawValue
        }
        if t.contains("race") || t.contains("grand prix") {
            return SessionBadgeType.race.rawValue
        }
        return SessionBadgeType.other.rawValue
    }
}
