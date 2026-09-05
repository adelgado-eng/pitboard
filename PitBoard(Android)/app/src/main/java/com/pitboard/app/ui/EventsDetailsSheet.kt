package com.pitboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.i18n.tr
import com.pitboard.app.util.DateTimeFormatters
import com.pitboard.app.weather.WeatherRepository
import com.pitboard.app.weather.WeatherResult

/** Popup con el detalle de un evento, al tocarlo — solo campos que YA trae [EventEntity], sin
 *  ninguna petición de red adicional (pedido explícito). "Hora local del circuito" solo se
 *  enseña cuando la fuente trajo [EventEntity.timeZoneId] (ver las fuentes de
 *  com.pitboard.app.schedule.sources que sí lo rellenan, ej. EspnNascarScheduleSource/
 *  ImsaScheduleSource) — si no, se omite en vez de enseñar una hora inventada. Mismo patrón
 *  de ModalBottomSheet que CarDriversSheet/SeriesConfigSheet. Extraído de EventsScreen.kt
 *  (Fase 4 del diagnóstico) sin cambiar ningún comportamiento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsSheet(event: EventEntity, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Clima del circuito bajo demanda: solo se pide al abrir ESTE evento (nunca para toda la
    // lista de golpe), y solo si Open-Meteo puede tener algo que decir (circuito reconocido +
    // dentro de los ~15 días de previsión) — ver WeatherRepository.
    var weather by remember(event.id) { mutableStateOf<WeatherResult?>(null) }
    LaunchedEffect(event.id) {
        weather = WeatherRepository.fetch(event.fullTitle, event.startTimeUtc, System.currentTimeMillis())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.series.displayName.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (event.inferredBadge.isNotEmpty()) {
                    SessionBadgeChip(event.inferredBadge)
                }
            }
            Text(
                event.fullTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailLine(tr("events_detail_your_time"), DateTimeFormatters.formatEventDateTimeLong(event.startTimeUtc))
                event.timeZoneId?.let { zoneId ->
                    DateTimeFormatters.formatEventDateTimeInZone(event.startTimeUtc, zoneId)?.let { local ->
                        DetailLine(tr("events_detail_track_time").format(zoneId), local)
                    }
                }
                DetailLine(tr("events_detail_series"), event.series.displayName)
                if (event.inferredBadge.isNotEmpty()) {
                    DetailLine(tr("events_detail_session_type"), tr(SessionBadgeType.labelKey(event.inferredBadge)))
                }
                // Sin fila cuando el circuito no se reconoce o está demasiado lejos en el
                // futuro — no aporta nada un "Clima: —" para el 90% de los eventos de la
                // temporada que todavía no tienen previsión.
                when (val w = weather) {
                    is WeatherResult.Available -> DetailLine(
                        tr("events_detail_weather"),
                        tr("events_detail_weather_value").format(w.tempCelsius.toInt(), w.rainProbabilityPercent)
                    )
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
