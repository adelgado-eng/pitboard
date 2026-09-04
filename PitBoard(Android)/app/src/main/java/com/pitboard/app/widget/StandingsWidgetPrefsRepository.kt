package com.pitboard.app.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.pitboard.app.data.AppTheme
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingType

/**
 * Preferencias guardadas por instancia del widget de Clasificación: categoría, si se enseña
 * Pilotos/Equipos (categorías normales) o qué clase de coche (WEC/ELMS/IMSA/Le Mans Cup, ver
 * CarBasedStandingsClasses), cuántas filas y apariencia — mismo patrón que
 * WidgetPrefsRepository para el widget de Eventos, pero cada instancia de ESTE widget puede
 * apuntar a una categoría distinta (para eso es "por instancia": varias copias del mismo
 * widget, cada una configurada a su gusto, en vez de un tipo de widget por categoría).
 */
data class StandingsWidgetConfig(
    val category: StandingsCategory,
    /** DRIVER o TEAM — ignorado en categorías "por coche" (siempre TEAM ahí). */
    val mode: StandingType,
    /** Solo relevante si [category] es "por coche" — null en el resto. */
    val carClass: StandingsClass?,
    val rowCount: Int,
    val backgroundColorHex: String,
    val appearance: AppTheme
)

object StandingsWidgetPrefsRepository {

    const val DEFAULT_ROW_COUNT = 5
    /** Valor guardado cuando el usuario elige "Todos" en el selector de filas. */
    const val NO_LIMIT = 9_999

    val DEFAULT_CATEGORY = StandingsCategory.F1
    val DEFAULT_MODE = StandingType.DRIVER
    const val DEFAULT_BACKGROUND_COLOR_HEX = "#131519"
    val DEFAULT_APPEARANCE = AppTheme.DARK

    private val KEY_CATEGORY = stringPreferencesKey("standings_category")
    private val KEY_MODE = stringPreferencesKey("standings_mode")
    private val KEY_CAR_CLASS = stringPreferencesKey("standings_car_class")
    private val KEY_ROW_COUNT = intPreferencesKey("standings_row_count")
    private val KEY_BACKGROUND_COLOR = stringPreferencesKey("standings_background_color_hex")
    private val KEY_APPEARANCE = stringPreferencesKey("standings_appearance")

    suspend fun save(context: Context, glanceId: GlanceId, config: StandingsWidgetConfig) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_CATEGORY] = config.category.name
                this[KEY_MODE] = config.mode.name
                if (config.carClass != null) {
                    this[KEY_CAR_CLASS] = config.carClass.name
                } else {
                    remove(KEY_CAR_CLASS)
                }
                this[KEY_ROW_COUNT] = config.rowCount
                this[KEY_BACKGROUND_COLOR] = config.backgroundColorHex
                this[KEY_APPEARANCE] = config.appearance.name
            }
        }
    }

    suspend fun load(context: Context, glanceId: GlanceId): StandingsWidgetConfig {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

        val category = prefs[KEY_CATEGORY]?.let { runCatching { StandingsCategory.valueOf(it) }.getOrNull() }
            ?: DEFAULT_CATEGORY
        val mode = prefs[KEY_MODE]?.let { runCatching { StandingType.valueOf(it) }.getOrNull() }
            ?: DEFAULT_MODE
        val carClass = prefs[KEY_CAR_CLASS]?.let { runCatching { StandingsClass.valueOf(it) }.getOrNull() }

        return StandingsWidgetConfig(
            category = category,
            mode = mode,
            carClass = carClass,
            rowCount = prefs[KEY_ROW_COUNT] ?: DEFAULT_ROW_COUNT,
            backgroundColorHex = prefs[KEY_BACKGROUND_COLOR] ?: DEFAULT_BACKGROUND_COLOR_HEX,
            appearance = prefs[KEY_APPEARANCE]?.let { raw ->
                try { AppTheme.valueOf(raw) } catch (_: IllegalArgumentException) { DEFAULT_APPEARANCE }
            } ?: DEFAULT_APPEARANCE
        )
    }

    fun queryLimitFor(rowCount: Int): Int = if (rowCount >= NO_LIMIT) 200 else rowCount
}
