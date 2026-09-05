package com.pitboard.app.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.data.TimeDisplayMode
import com.pitboard.app.i18n.tr
import com.pitboard.app.schedule.RaceScheduleRepository
import com.pitboard.app.standings.ConnectivityHelper
import com.pitboard.app.ui.components.EmptyState
import com.pitboard.app.util.EventWeekendGrouper
import com.pitboard.app.util.EventWeekendGroups
import com.pitboard.app.util.SeasonWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val settings = AppSettingsRepository(application)
    private val scheduleRepository = RaceScheduleRepository(database.eventDao())

    /** true mientras las 15 fuentes están en vuelo — igual que StandingsViewModel.syncing,
     *  para que el botón de la barra cambie a un indicador de progreso. */
    var syncing by mutableStateOf(false)
        private set

    // "Ahora" como flujo en vez de un valor fijo: si fuera un `val` calculado una sola vez
    // en el constructor, un evento que ya ha pasado seguiría apareciendo en la lista hasta
    // que se reiniciara la app (Room solo re-emite el Flow cuando cambian los DATOS, no
    // porque haya pasado el tiempo). Al meterlo en el combine, pulsar "Actualizar" fuerza
    // una nueva consulta con la hora actual y los eventos pasados desaparecen al momento.
    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    val selectedSeries: StateFlow<Set<RaceSeries>> = settings.eventScreenActiveSeries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Qué hora se ve de un vistazo en las filas de la lista — ver TimeDisplayMode. */
    val timeDisplayMode: StateFlow<TimeDisplayMode> = settings.timeDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeDisplayMode.DEVICE)

    /** Tipos de sesión activos (Carrera/Clasificación/Sprint/Libres, ver SessionBadgeType) —
     *  a diferencia de selectedSeries no entra en la consulta a Room: con el número de
     *  eventos que maneja la app (una temporada, no un histórico) filtrar en memoria sobre la
     *  lista ya cargada es tan barato como añadir otra condición SQL, y evita tocar EventDao
     *  otra vez. */
    val selectedSessionTypes: StateFlow<Set<String>> = settings.eventScreenActiveSessionTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val seriesConfigByKey: StateFlow<Map<RaceSeries, SeriesConfigEntity>> =
        database.seriesConfigDao().observeAll()
            .map { list -> list.associateBy { it.series } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val eventGroups: StateFlow<EventWeekendGroups?> =
        combine(selectedSeries, refreshTrigger) { series, nowUtc -> series to nowUtc }
            .flatMapLatest { (series, nowUtc) ->
                val endOfYearUtc = SeasonWindow.endOfCurrentYearUtc(nowUtc)
                val flow = if (series.isEmpty()) {
                    database.eventDao().observeAllUpcoming(nowUtc, endOfYearUtc)
                } else {
                    database.eventDao().observeUpcomingBySeries(series.toList(), nowUtc, endOfYearUtc)
                }
                flow.map { EventWeekendGrouper.split(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateActiveSeries(series: Set<RaceSeries>) {
        viewModelScope.launch { settings.setEventScreenActiveSeries(series) }
    }

    fun updateActiveSessionTypes(sessionTypes: Set<String>) {
        viewModelScope.launch { settings.setEventScreenActiveSessionTypes(sessionTypes) }
    }

    fun updateSeriesConfig(config: SeriesConfigEntity) {
        viewModelScope.launch { database.seriesConfigDao().update(config) }
    }

    /** Sincronización manual bajo demanda: llama a las 15 fuentes DIRECTAMENTE (no vía
     *  RaceScheduleScheduler/WorkManager) para poder esperar el resultado y enseñar un
     *  indicador de progreso — igual que StandingsViewModel.refreshNow() y por el mismo
     *  motivo: el usuario está mirando la pantalla, así que no aporta nada depender del
     *  scheduler del sistema. El ciclo diario en segundo plano sigue usando el scheduler.
     *
     *  Antes esto solo reprogramaba una sync en segundo plano sin esperarla: si la fuente de
     *  una serie fallaba (ver WikipediaSeasonCalendarSource — el bug real de Porsche Supercup
     *  del 03/09/2026 hacía que la fuente devolviera siempre 0 eventos), el botón no daba
     *  ninguna pista de que algo había ido mal ni de cuándo había terminado. */
    fun refreshNow() {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            try {
                scheduleRepository.syncAll()
            } finally {
                syncing = false
                refreshTrigger.value = System.currentTimeMillis()
            }
        }
    }
}

/** Pantalla de Eventos — orquesta el panel de filtro ([EventsFilterPanel]), la lista
 *  ([EventsList]) y los dos diálogos ([SeriesConfigSheet]/[EventDetailsSheet]), cada uno ya
 *  en su propio archivo.
 *
 *  05/09/2026 (Fase 4 del diagnóstico): este archivo concentraba navegación + filtros +
 *  lista + diálogos en un único sitio de 872 líneas — coincidía con la comunidad de menor
 *  cohesión de todo el grafo de graphify (0,05 sobre 40 nodos). Se hace DESPUÉS de la Fase 1
 *  (red de tests de los parsers) a propósito: es el refactor de más riesgo del plan, así que
 *  se apoya en que Android e iOS ya compilan y pasan sus tests reales antes de tocarlo. Pura
 *  extracción de código verbatim a otros archivos — ningún comportamiento cambia, cada
 *  composable extraído recibe exactamente los mismos parámetros con los que ya se llamaba
 *  aquí mismo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(viewModel: EventsViewModel = viewModel()) {
    val groups by viewModel.eventGroups.collectAsState()
    val seriesConfigByKey by viewModel.seriesConfigByKey.collectAsState()
    val selectedSeries by viewModel.selectedSeries.collectAsState()
    val selectedSessionTypes by viewModel.selectedSessionTypes.collectAsState()
    val timeDisplayMode by viewModel.timeDisplayMode.collectAsState()

    // Igual que en Clasificaciones: se comprueba una vez al entrar, no en vivo — si la
    // conexión cambia mientras la pantalla está abierta, se refleja en el siguiente "Actualizar"
    // o la próxima vez que se abra la pantalla, sin necesidad de un listener permanente.
    val context = LocalContext.current
    val isOnline = remember { ConnectivityHelper.isOnline(context) }

    var showSeriesEditor by remember { mutableStateOf(false) }
    // Evento tocado, para el popup de detalle (ver EventDetailsSheet) — null = cerrado.
    var detailsEvent by remember { mutableStateOf<EventEntity?>(null) }

    // Panel de filtro: palabra clave (transitoria, no se guarda) + series activas
    // (persistente, ver AppSettingsRepository.eventScreenActiveSeries). Vive detrás del botón
    // de embudo — no ocupa sitio hasta que se despliega.
    var showFilterPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val quickFiltersActive = searchQuery.isNotBlank() || selectedSeries.isNotEmpty() || selectedSessionTypes.isNotEmpty()

    // tr() es @Composable (lee el idioma activo de LocalAppLanguage) y no se puede llamar
    // dentro del onClick de más abajo (contexto normal, no @Composable) — se resuelve aquí,
    // en contexto de composición, y el lambda solo lee esta variable ya capturada.
    val offlineToastMessage = tr("events_offline_toast")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("events_title"), fontWeight = FontWeight.Bold) },
                actions = {
                    if (viewModel.syncing) {
                        // Mismo hueco que el botón, para que la barra no dé un salto al
                        // cambiar uno por otro (igual que en Clasificaciones).
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (ConnectivityHelper.isOnline(context)) {
                                    viewModel.refreshNow()
                                } else {
                                    Toast.makeText(context, offlineToastMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = tr("events_refresh"))
                        }
                    }
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        Icon(
                            Icons.Default.FilterAlt,
                            contentDescription = tr("events_search_and_filter"),
                            tint = if (quickFiltersActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    }
                    IconButton(onClick = { showSeriesEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = tr("events_edit_series"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val allWeekendEvents = groups?.weekendEvents.orEmpty()
            val allLaterEvents = groups?.laterEvents.orEmpty()
            val noEventsAtAll = allWeekendEvents.isEmpty() && allLaterEvents.isEmpty()

            fun matchesSearch(event: EventEntity): Boolean {
                val query = searchQuery.trim()
                val matchesQuery = query.isEmpty() ||
                    event.fullTitle.contains(query, ignoreCase = true) ||
                    event.series.displayName.contains(query, ignoreCase = true)
                val matchesSessionType = selectedSessionTypes.isEmpty() || event.inferredBadge in selectedSessionTypes
                return matchesQuery && matchesSessionType
            }

            val weekendEvents = allWeekendEvents.filter(::matchesSearch)
            val laterEvents = allLaterEvents.filter(::matchesSearch)
            val nothingMatchesQuickFilters = !noEventsAtAll && weekendEvents.isEmpty() && laterEvents.isEmpty()

            EventsFilterPanel(
                visible = showFilterPanel,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedSeries = selectedSeries,
                onSeriesChange = viewModel::updateActiveSeries,
                seriesConfigByKey = seriesConfigByKey,
                selectedSessionTypes = selectedSessionTypes,
                onSessionTypesChange = viewModel::updateActiveSessionTypes
            )

            if (noEventsAtAll && selectedSeries.isNotEmpty()) {
                // Va ANTES que el aviso de "sin conexión": con un filtro de por medio, "no hay
                // eventos guardados para estas series" es el diagnóstico correcto tanto si hay
                // internet como si no — decir "necesitas conexión" aquí sería engañoso, porque
                // sí puede haber eventos guardados de OTRAS series (ver el bug real de Porsche
                // Supercup, 03/09/2026: sin un botón para quitar el filtro aquí mismo, la
                // única salida era abrir el panel de embudo a mano).
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = tr("events_empty_filtered_title"),
                    message = tr("events_empty_filtered_message"),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.updateActiveSeries(emptySet()) }) {
                                Text(tr("events_remove_filter"))
                            }
                            Button(onClick = { viewModel.refreshNow() }) {
                                Text(tr("events_refresh"))
                            }
                        }
                    }
                )
            } else if (noEventsAtAll && !isOnline) {
                EmptyState(
                    icon = Icons.Default.WifiOff,
                    title = tr("events_empty_offline_title"),
                    message = tr("events_empty_offline_message")
                )
            } else if (noEventsAtAll) {
                EmptyState(
                    icon = Icons.Default.Event,
                    title = tr("events_empty_title"),
                    message = tr("events_empty_message"),
                    action = {
                        Button(onClick = { viewModel.refreshNow() }) {
                            Text(tr("events_refresh"))
                        }
                    }
                )
            } else if (nothingMatchesQuickFilters) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = tr("events_empty_no_match_title"),
                    message = tr("events_empty_no_match_message"),
                    action = if (quickFiltersActive) {
                        {
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.updateActiveSeries(emptySet())
                                    viewModel.updateActiveSessionTypes(emptySet())
                                }
                            ) {
                                Text(tr("events_clear_search_and_filters"))
                            }
                        }
                    } else null
                )
            } else {
                EventsList(
                    isOnline = isOnline,
                    weekendEvents = weekendEvents,
                    weekendLabelKey = groups?.weekendLabelKey.orEmpty(),
                    laterEvents = laterEvents,
                    seriesConfigByKey = seriesConfigByKey,
                    timeDisplayMode = timeDisplayMode,
                    onEventClick = { detailsEvent = it }
                )
            }
        }
    }

    if (showSeriesEditor) {
        SeriesConfigSheet(
            seriesConfigByKey = seriesConfigByKey,
            onDismiss = { showSeriesEditor = false },
            onSave = { updated -> viewModel.updateSeriesConfig(updated) }
        )
    }

    detailsEvent?.let { event ->
        EventDetailsSheet(event = event, onDismiss = { detailsEvent = null })
    }
}
