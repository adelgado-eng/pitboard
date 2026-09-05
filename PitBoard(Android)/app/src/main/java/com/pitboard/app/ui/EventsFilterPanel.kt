package com.pitboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.i18n.tr

/** Tipos de sesión que se pueden elegir en el filtro rápido — se deja fuera OTHER ("" — sin
 *  clasificar), que no es algo que nadie elija filtrar a propósito. */
private val SESSION_TYPE_FILTER_OPTIONS = listOf(
    SessionBadgeType.RACE,
    SessionBadgeType.QUALY,
    SessionBadgeType.SPRINT,
    SessionBadgeType.PRACTICE
)

/** Panel de filtro de Eventos (búsqueda + series + tipo de sesión) — vive detrás del botón
 *  de embudo de la barra superior, ver EventsScreen. Extraído de EventsScreen.kt (Fase 4 del
 *  diagnóstico) sin cambiar ningún comportamiento, solo para que ese archivo deje de
 *  concentrar navegación + filtros + lista + diálogos en un único sitio de 872 líneas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventsFilterPanel(
    visible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSeries: Set<RaceSeries>,
    onSeriesChange: (Set<RaceSeries>) -> Unit,
    seriesConfigByKey: Map<RaceSeries, SeriesConfigEntity>,
    selectedSessionTypes: Set<String>,
    onSessionTypesChange: (Set<String>) -> Unit
) {
    // Antes esto se ocultaba también con "&& !noEventsAtAll": si un filtro dejaba la lista a
    // cero eventos, el panel con los chips de serie —la única forma de QUITAR ese filtro sin
    // salir de la pantalla— desaparecía con la lista, dejando al botón de embudo sin ningún
    // efecto visible. El panel ahora se ve siempre que se pida, haya o no eventos que mostrar
    // debajo.
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("events_search_placeholder")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = tr("events_clear_search"))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSeries.isEmpty(),
                    onClick = { onSeriesChange(emptySet()) },
                    label = { Text(tr("events_filter_all_series")) }
                )
                RaceSeries.entries.forEach { series ->
                    val active = series in selectedSeries
                    FilterChip(
                        selected = active,
                        onClick = {
                            onSeriesChange(if (active) selectedSeries - series else selectedSeries + series)
                        },
                        label = { Text(seriesConfigByKey[series]?.tag ?: series.defaultTag) }
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSessionTypes.isEmpty(),
                    onClick = { onSessionTypesChange(emptySet()) },
                    label = { Text(tr("events_filter_all_sessions")) }
                )
                SESSION_TYPE_FILTER_OPTIONS.forEach { badge ->
                    val active = badge in selectedSessionTypes
                    FilterChip(
                        selected = active,
                        onClick = {
                            onSessionTypesChange(if (active) selectedSessionTypes - badge else selectedSessionTypes + badge)
                        },
                        label = { Text(tr(SessionBadgeType.labelKey(badge))) }
                    )
                }
            }
        }
    }
}
