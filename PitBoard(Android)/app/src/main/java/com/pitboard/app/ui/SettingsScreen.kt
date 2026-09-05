package com.pitboard.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.AppTheme
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.data.TimeDisplayMode
import com.pitboard.app.i18n.tr
import com.pitboard.app.notifications.NotificationPermission
import com.pitboard.app.notifications.NotificationScheduler
import com.pitboard.app.standings.StandingsScheduler
import com.pitboard.app.util.ColorContrast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val appSettingsRepository = AppSettingsRepository(application)
    private val notificationScheduler = NotificationScheduler(
        context = application,
        eventDao = database.eventDao(),
        appSettingsRepository = appSettingsRepository
    )

    val notificationsEnabled = appSettingsRepository.notificationsEnabled
    val notificationPermissionRequested = appSettingsRepository.notificationPermissionRequested
    val competitiveEnabled = appSettingsRepository.competitiveNotificationsEnabled
    val practiceEnabled = appSettingsRepository.practiceNotificationsEnabled
    val minutesBefore = appSettingsRepository.notificationMinutesBefore
    val appTheme = appSettingsRepository.appTheme
    val standingsEnabled = appSettingsRepository.standingsEnabled
    val timeDisplayMode = appSettingsRepository.timeDisplayMode

    val seriesConfigs: StateFlow<List<SeriesConfigEntity>> =
        database.seriesConfigDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notificationActiveSeries: StateFlow<Set<RaceSeries>> =
        appSettingsRepository.notificationDisabledSeries
            .map { disabled -> RaceSeries.entries.toSet() - disabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RaceSeries.entries.toSet())

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setNotificationsEnabled(enabled)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    /** Respuesta del dialogo del sistema: el interruptor sigue exactamente lo que el
     *  usuario haya contestado (aceptado = activado, rechazado = desactivado). */
    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setNotificationPermissionRequested(true)
            appSettingsRepository.setNotificationsEnabled(granted)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    /** Si el permiso se ha revocado desde los ajustes del sistema mientras la app estaba
     *  en segundo plano, el interruptor no puede quedarse en "activado" mintiendo. */
    fun syncWithSystemPermission(granted: Boolean) {
        viewModelScope.launch {
            if (!granted && appSettingsRepository.isNotificationsEnabledNow()) {
                appSettingsRepository.setNotificationsEnabled(false)
                notificationScheduler.rescheduleAllUpcoming()
            }
        }
    }

    fun setCompetitiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setCompetitiveNotificationsEnabled(enabled)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    fun setPracticeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPracticeNotificationsEnabled(enabled)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    fun setMinutesBefore(minutes: Int) {
        viewModelScope.launch {
            appSettingsRepository.setNotificationMinutesBefore(minutes)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    fun toggleNotificationSeries(series: RaceSeries, enabled: Boolean) {
        viewModelScope.launch {
            val disabled = appSettingsRepository.notificationDisabledSeriesNow().toMutableSet()
            if (enabled) disabled.remove(series) else disabled.add(series)
            appSettingsRepository.setNotificationDisabledSeries(disabled)
            notificationScheduler.rescheduleAllUpcoming()
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { appSettingsRepository.setAppTheme(theme) }
    }

    fun setTimeDisplayMode(mode: TimeDisplayMode) {
        viewModelScope.launch { appSettingsRepository.setTimeDisplayMode(mode) }
    }

    /** Activa/desactiva la sincronización semanal de clasificaciones. Al activar, además
     *  programa el ciclo semanal (próximo lunes 12:00) y lanza una sincronización
     *  inmediata — así el usuario no tiene que esperar hasta el lunes para ver algo. Al
     *  desactivar, cancela el trabajo programado (no se vuelve a pedir nada a internet). */
    fun setStandingsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setStandingsEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                StandingsScheduler.schedule(context)
                StandingsScheduler.syncNow(context)
            } else {
                StandingsScheduler.cancel(context)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val permissionAlreadyRequested by viewModel.notificationPermissionRequested.collectAsState(initial = false)
    val competitiveEnabled by viewModel.competitiveEnabled.collectAsState(initial = true)
    val practiceEnabled by viewModel.practiceEnabled.collectAsState(initial = false)
    val minutesBefore by viewModel.minutesBefore.collectAsState(initial = 60)
    val appTheme by viewModel.appTheme.collectAsState(initial = AppTheme.SYSTEM)
    val standingsEnabled by viewModel.standingsEnabled.collectAsState(initial = false)
    val timeDisplayMode by viewModel.timeDisplayMode.collectAsState(initial = TimeDisplayMode.DEVICE)
    val seriesConfigs by viewModel.seriesConfigs.collectAsState()
    val activeSeries by viewModel.notificationActiveSeries.collectAsState()

    var showCategoryPicker by remember { mutableStateOf(false) }
    // Dialogo propio para el caso "el sistema ya no ensena nada": la unica via es Ajustes
    var showPermissionBlockedDialog by remember { mutableStateOf(false) }
    // Marca que hemos mandado al usuario a los ajustes del sistema queriendo activar los
    // avisos, para encender el interruptor al volver si alli concedio el permiso.
    var waitingForSystemSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    // Al volver a la pantalla (por ejemplo desde los ajustes del sistema) se vuelve a mirar
    // el permiso real y se pone el interruptor de acuerdo con el.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val granted = NotificationPermission.isGranted(context)
            if (granted && waitingForSystemSettings) {
                waitingForSystemSettings = false
                viewModel.onNotificationPermissionResult(true)
            } else {
                if (!granted) waitingForSystemSettings = false
                viewModel.syncWithSystemPermission(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tr("settings_title"), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // TARJETA: NOTIFICACIONES
            SettingsCard(title = tr("settings_notifications_title"), icon = Icons.Default.Notifications) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("settings_notifications_enable"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("settings_notifications_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { wantsEnabled ->
                            when {
                                // Apagar nunca necesita permiso
                                !wantsEnabled -> viewModel.setNotificationsEnabled(false)

                                // Ya lo tenemos concedido: se activa y listo
                                NotificationPermission.isGranted(context) ->
                                    viewModel.setNotificationsEnabled(true)

                                // Denegado de forma definitiva (dijo que no dos veces o
                                // silencio la app): el sistema ya no ensena el dialogo, asi
                                // que se lo explicamos y le llevamos a los ajustes.
                                NotificationPermission.isBlockedBySystem(context, permissionAlreadyRequested) ->
                                    showPermissionBlockedDialog = true

                                // Caso normal: volvemos a pedir el permiso del sistema
                                else -> permissionLauncher.launch(NotificationPermission.PERMISSION)
                            }
                        }
                    )
                }

                if (notificationsEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                    Text(tr("settings_notifications_when"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15 to "15m", 30 to "30m", 60 to "1h").forEach { (min, label) ->
                            FilterChip(
                                selected = minutesBefore == min,
                                onClick = { viewModel.setMinutesBefore(min) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(tr("settings_notifications_which_sessions"), style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SessionTypeToggle(
                            label = tr("settings_notifications_competitive"),
                            checked = competitiveEnabled,
                            onCheckedChange = viewModel::setCompetitiveEnabled
                        )
                        SessionTypeToggle(
                            label = tr("settings_notifications_practice"),
                            checked = practiceEnabled,
                            onCheckedChange = viewModel::setPracticeEnabled
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = tr("cd_edit_active_series")) { showCategoryPicker = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("settings_notifications_active_series"), style = MaterialTheme.typography.bodyMedium)
                            val count = activeSeries.size
                            Text(
                                if (count == RaceSeries.entries.size) {
                                    tr("settings_notifications_all_series")
                                } else {
                                    tr("settings_notifications_series_count").format(count)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // TARJETA: CLASIFICACIONES
            SettingsCard(title = tr("settings_standings_title"), icon = Icons.Default.EmojiEvents) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("settings_standings_enable"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("settings_standings_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = standingsEnabled,
                        onCheckedChange = { wantsEnabled -> viewModel.setStandingsEnabled(wantsEnabled) }
                    )
                }
            }

            // TARJETA: APARIENCIA
            SettingsCard(title = tr("settings_appearance_title"), icon = Icons.Default.Palette) {
                Text(
                    tr("settings_appearance_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppTheme.LIGHT to tr("settings_theme_light"),
                        AppTheme.DARK to tr("settings_theme_dark"),
                        AppTheme.SYSTEM to tr("settings_theme_auto")
                    ).forEach { (theme, label) ->
                        FilterChip(
                            selected = appTheme == theme,
                            onClick = { viewModel.setAppTheme(theme) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // TARJETA: ZONA HORARIA
            SettingsCard(title = tr("settings_timezone_title"), icon = Icons.Default.Public) {
                Text(
                    tr("settings_timezone_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TimeDisplayMode.DEVICE to tr("settings_timezone_device"),
                        TimeDisplayMode.TRACK to tr("settings_timezone_track")
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = timeDisplayMode == mode,
                            onClick = { viewModel.setTimeDisplayMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // TARJETA: AYUDA (avisos/widget que no llegan por ahorro de batería del fabricante)
            SettingsCard(title = tr("settings_battery_help_title"), icon = Icons.Default.BatteryAlert) {
                Text(
                    tr("settings_battery_help_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))
                    runCatching { context.startActivity(intent) }
                }) {
                    Text(tr("settings_battery_help_button"))
                }
            }

            // 30/08/2026 (2): aquí había una tarjeta "Sincronización" con un botón
            // "Actualizar ahora" que releía todos los .ics importados. Se ha quitado junto
            // con el resto del resincronizado (ver IcsImportRepository): el .ics es una
            // copia local que no cambia sola, así que el botón no podía traer nada nuevo.

            Text(
                tr("settings_footer"),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    if (showPermissionBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionBlockedDialog = false },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            title = { Text(tr("settings_notifications_permission_title")) },
            text = {
                Text(tr("settings_notifications_permission_body"))
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionBlockedDialog = false
                    waitingForSystemSettings = true
                    NotificationPermission.openSystemNotificationSettings(context)
                }) { Text(tr("settings_open_settings")) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionBlockedDialog = false }) { Text(tr("settings_not_now")) }
            }
        )
    }

    if (showCategoryPicker) {
        NotificationSeriesPicker(
            seriesConfigs = seriesConfigs,
            selected = activeSeries,
            onDismiss = { showCategoryPicker = false },
            onToggle = viewModel::toggleNotificationSeries
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SessionTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NotificationSeriesPicker(
    seriesConfigs: List<SeriesConfigEntity>,
    selected: Set<RaceSeries>,
    onDismiss: () -> Unit,
    onToggle: (RaceSeries, Boolean) -> Unit
) {
    val configByKey = seriesConfigs.associateBy { it.series }

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("settings_series_with_alerts_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = tr("events_close")) }
                }

                Text(
                    tr("settings_series_with_alerts_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RaceSeries.entries.forEach { series ->
                        val config = configByKey[series]
                        val tag = config?.tag ?: series.defaultTag
                        val colorHex = config?.colorHex ?: series.defaultColorHex
                        val isSelected = series in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onToggle(series, !isSelected) }
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tagColor = ColorContrast.safeParseColor(colorHex)
                            Box(
                                modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall).background(tagColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tag, color = ColorContrast.readableTextColor(tagColor), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(series.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = isSelected, onCheckedChange = { onToggle(series, it) })
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(tr("settings_done"))
                }
            }
        }
    }
}