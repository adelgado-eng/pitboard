package com.pitboard.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Los 5 idiomas que el usuario puede elegir en el selector de primer arranque
 * (LanguagePickerScreen) — independiente del idioma del sistema: el usuario elige uno a
 * propósito la primera vez, y se queda guardado (ver AppSettingsRepository.appLanguage) hasta
 * que lo cambie a mano desde Ajustes.
 *
 * `nativeName` siempre se enseña en SU PROPIO idioma en el selector (nunca traducido) — así
 * alguien que no lee español encuentra "English" sin tener que entender antes "Inglés".
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    CATALAN("ca", "Català"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch")
}

/** Idioma activo en toda la app — se fija una vez en PitBoardApp() (MainActivity.kt) a partir
 *  de AppSettingsRepository.appLanguage, y cualquier composable de más abajo lo lee con
 *  `LocalAppLanguage.current` (ver también la función `tr()` en Strings.kt). El valor por
 *  defecto (SPANISH) solo se usa antes de que la preferencia real llegue del DataStore, nunca
 *  se enseña de verdad sin que el picker de primer arranque haya guardado algo. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SPANISH }
