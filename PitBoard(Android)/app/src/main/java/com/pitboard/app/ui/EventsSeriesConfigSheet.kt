package com.pitboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.i18n.tr
import com.pitboard.app.util.ColorContrast

/** Botón lápiz de la barra superior de Eventos: en vez de filtrar eventos, aquí se configura
 *  el tag corto (iniciales, ej. "NCU") y el color de cada una de las 15 series — ya no hay
 *  calendarios que agrupen esto, así que es una lista plana. Extraído de EventsScreen.kt
 *  (Fase 4 del diagnóstico) sin cambiar ningún comportamiento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesConfigSheet(
    seriesConfigByKey: Map<RaceSeries, SeriesConfigEntity>,
    onDismiss: () -> Unit,
    onSave: (SeriesConfigEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<SeriesConfigEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("events_edit_series"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("events_close"))
                    }
                }
                Text(
                    tr("events_edit_series_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(RaceSeries.entries, key = { it.name }) { series ->
                        val config = seriesConfigByKey[series]
                            ?: SeriesConfigEntity(series, series.defaultTag, series.defaultColorHex)
                        SeriesConfigRow(config = config, onClick = { editing = config })
                    }
                }
            }
        }
    }

    editing?.let { config ->
        EditSeriesConfigDialog(
            config = config,
            onDismiss = { editing = null },
            onSave = { updated ->
                onSave(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun SeriesConfigRow(config: SeriesConfigEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorSwatch(hex = config.colorHex)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(config.series.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("events_series_tag_prefix").format(config.tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                tr("events_edit"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditSeriesConfigDialog(
    config: SeriesConfigEntity,
    onDismiss: () -> Unit,
    onSave: (SeriesConfigEntity) -> Unit
) {
    var tag by remember { mutableStateOf(config.tag) }
    var colorHex by remember { mutableStateOf(config.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.series.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.uppercase().take(5) },
                    label = { Text(tr("events_tag_label")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it },
                    label = { Text(tr("events_color_label")) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("events_preview_label"), style = MaterialTheme.typography.bodySmall)
                    ColorSwatch(hex = colorHex)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(config.copy(tag = tag.ifBlank { config.tag }, colorHex = colorHex))
            }) { Text(tr("events_save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("events_cancel")) }
        }
    )
}

@Composable
private fun ColorSwatch(hex: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ColorContrast.safeParseColor(hex))
    )
}
