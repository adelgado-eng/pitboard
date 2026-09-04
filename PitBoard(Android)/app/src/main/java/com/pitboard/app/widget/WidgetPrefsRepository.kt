package com.pitboard.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.pitboard.app.data.AppTheme
import com.pitboard.app.data.RaceSeries

enum class WidgetStyle { COMPACTO, DETALLADO }

/**
 * SOLIDO = fondo opaco al 100 %.
 * DESENFOCADO = tinte semitransparente sobre el fondo del móvil; la opacidad la controla
 * [WidgetPrefsRepository.FIXED_BACKGROUND_OPACITY]. Android/Glance no permite blur
 * real en widgets; esto es la mejor aproximación disponible.
 */
enum class WidgetBackgroundMode { SOLIDO, DESENFOCADO }

/**
 * Preferencias guardadas por instancia de widget: series activas, cuántos eventos, palabras
 * del título, color de fondo y apariencia (Claro/Oscuro/Auto — independiente del color de
 * fondo, que sigue siendo libre).
 */
data class WidgetConfig(
    val activeSeries: Set<RaceSeries>,
    val eventCount: Int,
    val wordCount: Int,
    val backgroundColorHex: String,
    val showTrackTime: Boolean,
    val appearance: AppTheme = AppTheme.DARK
)

object WidgetPrefsRepository {

    const val DEFAULT_EVENT_COUNT = 10
    const val DEFAULT_WORD_COUNT = 4
    /** Valor guardado cuando el usuario elige "Todos" en el selector de cantidad. */
    const val NO_LIMIT = 9_999

    val DEFAULT_BACKGROUND_MODE = WidgetBackgroundMode.DESENFOCADO
    const val DEFAULT_BACKGROUND_COLOR_HEX = "#131519"
    /** DARK preserva el aspecto de siempre para los widgets ya instalados (nunca han
     *  guardado esta preferencia, así que no deben cambiar de aspecto solos). */
    val DEFAULT_APPEARANCE = AppTheme.DARK
    /** Opacidad fija del fondo del widget — sin ajuste en la configuración. */
    const val FIXED_BACKGROUND_OPACITY = 72

    private val KEY_SERIES = stringSetPreferencesKey("active_series")
    private val KEY_STYLE = stringPreferencesKey("style")
    private val KEY_EVENT_COUNT = intPreferencesKey("event_count")
    private val KEY_WORD_COUNT = intPreferencesKey("word_count")
    private val KEY_BACKGROUND_MODE = stringPreferencesKey("background_mode")
    private val KEY_BACKGROUND_COLOR = stringPreferencesKey("background_color_hex")
    private val KEY_BACKGROUND_OPACITY = intPreferencesKey("background_opacity_percent")
    private val KEY_SHOW_TRACK_TIME = booleanPreferencesKey("show_track_time")
    private val KEY_APPEARANCE = stringPreferencesKey("appearance")

    suspend fun save(context: Context, glanceId: GlanceId, config: WidgetConfig) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_SERIES] = config.activeSeries.map { it.name }.toSet()
                this[KEY_STYLE] = WidgetStyle.DETALLADO.name
                this[KEY_EVENT_COUNT] = config.eventCount
                this[KEY_WORD_COUNT] = config.wordCount
                this[KEY_BACKGROUND_MODE] = WidgetBackgroundMode.DESENFOCADO.name
                this[KEY_BACKGROUND_COLOR] = config.backgroundColorHex
                this[KEY_BACKGROUND_OPACITY] = FIXED_BACKGROUND_OPACITY
                this[KEY_SHOW_TRACK_TIME] = config.showTrackTime
                this[KEY_APPEARANCE] = config.appearance.name
            }
        }
    }

    suspend fun load(context: Context, glanceId: GlanceId): WidgetConfig {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return fromPreferences(prefs)
    }

    fun fromPreferences(prefs: Preferences): WidgetConfig {
        val series = prefs[KEY_SERIES]
            ?.mapNotNull { runCatching { RaceSeries.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

        return WidgetConfig(
            activeSeries = series,
            eventCount = (prefs[KEY_EVENT_COUNT] ?: DEFAULT_EVENT_COUNT).let { saved ->
                if (saved == Int.MAX_VALUE) NO_LIMIT else saved
            },
            wordCount = prefs[KEY_WORD_COUNT] ?: DEFAULT_WORD_COUNT,
            backgroundColorHex = prefs[KEY_BACKGROUND_COLOR] ?: DEFAULT_BACKGROUND_COLOR_HEX,
            showTrackTime = prefs[KEY_SHOW_TRACK_TIME] ?: true,
            appearance = prefs[KEY_APPEARANCE]?.let { raw ->
                try { AppTheme.valueOf(raw) } catch (_: IllegalArgumentException) { DEFAULT_APPEARANCE }
            } ?: DEFAULT_APPEARANCE
        )
    }

    fun queryLimitFor(eventCount: Int): Int =
        if (eventCount >= NO_LIMIT) NO_LIMIT else eventCount

    /** Series activas de verdad: vacío (widget recién colocado, nunca configurado) significa
     *  "todas". Antes esto necesitaba "autocurar" referencias a calendarios borrados o
     *  reimportados — ya no hace falta, RaceSeries es una lista fija que nunca desaparece. */
    fun effectiveSeries(config: WidgetConfig): Set<RaceSeries> =
        config.activeSeries.ifEmpty { RaceSeries.entries.toSet() }
}
