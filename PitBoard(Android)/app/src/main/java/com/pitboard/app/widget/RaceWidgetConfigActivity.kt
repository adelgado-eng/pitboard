package com.pitboard.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.AppTheme
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.ui.theme.PitBoardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RaceWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        // Samsung ID Recovery Logic
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val context = LocalContext.current
            val appSettingsRepository = remember { AppSettingsRepository(context) }
            val appTheme by appSettingsRepository.appTheme.collectAsState(initial = AppTheme.SYSTEM)

            PitBoardTheme(appTheme = appTheme) {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    ConfigErrorState("Error: No se recibió ID del widget.")
                } else {
                    RaceWidgetConfigScreen(
                        appWidgetId = appWidgetId,
                        onSaved = {
                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ConfigErrorState(msg: String) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { finish() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceWidgetConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var seriesConfigByKey by remember { mutableStateOf<Map<RaceSeries, SeriesConfigEntity>>(emptyMap()) }
    var selectedSeries by remember { mutableStateOf<Set<RaceSeries>>(emptySet()) }
    var eventCount by remember { mutableStateOf(WidgetPrefsRepository.DEFAULT_EVENT_COUNT) }
    var wordCount by remember { mutableStateOf(WidgetPrefsRepository.DEFAULT_WORD_COUNT) }
    var backgroundColorHex by remember { mutableStateOf(WidgetPrefsRepository.DEFAULT_BACKGROUND_COLOR_HEX) }
    var showTrackTime by remember { mutableStateOf(true) }
    var widgetAppearance by remember { mutableStateOf(WidgetPrefsRepository.DEFAULT_APPEARANCE) }
    var showColorPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(appWidgetId) {
        try {
            val db = AppDatabase.getInstance(context)
            seriesConfigByKey = db.seriesConfigDao().getAll().associateBy { it.series }

            val manager = GlanceAppWidgetManager(context)
            var glanceId: GlanceId? = null
            repeat(5) {
                glanceId = try { manager.getGlanceIdBy(appWidgetId) } catch (_: Exception) { null }
                if (glanceId != null) return@repeat
                delay(300)
            }

            if (glanceId != null) {
                val existing = WidgetPrefsRepository.load(context, glanceId!!)
                selectedSeries = WidgetPrefsRepository.effectiveSeries(existing)
                eventCount = existing.eventCount
                wordCount = existing.wordCount
                backgroundColorHex = existing.backgroundColorHex
                showTrackTime = existing.showTrackTime
                widgetAppearance = existing.appearance
            }
        } catch (e: Exception) {
            Log.e("RaceWidgetConfig", "Error loading config", e)
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val canSave = selectedSeries.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Configurar Widget", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

        Text("Series", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RaceSeries.entries.forEach { series ->
                val active = series in selectedSeries
                FilterChip(
                    selected = active,
                    onClick = {
                        selectedSeries = if (active) selectedSeries - series else selectedSeries + series
                    },
                    label = { Text(seriesConfigByKey[series]?.tag ?: series.defaultTag) }
                )
            }
        }

        Text("Eventos y Estilo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        EventCountRow(selected = eventCount, onSelect = { eventCount = it })

        Text("Palabras en título: $wordCount", style = MaterialTheme.typography.bodyMedium)
        Slider(value = wordCount.toFloat(), onValueChange = { wordCount = it.toInt() }, valueRange = 1f..8f, steps = 6)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Mostrar hora de la pista", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = showTrackTime, onCheckedChange = { showTrackTime = it })
        }

        Text("Apariencia del widget", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppTheme.LIGHT to "☀️ Claro",
                AppTheme.DARK to "🌙 Oscuro",
                AppTheme.SYSTEM to "📱 Auto"
            ).forEach { (theme, label) ->
                FilterChip(
                    selected = widgetAppearance == theme,
                    onClick = { widgetAppearance = theme },
                    label = { Text(label) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = backgroundColorHex,
                onValueChange = { backgroundColorHex = it },
                label = { Text("Color Fondo (#RRGGBB)") },
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.padding(start = 12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(safeParseColor(backgroundColorHex))
                        // 05/09/2026 (Fase 2, accesibilidad): el círculo de color no tenía
                        // ningún nombre accesible — TalkBack lo anunciaba como "Botón" a secas.
                        .semantics { contentDescription = "Elegir color de fondo del widget" }
                        .clickable { showColorPicker = true }
                )
            }
        }

        Button(
            onClick = {
                scope.launch {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceId = try { manager.getGlanceIdBy(appWidgetId) } catch (_: Exception) { null }

                    if (glanceId == null) {
                        Toast.makeText(context, "Error: Widget no reconocido.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    WidgetPrefsRepository.save(context, glanceId, WidgetConfig(
                        activeSeries = selectedSeries,
                        eventCount = eventCount,
                        wordCount = wordCount,
                        backgroundColorHex = backgroundColorHex,
                        showTrackTime = showTrackTime,
                        appearance = widgetAppearance
                    ))

                    // Force update immediately
                    RaceWidget.instance.update(context, glanceId)
                    RaceWidget.instance.updateAll(context)
                    onSaved()
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Guardar Configuración")
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialHex = backgroundColorHex,
            onDismiss = { showColorPicker = false },
            onColorPicked = { backgroundColorHex = it; showColorPicker = false }
        )
    }
}

/** internal (no private): reutilizado también por StandingsWidgetConfigActivity. */
@Composable
internal fun ColorPickerDialog(initialHex: String, onDismiss: () -> Unit, onColorPicked: (String) -> Unit) {
    val controller = rememberColorPickerController()
    var pickedHex by remember { mutableStateOf(initialHex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir color") },
        text = {
            HsvColorPicker(modifier = Modifier.fillMaxWidth().height(220.dp), controller = controller,
                initialColor = safeParseColor(initialHex), onColorChanged = { pickedHex = "#" + it.hexCode.takeLast(6) })
        },
        confirmButton = { Button(onClick = { onColorPicked(pickedHex) }) { Text("OK") } }
    )
}

@Composable
private fun EventCountRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf("5" to 5, "10" to 10, "20" to 20, "50" to 50, "Todos" to WidgetPrefsRepository.NO_LIMIT)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

internal fun safeParseColor(hex: String): Color = try {
    Color(hex.toColorInt())
} catch (_: Exception) {
    Color(0xFF5F6570)
}
