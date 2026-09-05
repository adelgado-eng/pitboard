package com.pitboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.data.TimeDisplayMode
import com.pitboard.app.i18n.tr
import com.pitboard.app.ui.components.OfflineBanner
import com.pitboard.app.ui.theme.BadgeColors
import com.pitboard.app.util.ColorContrast
import com.pitboard.app.util.DateTimeFormatters

/** Lista de eventos de Eventos (bloque "este fin de semana" + "más adelante") — extraída de
 *  EventsScreen.kt (Fase 4 del diagnóstico) sin cambiar ningún comportamiento. */
@Composable
fun EventsList(
    isOnline: Boolean,
    weekendEvents: List<EventEntity>,
    weekendLabelKey: String,
    laterEvents: List<EventEntity>,
    seriesConfigByKey: Map<RaceSeries, SeriesConfigEntity>,
    timeDisplayMode: TimeDisplayMode,
    onEventClick: (EventEntity) -> Unit
) {
    if (!isOnline) {
        OfflineBanner()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (weekendEvents.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = tr(weekendLabelKey).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            weekendEvents.forEachIndexed { index, event ->
                                EventRow(
                                    event = event,
                                    config = seriesConfigByKey[event.series],
                                    timeDisplayMode = timeDisplayMode,
                                    onClick = { onEventClick(event) }
                                )
                                if (index < weekendEvents.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (laterEvents.isNotEmpty()) {
            item {
                Text(
                    text = tr("events_later_section"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
                )
            }
            items(laterEvents, key = { "later_${it.id}" }) { event ->
                EventCard(
                    event = event,
                    config = seriesConfigByKey[event.series],
                    timeDisplayMode = timeDisplayMode,
                    onClick = { onEventClick(event) }
                )
            }
        }
    }
}

@Composable
fun EventRow(
    event: EventEntity,
    config: SeriesConfigEntity?,
    timeDisplayMode: TimeDisplayMode,
    onClick: () -> Unit
) {
    val tagColor = ColorContrast.ensureContrast(
        config?.colorHex?.let { ColorContrast.safeParseColor(it) } ?: BadgeColors.fallback,
        MaterialTheme.colorScheme.surface
    )

    // 03/09/2026 (2): sustituido el panel desplegable dentro de la propia fila por un popup
    // (ModalBottomSheet, ver EventDetailsSheet) — pedido explícito tras probar la primera
    // versión ("algo más estilo un pop up").
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(tagColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                config?.tag ?: event.series.defaultTag,
                color = ColorContrast.readableTextColor(tagColor),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                event.fullTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Text(
                DateTimeFormatters.formatEventDateTime(event.startTimeUtc, timeDisplayMode, event.timeZoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (event.inferredBadge.isNotEmpty()) {
            SessionBadgeChip(event.inferredBadge)
        }
    }
}

@Composable
fun EventCard(
    event: EventEntity,
    config: SeriesConfigEntity?,
    timeDisplayMode: TimeDisplayMode,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        EventRow(event, config, timeDisplayMode, onClick)
    }
}

@Composable
fun SessionBadgeChip(badge: String) {
    val bg = BadgeColors.forBadge(badge)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(badge, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}
