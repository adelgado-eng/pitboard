import Foundation

/// Los 5 idiomas que el usuario puede elegir en el selector de primer arranque
/// (LanguagePickerScreen) — equivalente exacto de `AppLanguage.kt`. Independiente del idioma
/// del sistema: el usuario elige uno a propósito la primera vez, y se queda guardado (ver
/// `AppSettingsRepository.appLanguage`) hasta que lo cambie a mano desde Ajustes.
///
/// `nativeName` siempre se enseña en SU PROPIO idioma en el selector (nunca traducido) — así
/// alguien que no lee español encuentra "English" sin tener que entender antes "Inglés".
public enum AppLanguage: String, Codable, Sendable, CaseIterable {
    case spanish = "SPANISH"
    case english = "ENGLISH"
    case catalan = "CATALAN"
    case french = "FRENCH"
    case german = "GERMAN"

    public var nativeName: String {
        switch self {
        case .spanish: "Español"
        case .english: "English"
        case .catalan: "Català"
        case .french: "Français"
        case .german: "Deutsch"
        }
    }
}
