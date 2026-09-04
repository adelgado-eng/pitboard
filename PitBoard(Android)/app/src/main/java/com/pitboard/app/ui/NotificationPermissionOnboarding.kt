package com.pitboard.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.notifications.NotificationPermission
import com.pitboard.app.notifications.NotificationScheduler
import kotlinx.coroutines.launch

/**
 * Primer arranque: pedimos el permiso de notificaciones nada mas abrir la app.
 *
 *  - Si el usuario ACEPTA  -> el interruptor "Activar avisos" de Ajustes queda activado.
 *  - Si el usuario RECHAZA -> el interruptor queda desactivado (no tendria sentido
 *    mostrarlo activado si el sistema no nos deja notificar nada).
 *
 * Se pide una sola vez: se guarda una marca en DataStore para no volver a lanzar el
 * dialogo en cada arranque. A partir de ahi, quien lo vuelve a pedir es el interruptor
 * de la pantalla de Ajustes (ver SettingsScreen).
 */
@Composable
fun NotificationPermissionOnboarding(appSettingsRepository: AppSettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduler = remember {
        NotificationScheduler(
            context = context.applicationContext,
            eventDao = AppDatabase.getInstance(context.applicationContext).eventDao(),
            appSettingsRepository = appSettingsRepository
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            appSettingsRepository.setNotificationPermissionRequested(true)
            appSettingsRepository.setNotificationsEnabled(granted)
            scheduler.rescheduleAllUpcoming()
        }
    }

    LaunchedEffect(Unit) {
        if (appSettingsRepository.wasNotificationPermissionRequestedNow()) return@LaunchedEffect

        if (!NotificationPermission.requiresRuntimeRequest) {
            // Android 12 o anterior: no hay dialogo que lanzar, las notificaciones vienen
            // concedidas salvo que el usuario las haya silenciado desde el sistema.
            appSettingsRepository.setNotificationPermissionRequested(true)
            appSettingsRepository.setNotificationsEnabled(NotificationPermission.isGranted(context))
            scheduler.rescheduleAllUpcoming()
            return@LaunchedEffect
        }

        if (NotificationPermission.isGranted(context)) {
            appSettingsRepository.setNotificationPermissionRequested(true)
            appSettingsRepository.setNotificationsEnabled(true)
            scheduler.rescheduleAllUpcoming()
        } else {
            // La marca se guarda en el callback del launcher, con la respuesta ya en mano
            permissionLauncher.launch(NotificationPermission.PERMISSION)
        }
    }
}
