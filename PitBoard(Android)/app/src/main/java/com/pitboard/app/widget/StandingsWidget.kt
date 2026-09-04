package com.pitboard.app.widget

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.AppTheme
import com.pitboard.app.i18n.AppLanguage
import com.pitboard.app.i18n.LocalAppLanguage
import com.pitboard.app.i18n.tr
import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.ui.formatPoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Widget de Clasificación — equivalente de RaceWidget.kt pero para Clasificaciones en vez de
 * Eventos. Un único tipo de widget, configurable POR INSTANCIA (categoría + Pilotos/Equipos,
 * o clase de coche en WEC/ELMS/IMSA/Le Mans Cup — ver StandingsWidgetConfigActivity): varias
 * copias del mismo widget, cada una apuntando a una categoría distinta, en vez de un tipo de
 * widget por cada una de las 15 categorías.
 *
 * Sin fotos de piloto/logo de equipo a propósito (04/09/2026, mismo criterio que el widget de
 * Eventos, que tampoco carga imágenes remotas): un widget no puede cargar una imagen de forma
 * perezosa como la app — hay que descargar los bytes de antemano en cada actualización, lo que
 * añade lentitud y riesgo de fallo justo cuando el sistema pide refrescar, y en widgets el
 * presupuesto de memoria es estrecho. Solo texto: posición, nombre, equipo y puntos.
 */
private val FALLBACK_COLOR = Color(0xFF5F6570)
private const val RELOAD_ATTEMPTS = 3

private data class StandingsWidgetPalette(val chalk: Color, val chalkDim: Color, val cardBg: Color, val gold: Color, val silver: Color, val bronze: Color)

private val DarkStandingsPalette = StandingsWidgetPalette(
    chalk = Color(0xFFEEF0F2),
    chalkDim = Color(0xFF9AA0AB),
    cardBg = Color(0xFF1C1F26),
    gold = Color(0xFFFFC933),
    silver = Color(0xFFC8CEDA),
    bronze = Color(0xFFD98E4F)
)

private val LightStandingsPalette = StandingsWidgetPalette(
    chalk = Color(0xFF181815),
    chalkDim = Color(0xFF5F6570),
    cardBg = Color(0xFFFFFFFF),
    gold = Color(0xFF8A6A00),
    silver = Color(0xFF60666F),
    bronze = Color(0xFF8A4A17)
)

private val LocalStandingsPalette = staticCompositionLocalOf { DarkStandingsPalette }

// Mismos 3 tamaños fijos que RaceWidget (ver el comentario allí sobre SizeMode.Responsive
// vs Exact) — el propio widget ya es redimensionable de extremo a extremo (ver
// standings_widget_info.xml), solo hace falta generar estas 3 variantes de diseño.
private val WIDGET_SIZES = setOf(
    DpSize(110.dp, 40.dp),
    DpSize(140.dp, 110.dp),
    DpSize(250.dp, 320.dp)
)

class StandingsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val contextLocal = LocalContext.current

            val state by produceState<WidgetReadyState?>(initialValue = null) {
                repeat(RELOAD_ATTEMPTS) { attempt ->
                    try {
                        val db = AppDatabase.getInstance(contextLocal)
                        val config = StandingsWidgetPrefsRepository.load(contextLocal, id)
                        val appLanguage = AppSettingsRepository(contextLocal).appLanguageNow() ?: AppLanguage.SPANISH

                        val effectiveClass = config.carClass ?: StandingsClass.OVERALL
                        val effectiveType = if (config.carClass != null) StandingType.TEAM else config.mode
                        val rows = db.standingDao().getStandings(
                            config.category,
                            effectiveClass,
                            effectiveType,
                            StandingsWidgetPrefsRepository.queryLimitFor(config.rowCount)
                        )

                        val useDark = when (config.appearance) {
                            AppTheme.LIGHT -> false
                            AppTheme.DARK -> true
                            AppTheme.SYSTEM -> {
                                val nightMode = contextLocal.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                                nightMode == Configuration.UI_MODE_NIGHT_YES
                            }
                        }

                        value = WidgetReadyState(config, rows, useDark, appLanguage)
                        return@produceState
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("StandingsWidget", "Load error (intento ${attempt + 1}/$RELOAD_ATTEMPTS)", e)
                        if (attempt < RELOAD_ATTEMPTS - 1) delay(500.milliseconds)
                    }
                }
            }

            GlanceTheme {
                state?.let {
                    CompositionLocalProvider(LocalAppLanguage provides it.appLanguage) {
                        WidgetUI(it)
                    }
                } ?: Box(
                    GlanceModifier.fillMaxSize()
                        .background(DarkStandingsPalette.cardBg)
                        .cornerRadius(24.dp)
                        .clickable(actionRunCallback<UpdateStandingsAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆", style = TextStyle(fontSize = 24.sp))
                }
            }
        }
    }

    private data class WidgetReadyState(
        val config: StandingsWidgetConfig,
        val rows: List<StandingEntity>,
        val useDark: Boolean,
        val appLanguage: AppLanguage
    )

    @Composable
    private fun WidgetUI(state: WidgetReadyState) {
        val palette = if (state.useDark) DarkStandingsPalette else LightStandingsPalette
        CompositionLocalProvider(LocalStandingsPalette provides palette) {
            val size = LocalSize.current
            val isList = size.height >= 80.dp && size.width >= 180.dp
            val bgColor = try { Color(state.config.backgroundColorHex.toColorInt()).copy(alpha = 0.72f) } catch (_: Exception) { palette.cardBg }

            Column(GlanceModifier.fillMaxSize().background(bgColor).cornerRadius(24.dp).padding(10.dp).clickable(actionRunCallback<UpdateStandingsAction>())) {
                if (isList) {
                    Header(state.config.category)
                    Box(GlanceModifier.height(8.dp)) {}
                }

                if (state.rows.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tr("standings_no_data_yet"), style = TextStyle(color = ColorProvider(palette.chalkDim)))
                    }
                } else {
                    when {
                        size.height < 80.dp -> MiniRow(state.rows.first())
                        size.width < 180.dp -> HeroRow(state.config.category, state.rows.first())
                        else -> RowList(state.rows)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(category: StandingsCategory) {
        val palette = LocalStandingsPalette.current
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🏆 ${category.displayName}",
                maxLines = 1,
                style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium, fontSize = 12.sp)
            )
        }
    }

    @Composable
    private fun RowList(rows: List<StandingEntity>) {
        val palette = LocalStandingsPalette.current
        LazyColumn(GlanceModifier.fillMaxSize().background(palette.cardBg).cornerRadius(20.dp)) {
            itemsIndexed(rows) { index, row ->
                StandingRow(row)
                if (index < rows.lastIndex) {
                    Box(GlanceModifier.fillMaxWidth().height(0.5.dp).background(palette.chalkDim.copy(alpha = 0.2f)).padding(horizontal = 16.dp)) {}
                }
            }
        }
    }

    @Composable
    private fun StandingRow(row: StandingEntity) {
        val palette = LocalStandingsPalette.current
        val positionColor = podiumColor(row.position, palette)
        Row(
            GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${row.position}",
                style = TextStyle(color = ColorProvider(positionColor ?: palette.chalkDim), fontWeight = FontWeight.Bold, fontSize = 13.sp),
                modifier = GlanceModifier.width(24.dp)
            )
            Column(GlanceModifier.defaultWeight().padding(horizontal = 8.dp)) {
                Text(row.name, maxLines = 1, style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium, fontSize = 12.sp))
                if (row.team.isNotBlank()) {
                    Text(row.team, maxLines = 1, style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 10.sp))
                }
            }
            Text(
                tr("standings_points").format(formatPoints(row.points)),
                style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            )
        }
    }

    @Composable
    private fun HeroRow(category: StandingsCategory, leader: StandingEntity) {
        val palette = LocalStandingsPalette.current
        Column(
            GlanceModifier.fillMaxSize().background(palette.cardBg).cornerRadius(16.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏆", style = TextStyle(fontSize = 20.sp))
            Box(GlanceModifier.height(6.dp)) {}
            Text(category.displayName, maxLines = 1, style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 10.sp))
            Box(GlanceModifier.height(4.dp)) {}
            Text(leader.name, maxLines = 1, style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Bold, fontSize = 13.sp))
            Text(
                tr("standings_points").format(formatPoints(leader.points)),
                style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun MiniRow(leader: StandingEntity) {
        val palette = LocalStandingsPalette.current
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = TextStyle(fontSize = 16.sp))
            Box(GlanceModifier.width(8.dp)) {}
            Column(GlanceModifier.defaultWeight()) {
                Text(leader.name, maxLines = 1, style = TextStyle(color = ColorProvider(palette.chalk), fontWeight = FontWeight.Medium, fontSize = 12.sp))
            }
            Text(
                tr("standings_points").format(formatPoints(leader.points)),
                style = TextStyle(color = ColorProvider(palette.chalkDim), fontSize = 11.sp)
            )
        }
    }

    private fun podiumColor(position: Int, palette: StandingsWidgetPalette): Color? = when (position) {
        1 -> palette.gold
        2 -> palette.silver
        3 -> palette.bronze
        else -> null
    }

    companion object { val instance = StandingsWidget() }
}

class UpdateStandingsAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        StandingsWidget.instance.update(context, glanceId)
    }
}
