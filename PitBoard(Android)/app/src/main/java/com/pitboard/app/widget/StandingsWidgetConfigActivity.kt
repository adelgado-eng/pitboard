package com.pitboard.app.widget

import android.appwidget.AppWidgetManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.AppTheme
import com.pitboard.app.standings.CarBasedStandingsClasses
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.ui.theme.PitBoardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Configuración por instancia del widget de Clasificación — equivalente de
 *  RaceWidgetConfigActivity.kt, pero eligiendo categoría + Pilotos/Equipos (o clase de coche
 *  en categorías de resistencia) en vez de series de calendario. */
class StandingsWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

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
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error: No se recibió ID del widget.", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { finish() }, modifier = Modifier.padding(top = 16.dp)) { Text("Cerrar") }
                        }
                    }
                } else {
                    StandingsWidgetConfigScreen(
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandingsWidgetConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf(StandingsWidgetPrefsRepository.DEFAULT_CATEGORY) }
    var mode by remember { mutableStateOf(StandingsWidgetPrefsRepository.DEFAULT_MODE) }
    var carClass by remember { mutableStateOf<StandingsClass?>(null) }
    var rowCount by remember { mutableStateOf(StandingsWidgetPrefsRepository.DEFAULT_ROW_COUNT) }
    var backgroundColorHex by remember { mutableStateOf(StandingsWidgetPrefsRepository.DEFAULT_BACKGROUND_COLOR_HEX) }
    var widgetAppearance by remember { mutableStateOf(StandingsWidgetPrefsRepository.DEFAULT_APPEARANCE) }
    var showColorPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val carClasses = CarBasedStandingsClasses.CAR_BASED_CLASSES[category]
    val isCarBased = carClasses != null

    LaunchedEffect(appWidgetId) {
        try {
            val manager = GlanceAppWidgetManager(context)
            var glanceId: GlanceId? = null
            repeat(5) {
                glanceId = try { manager.getGlanceIdBy(appWidgetId) } catch (_: Exception) { null }
                if (glanceId != null) return@repeat
                delay(300)
            }

            if (glanceId != null) {
                val existing = StandingsWidgetPrefsRepository.load(context, glanceId!!)
                category = existing.category
                mode = existing.mode
                carClass = existing.carClass
                rowCount = existing.rowCount
                backgroundColorHex = existing.backgroundColorHex
                widgetAppearance = existing.appearance
            }
        } catch (e: Exception) {
            Log.e("StandingsWidgetConfig", "Error loading config", e)
        }
        loading = false
    }

    // Al cambiar a una categoría "por coche" (o cambiar de una a otra), la clase seleccionada
    // se ajusta a la primera de esa categoría si no había ninguna válida ya elegida.
    LaunchedEffect(category) {
        val classes = CarBasedStandingsClasses.CAR_BASED_CLASSES[category]
        if (classes != null && carClass !in classes.map { it.first }) {
            carClass = classes.first().first
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Configurar widget de clasificación", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

        Text("Categoría", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StandingsCategory.entries.forEach { entry ->
                CategoryRow(entry = entry, selected = entry == category, onClick = { category = entry })
            }
        }

        if (isCarBased) {
            Text("Clase", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                carClasses.orEmpty().forEach { (cls, label) ->
                    FilterChip(selected = carClass == cls, onClick = { carClass = cls }, label = { Text(label) })
                }
            }
        } else {
            Text("Pilotos o equipos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == StandingType.DRIVER, onClick = { mode = StandingType.DRIVER }, label = { Text("Pilotos") })
                FilterChip(selected = mode == StandingType.TEAM, onClick = { mode = StandingType.TEAM }, label = { Text("Equipos") })
            }
        }

        Text("Filas a mostrar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        RowCountRow(selected = rowCount, onSelect = { rowCount = it })

        Text("Apariencia del widget", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppTheme.LIGHT to "☀️ Claro",
                AppTheme.DARK to "🌙 Oscuro",
                AppTheme.SYSTEM to "📱 Auto"
            ).forEach { (theme, label) ->
                FilterChip(selected = widgetAppearance == theme, onClick = { widgetAppearance = theme }, label = { Text(label) })
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
                        // 05/09/2026 (Fase 2, accesibilidad): mismo fix que en
                        // RaceWidgetConfigActivity — el círculo de color no tenía nombre
                        // accesible.
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

                    StandingsWidgetPrefsRepository.save(
                        context,
                        glanceId,
                        StandingsWidgetConfig(
                            category = category,
                            mode = mode,
                            carClass = if (isCarBased) carClass else null,
                            rowCount = rowCount,
                            backgroundColorHex = backgroundColorHex,
                            appearance = widgetAppearance
                        )
                    )

                    StandingsWidget.instance.update(context, glanceId)
                    StandingsWidget.instance.updateAll(context)
                    onSaved()
                }
            },
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

@Composable
private fun CategoryRow(entry: StandingsCategory, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Text(
            entry.displayName,
            modifier = Modifier.padding(12.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RowCountRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf("3" to 3, "5" to 5, "10" to 10, "Todos" to StandingsWidgetPrefsRepository.NO_LIMIT)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
