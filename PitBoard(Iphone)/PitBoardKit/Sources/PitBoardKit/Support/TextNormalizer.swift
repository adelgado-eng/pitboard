import Foundation

/// `normalize()` aparece repetida, byte a byte igual, en al menos 7 fuentes de Android
/// (`OfficialRosterStandingsSource`, `RosterNameFilter`, `DriverDbStandingsSource`,
/// `MotorsportStandingsHtmlSource`, `AcoCarDriversSource`, `ElmsDriversSource`...). Aquí
/// se centraliza en un solo sitio — mismo comportamiento exacto, sin la duplicación.
public enum TextNormalizer {

    /// Quita tildes (NFD + elimina la marca diacrítica, categoría Unicode Mn — no basta
    /// con filtrar `[^a-z0-9\s]` directamente, eso BORRARÍA la tilde en vez de convertir
    /// la letra a su base: "Jorge Martín" -> "jorge martn" en vez de "jorge martin"),
    /// pasa a minúsculas, quita cualquier caracter que no sea alfanumérico o espacio, y
    /// colapsa espacios múltiples.
    public static func normalize(_ s: String) -> String {
        let folded = s.folding(options: .diacriticInsensitive, locale: nil)
        let lowered = folded.lowercased()
        let filtered = lowered.unicodeScalars.map { scalar -> Character in
            (CharacterSet.alphanumerics.contains(scalar) || scalar == " ") ? Character(scalar) : " "
        }
        let collapsed = String(filtered)
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return collapsed
    }

    /// slug en minúsculas separado por guiones (ej. "Max Verstappen" -> "max-verstappen") —
    /// usado por las plantillas de perfil oficial ("{slug}") en varias fuentes de standings.
    public static func slugify(_ s: String) -> String {
        normalize(s).replacingOccurrences(of: " ", with: "-")
    }
}
