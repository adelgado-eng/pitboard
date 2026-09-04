package com.pitboard.app.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsRepository
import com.pitboard.app.i18n.tr
import com.pitboard.app.standings.ConnectivityHelper
import com.pitboard.app.ui.components.EmptyState
import com.pitboard.app.ui.components.OfflineBanner
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StandingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val standingsRepository = StandingsRepository(database.standingDao(), database.carDriverDao())

    val standingsEnabled: StateFlow<Boolean> = settingsRepository.standingsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** El piloto (o, en las categorías "por coche" — WEC/Le Mans Cup/ELMS/IMSA — el
     *  EQUIPO) en cabeza de cada categoría, para la vista previa del menú — o null si esa
     *  categoría todavía no tiene ninguna sincronización guardada.
     *
     *  04/09/2026: ELMS/IMSA/WEC/LEMANS_CUP nunca guardan filas OVERALL/DRIVER (esas 4
     *  categorías solo tienen filas TEAM por clase), así que pedir siempre OVERALL+DRIVER
     *  dejaba estas 4 en "Sin datos todavía" para siempre aunque sí tuvieran clasificación
     *  guardada — bug real reportado. Para esas se pide el equipo en cabeza de su clase
     *  principal (StandingsCategory.primaryCarClass) en su lugar. */
    val leaderByCategory: StateFlow<Map<StandingsCategory, StandingEntity?>> = combine(
        StandingsCategory.entries.map { category ->
            val primaryCarClass = category.primaryCarClass
            val query = if (primaryCarClass != null) {
                standingsRepository.observe(category, primaryCarClass, StandingType.TEAM)
            } else {
                standingsRepository.observe(category, StandingsClass.OVERALL, StandingType.DRIVER)
            }
            query.map { rows -> category to rows.firstOrNull() }
        }
    ) { pairs -> pairs.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** true mientras las 7 fuentes están en vuelo — la pantalla cambia el botón de
     *  actualizar por un indicador de progreso para que se note que está trabajando. */
    var syncing by mutableStateOf(false)
        private set

    /** Informe de la última sincronización manual, o null si no hay ninguno pendiente de
     *  enseñar. La pantalla lo muestra en un diálogo con una línea por categoría (fase 2 del
     *  diagnóstico): antes solo se veía "N ok, M fallidas" en un Toast, sin el motivo — y el
     *  motivo (HTTP 403, tabla no encontrada, timeout, NetworkOnMainThreadException...) es
     *  justo lo único que permite arreglar la fuente concreta que falla. */
    private val _lastSyncReport = MutableStateFlow<StandingsRepository.SyncResult?>(null)
    val lastSyncReport: StateFlow<StandingsRepository.SyncResult?> = _lastSyncReport.asStateFlow()

    /** Sincronización manual bajo demanda.
     *
     *  30/08/2026 (bypass temporal de diagnóstico): llama a syncAll() DIRECTAMENTE en vez
     *  de pasar por WorkManager (StandingsScheduler.syncNow) — en el Samsung real de
     *  pruebas la tarea se quedaba colgada en "Enqueued" para siempre (gestión de batería
     *  de One UI bloqueando el trabajo en segundo plano) y nunca llegaba a ejecutarse. Para
     *  este botón el usuario ya está mirando la pantalla y esperando el resultado, así que
     *  no aporta nada depender del scheduler del sistema — WorkManager sigue teniendo
     *  sentido para StandingsScheduler.schedule() (el sync periódico semanal, que si tiene
     *  que sobrevivir a que la app esté cerrada). Los datos se actualizan solos en la UI en
     *  cuanto se guardan en Room, gracias a que leaderByCategory es un Flow. */
    fun refreshNow() {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            try {
                _lastSyncReport.value = standingsRepository.syncAll()
            } finally {
                syncing = false
            }
        }
    }

    fun dismissSyncReport() {
        _lastSyncReport.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    viewModel: StandingsViewModel = viewModel(),
    onCategoryClick: (StandingsCategory) -> Unit
) {
    val context = LocalContext.current
    val standingsEnabled by viewModel.standingsEnabled.collectAsState()
    val leaderByCategory by viewModel.leaderByCategory.collectAsState()

    val syncReport by viewModel.lastSyncReport.collectAsState()

    val isOnline = remember { ConnectivityHelper.isOnline(context) }
    val hasAnyCache = remember(leaderByCategory) { leaderByCategory.values.any { it != null } }

    // tr() es @Composable y no se puede llamar dentro del onClick de más abajo — se resuelve
    // aquí, en contexto de composición (mismo patrón que EventsScreen.kt).
    val offlineToastMessage = tr("events_offline_toast")

    syncReport?.let { report ->
        SyncReportDialog(report = report, onDismiss = viewModel::dismissSyncReport)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("standings_title"), fontWeight = FontWeight.Bold) },
                actions = {
                    if (viewModel.syncing) {
                        // Mismo hueco que el botón, para que la barra no dé un salto al
                        // cambiar uno por otro.
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
                                // Se comprueba la conexión en el momento de tocar el botón (no
                                // el "isOnline" recordado al entrar en la pantalla, que puede
                                // haberse quedado desactualizado) — sin esto, pulsar Actualizar
                                // sin internet lanzaba las 7 peticiones igualmente, todas
                                // fallaban, y el diálogo de resultado salía lleno de errores
                                // técnicos (timeout, host no resuelto...) que no aportaban nada:
                                // ya sabíamos de antemano que no había red.
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
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !standingsEnabled -> EmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = tr("standings_empty_disabled_title"),
                    message = tr("standings_empty_disabled_message")
                )

                !isOnline && !hasAnyCache -> EmptyState(
                    icon = Icons.Default.WifiOff,
                    title = tr("events_empty_offline_title"),
                    message = tr("standings_empty_offline_message")
                )

                else -> {
                    if (!isOnline) {
                        OfflineBanner()
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(StandingsCategory.entries) { category ->
                            CategoryRow(
                                category = category,
                                leader = leaderByCategory[category],
                                onClick = { onCategoryClick(category) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: StandingsCategory, leader: StandingEntity?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    // Fondo blanco fijo (no depende del tema) para que los logos oficiales,
                    // pensados para fondo claro, se vean bien siempre. Ya no es un círculo que
                    // recorta el logo — es una "pegatina" redondeada que sigue su forma natural,
                    // con un margen pequeño alrededor (28/08/2026).
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Logo real de la categoría (URL externa, ver StandingsCategory.logoUrl,
                // 28/08/2026 — mejor esfuerzo, no verificable desde este entorno). Si no
                // carga o mientras carga, se ve el mismo icono de trofeo genérico de antes.
                SubcomposeAsyncImage(
                    model = category.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(42.dp),
                    loading = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    error = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
            Column(Modifier.weight(1f)) {
                Text(category.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = if (leader != null) {
                        tr("standings_leading").format(leader.name, formatPoints(leader.points))
                    } else {
                        tr("standings_no_data_yet")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Los puntos vienen como Double (necesario para NASCAR/IndyCar en algunos casos), pero
 *  se muestran sin decimales cuando son un número entero — que es lo habitual. */
internal fun formatPoints(points: Double): String =
    if (points == points.toLong().toDouble()) points.toLong().toString() else points.toString()

/**
 * Resultado de la sincronización manual: solo el recuento en el título ("Sincronización: X
 * de Y OK") — pedido explícito de no enumerar cada categoría con sus filas. Si alguna
 * falló, se lista solo esa con un motivo corto y sin tecnicismos (ver
 * StandingsRepository.friendlyReason) — el texto real de la excepción (HTTP 403,
 * timeout...) sigue yendo a Logcat para depurar, pero nunca se copia tal cual aquí.
 */
@Composable
private fun SyncReportDialog(
    report: StandingsRepository.SyncResult,
    onDismiss: () -> Unit
) {
    val failed = report.outcomes.filterNot { it.ok }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("events_close")) }
        },
        title = {
            Text(tr("standings_sync_result").format(report.succeeded.size, report.outcomes.size))
        },
        text = if (failed.isEmpty()) null else {
            {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                failed.forEach { outcome ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = outcome.category.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        outcome.detail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            }
        }
    )
}
