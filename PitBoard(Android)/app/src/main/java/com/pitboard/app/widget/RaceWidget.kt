package com.pitboard.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.pitboard.app.ui.theme.BadgeColors
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppTheme
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.util.ColorContrast
import com.pitboard.app.util.DateTimeFormatters
import com.pitboard.app.util.EventWeekendGrouper
import com.pitboard.app.util.SeasonWindow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lean & Fast RaceWidget - Minimized processing for maximum speed on Samsung.
 */

private val FALLBACK_COLOR = Color(0xFF5F6570)

/** Colores de texto y tarjetas internas del widget. Antes eran fijos (siempre oscuro);
 *  ahora dependen de la apariencia elegida al configurar el widget (Claro/Oscuro/Auto). El
 *  color de fondo general (backgroundColorHex) sigue siendo libre, independiente de esto. */
private data class WidgetPalette(val chalk: Color, val chalkDim: Color, val cardBg: Color)

private val DarkWidgetPalette = WidgetPalette(
    chalk = Color(0xFFEEF0F2),
    chalkDim = Color(0xFF9AA0AB),
    cardBg = Color(0xFF1C1F26)
)

private val LightWidgetPalette = WidgetPalette(
    chalk = Color(0xFF181815),
    chalkDim = Color(0xFF5F6570),
    cardBg = Color(0xFFFFFFFF)
)

private val LocalWidgetPalette = staticCompositionLocalOf { DarkWidgetPalette }

// Tamaños fijos que cubren mini/hero/lista, alineados con los minWidth/minHeight de
// race_widget_info_small/medium/large.xml.
// FIX: con SizeMode.Exact, Glance genera una vista por cada tamaño EXACTO en píxeles
// durante el redimensionado libre de One UI (Samsung); esa generación falla o se cuelga
// en silencio de forma intermitente, y el widget se queda en el initialLayout estático
// (el 🏁 fijo) o falla "de repente" sin motivo aparente. SizeMode.Responsive con un set
// fijo de tamaños es la solución recomendada: solo hay que generar estas 3 variantes.
private val WIDGET_SIZES = setOf(
    DpSize(110.dp, 40.dp),   // pequeño (mini row)
    DpSize(140.dp, 110.dp),  // mediano (hero)
    DpSize(250.dp, 320.dp)   // grande (lista)
)

class RaceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val contextLocal = LocalContext.current

            val state by produceState<WidgetReadyState?>(initialValue = null) {
                try {
                    val db = AppDatabase.getInstance(contextLocal)
                    val savedConfig = WidgetPrefsRepository.load(contextLocal, id)
                    val appWidgetId = run {
                        val manager = GlanceAppWidgetManager(contextLocal)
                        var resolved = AppWidgetManager.INVALID_APPWIDGET_ID
                        repeat(5) {
                            resolved = try { manager.getAppWidgetId(id) } catch (_: Exception) { AppWidgetManager.INVALID_APPWIDGET_ID }
                            if (resolved != AppWidgetManager.INVALID_APPWIDGET_ID) return@run resolved
                            delay(200.milliseconds)
                        }
                        resolved
                    }

                    val config = savedConfig
                    val seriesConfigs = db.seriesConfigDao().getAll().associateBy { it.series }
                    val activeSeries = WidgetPrefsRepository.effectiveSeries(config).toList()
                    val now = System.currentTimeMillis()

                    val events = db.eventDao().getFilteredUpcoming(
                        now,
                        SeasonWindow.endOfCurrentYearUtc(now),
                        activeSeries,
                        WidgetPrefsRepository.queryLimitFor(config.eventCount)
                    )

                    val useDark = when (config.appearance) {
                        AppTheme.LIGHT -> false
                        AppTheme.DARK -> true
                        AppTheme.SYSTEM -> {
                            val nightMode = contextLocal.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                            nightMode == Configuration.UI_MODE_NIGHT_YES
                        }
                    }

                    value = WidgetReadyState(
                        config,
                        events.distinctBy { it.fullTitle to it.startTimeUtc }.take(WidgetPrefsRepository.queryLimitFor(config.eventCount)),
                        seriesConfigs,
                        appWidgetId,
                        useDark
                    )
                } catch (e: Exception) {
                    Log.e("RaceWidget", "Load error", e)
                }
            }

            GlanceTheme {
                state?.let { WidgetUI(it) } ?: Box(GlanceModifier.fillMaxSize().background(DarkWidgetPalette.cardBg).cornerRadius(24.dp), contentAlignment = Alignment.Center) {
                    Text("🏁", style = TextStyle(fontSize = 24.sp))
                }
            }
        }
    }

    private data class WidgetReadyState(
        val config: WidgetConfig,
        val events: List<EventEntity>,
        val seriesConfigs: Map<RaceSeries, SeriesConfigEntity>,
        val appWidgetId: Int,
        val useDark: Boolean
    )

    @Composable
    private fun WidgetUI(state: WidgetReadyState) {
        val palette = if (state.useDark) DarkWidgetPalette else LightWidgetPalette
        CompositionLocalProvider(LocalWidgetPalette provides palette) {
            val size = LocalSize.current
            val isList = size.height >= 80.dp && size.width >= 180.dp
            val bgColor = try { Color(state.config.backgroundColorHex.toColorInt()).copy(alpha = 0.72f) } catch (_: Exception) { palette.cardBg }

            Column(GlanceModifier.fillMaxSize().background(bgColor).cornerRadius(24.dp).padding(10.dp).clickable(actionRunCallback<UpdateAction>())) {
                if (isList) {
                    WidgetHeader(state.appWidgetId)
                    Box(GlanceModifier.height(8.dp)) {}
                }

                if (state.events.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin eventos", style = TextStyle(color = ColorProvider(palette.chalkDim))) }
                } else {
                    when {
                        size.height < 80.dp -> MiniRow(state.events.first(), state.seriesConfigs, state.config)
                        size.width < 180.dp -> HeroRow(state.events.first(), state.seriesConfigs, state.config)
                        else -> EventList(state.events, state.seriesConfigs, state.config)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetHeader(appWidgetId: Int) {
        val context = LocalContext.current
        val palette = LocalWidgetPalette.current
        val editIntent = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent(context, RaceWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else null

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🏁 PitBoard", style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium))
            Box(GlanceModifier.defaultWeight()) {}
            editIntent?.let {
                Text("✎", modifier = GlanceModifier.background(palette.cardBg).cornerRadius(14.dp).padding(horizontal = 16.dp, vertical = 8.dp).clickable(actionStartActivity(it)),
                    style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    private fun EventList(events: List<EventEntity>, seriesConfigs: Map<RaceSeries, SeriesConfigEntity>, config: WidgetConfig) {
        val palette = LocalWidgetPalette.current
        val groups = EventWeekendGrouper.split(events)
        LazyColumn(GlanceModifier.fillMaxSize()) {
            if (groups.weekendEvents.isNotEmpty()) {
                item {
                    Column(GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(groups.weekendLabel.uppercase(), style = TextStyle(color = ColorProvider(palette.chalkDim), fontWeight = FontWeight.Bold, fontSize = 11.sp), modifier = GlanceModifier.padding(bottom = 6.dp, start = 4.dp))
                        Column(GlanceModifier.fillMaxWidth().background(palette.cardBg).cornerRadius(20.dp)) {
                            groups.weekendEvents.forEachIndexed { idx, event ->
                                EventItem(event, seriesConfigs, config, false)
                                if (idx < groups.weekendEvents.lastIndex) Box(GlanceModifier.fillMaxWidth().height(0.5.dp).background(palette.chalkDim.copy(alpha = 0.2f)).padding(horizontal = 16.dp)) {}
                            }
                        }
                    }
                }
            }
            itemsIndexed(groups.laterEvents) { _, event ->
                EventItem(event, seriesConfigs, config, true)
                Box(GlanceModifier.height(8.dp)) {}
            }
        }
    }

    @Composable
    private fun EventItem(event: EventEntity, seriesConfigs: Map<RaceSeries, SeriesConfigEntity>, config: WidgetConfig, useContainer: Boolean) {
        val palette = LocalWidgetPalette.current
        val cat = seriesConfigs[event.series]
        val color = try { Color((cat?.colorHex ?: "#5F6570").toColorInt()) } catch (_: Exception) { FALLBACK_COLOR }
        val contrastColor = ColorContrast.ensureContrast(color, palette.cardBg)
        val textOnTag = ColorContrast.readableTextColor(contrastColor)
        val subtitle = buildString { append(DateTimeFormatters.formatEventDateTime(event.startTimeUtc)); if (config.showTrackTime) trackTimeLabel(event.startTimeUtc, event.timeZoneId)?.let { append(" ($it)") } }

        val content = @Composable {
            Row(GlanceModifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.width(44.dp).fillMaxHeight().background(contrastColor).cornerRadius(8.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Text(cat?.tag ?: event.series.defaultTag, style = TextStyle(color = ColorProvider(textOnTag), fontWeight = FontWeight.Bold))
                    Text("D-${daysUntil(event.startTimeUtc)}", style = TextStyle(color = ColorProvider(textOnTag.copy(alpha = 0.8f)), fontSize = 10.sp))
                }
                Column(GlanceModifier.defaultWeight().padding(horizontal = 10.dp)) {
                    Text(eventDisplayName(event.fullTitle, event.series.displayName, config.wordCount), style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(subtitle, style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 11.sp), maxLines = 1)
                }
                SessionBadge(event.inferredBadge)
                Box(GlanceModifier.width(8.dp)) {}
            }
        }
        if (useContainer) Box(GlanceModifier.fillMaxWidth().background(palette.cardBg).cornerRadius(16.dp)) { content() } else content()
    }

    @Composable
    private fun MiniRow(event: EventEntity, seriesConfigs: Map<RaceSeries, SeriesConfigEntity>, config: WidgetConfig) {
        val palette = LocalWidgetPalette.current
        val cat = seriesConfigs[event.series]
        val color = ColorContrast.ensureContrast(try { Color((cat?.colorHex ?: "#5F6570").toColorInt()) } catch (_: Exception) { FALLBACK_COLOR }, palette.cardBg)
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(36.dp).height(28.dp).background(color).cornerRadius(8.dp), contentAlignment = Alignment.Center) {
                Text(cat?.tag ?: event.series.defaultTag, style = TextStyle(color = ColorProvider(ColorContrast.readableTextColor(color)), fontWeight = FontWeight.Bold))
            }
            Box(GlanceModifier.width(8.dp)) {}
            Column(GlanceModifier.defaultWeight()) {
                Text(eventDisplayName(event.fullTitle, event.series.displayName, config.wordCount), style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium), maxLines = 1)
                if (config.showTrackTime) trackTimeLabel(event.startTimeUtc, event.timeZoneId)?.let { Text("Pista: $it", style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 10.sp), maxLines = 1) }
            }
            Text("D-${daysUntil(event.startTimeUtc)}", style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 12.sp))
        }
    }

    @Composable
    private fun HeroRow(event: EventEntity, seriesConfigs: Map<RaceSeries, SeriesConfigEntity>, config: WidgetConfig) {
        val palette = LocalWidgetPalette.current
        val cat = seriesConfigs[event.series]
        val color = ColorContrast.ensureContrast(try { Color((cat?.colorHex ?: "#5F6570").toColorInt()) } catch (_: Exception) { FALLBACK_COLOR }, palette.cardBg)
        val textOnTag = ColorContrast.readableTextColor(color)
        Column(GlanceModifier.fillMaxSize().background(palette.cardBg).cornerRadius(16.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(56.dp).height(40.dp).background(color).cornerRadius(12.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cat?.tag ?: event.series.defaultTag, style = TextStyle(color = ColorProvider(textOnTag), fontWeight = FontWeight.Bold))
                    Text("D-${daysUntil(event.startTimeUtc)}", style = TextStyle(color = ColorProvider(textOnTag.copy(alpha = 0.85f)), fontSize = 10.sp))
                }
            }
            Box(GlanceModifier.height(8.dp)) {}
            Text(eventDisplayName(event.fullTitle, event.series.displayName, config.wordCount), style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium), maxLines = 2)
            Box(GlanceModifier.height(4.dp)) {}
            Text(DateTimeFormatters.formatEventDateTime(event.startTimeUtc), style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 12.sp))
        }
    }

    @Composable
    private fun SessionBadge(badge: String) {
        if (badge.isEmpty()) return
        Box(GlanceModifier.size(30.dp).background(BadgeColors.forBadge(badge)).cornerRadius(15.dp), contentAlignment = Alignment.Center) {
            Text(badge, style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold))
        }
    }

    private fun eventDisplayName(full: String, series: String, words: Int): String {
        val parts = full.split(" - ").map { it.trim() }.filter { p -> !listOf("carrera", "race", "calificacion", "calificación", "clasificacion", "clasificación", "qualifying", "qualy", "sprint", "libre", "entrenamiento", "practice", "warm", "shootout").any { it in p.lowercase() } }
        val body = if (parts.size > 1 && parts.first().equals(series, true)) parts.drop(1).joinToString(" - ") else parts.joinToString(" - ")
        val list = (if (body.isBlank()) full else body).split(" ")
        return if (list.size <= words) list.joinToString(" ") else list.take(words).joinToString(" ") + "…"
    }

    private fun trackTimeLabel(utc: Long, tzId: String?): String? {
        val tz = try { ZoneId.of(tzId ?: return null) } catch (_: Exception) { return null }
        if (ZoneId.systemDefault().id == tz.id) return null
        val d = Instant.ofEpochMilli(utc).atZone(tz)
        return "%02d:%02d".format(d.hour, d.minute)
    }

    private fun daysUntil(utc: Long): Long = ChronoUnit.DAYS.between(Instant.now().atZone(ZoneId.systemDefault()).toLocalDate(), Instant.ofEpochMilli(utc).atZone(ZoneId.systemDefault()).toLocalDate())

    companion object { val instance = RaceWidget() }
}

class UpdateAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        RaceWidget.instance.update(context, glanceId)
    }
}