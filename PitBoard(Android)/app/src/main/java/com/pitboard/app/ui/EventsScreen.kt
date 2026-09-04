package com.pitboard.app.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.data.TimeDisplayMode
import com.pitboard.app.i18n.tr
import com.pitboard.app.schedule.RaceScheduleRepository
import com.pitboard.app.standings.ConnectivityHelper
import com.pitboard.app.ui.theme.BadgeColors
import com.pitboard.app.util.ColorContrast
import com.pitboard.app.util.DateTimeFormatters
import com.pitboard.app.ui.components.EmptyState
import com.pitboard.app.ui.components.OfflineBanner
import com.pitboard.app.util.EventWeekendGrouper
import com.pitboard.app.util.EventWeekendGroups
import com.pitboard.app.util.SeasonWindow
import com.pitboard.app.weather.WeatherRepository
import com.pitboard.app.weather.WeatherResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tipos de sesión que se pueden elegir en el filtro rápido — se deja fuera OTHER ("" — sin
 *  clasificar), que no es algo que nadie elija filtrar a propósito. */
private val SESSION_TYPE_FILTER_OPTIONS = listOf(
    SessionBadgeType.RACE,
    SessionBadgeType.QUALY,
    SessionBadgeType.SPRINT,
    SessionBadgeType.PRACTICE
)

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

            // Antes esto se ocultaba también con "&& !noEventsAtAll": si un filtro dejaba la
            // lista a cero eventos, el panel con los chips de serie —la única forma de QUITAR
            // ese filtro sin salir de la pantalla— desaparecía con la lista, dejando al botón
            // de embudo sin ningún efecto visible. El panel ahora se ve siempre que se pida,
            // haya o no eventos que mostrar debajo.
            AnimatedVisibility(
                visible = showFilterPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("events_search_placeholder")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
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
                            onClick = { viewModel.updateActiveSeries(emptySet()) },
                            label = { Text(tr("events_filter_all_series")) }
                        )
                        RaceSeries.entries.forEach { series ->
                            val active = series in selectedSeries
                            FilterChip(
                                selected = active,
                                onClick = {
                                    viewModel.updateActiveSeries(
                                        if (active) selectedSeries - series else selectedSeries + series
                                    )
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
                            onClick = { viewModel.updateActiveSessionTypes(emptySet()) },
                            label = { Text(tr("events_filter_all_sessions")) }
                        )
                        SESSION_TYPE_FILTER_OPTIONS.forEach { badge ->
                            val active = badge in selectedSessionTypes
                            FilterChip(
                                selected = active,
                                onClick = {
                                    viewModel.updateActiveSessionTypes(
                                        if (active) selectedSessionTypes - badge else selectedSessionTypes + badge
                                    )
                                },
                                label = { Text(tr(SessionBadgeType.labelKey(badge))) }
                            )
                        }
                    }
                }
            }

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
                                    text = tr(groups?.weekendLabelKey.orEmpty()).uppercase(),
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
                                                onClick = { detailsEvent = event }
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
                                onClick = { detailsEvent = event }
                            )
                        }
                    }
                }
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

/** Botón lápiz de la barra superior: en vez de filtrar eventos, aquí se configura el tag
 *  corto (iniciales, ej. "NCU") y el color de cada una de las 15 series — ya no hay
 *  calendarios que agrupen esto, así que es una lista plana. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesConfigSheet(
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

@Composable
private fun EventRow(
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

/** Popup con el detalle de un evento, al tocarlo — solo campos que YA trae [EventEntity], sin
 *  ninguna petición de red adicional (pedido explícito). "Hora local del circuito" solo se
 *  enseña cuando la fuente trajo [EventEntity.timeZoneId] (ver las fuentes de
 *  com.pitboard.app.schedule.sources que sí lo rellenan, ej. EspnNascarScheduleSource/
 *  ImsaScheduleSource) — si no, se omite en vez de enseñar una hora inventada. Mismo patrón
 *  de ModalBottomSheet que CarDriversSheet/SeriesConfigSheet en este mismo archivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailsSheet(event: EventEntity, onDismiss: () -> Unit) {
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

@Composable
private fun EventCard(
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
private fun SessionBadgeChip(badge: String) {
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
