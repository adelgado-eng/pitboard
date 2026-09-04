package com.pitboard.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

class AppSettingsRepository(private val context: Context) {

    val notificationsEnabled: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] ?: true }

    /** Se marca la primera vez que la app pide el permiso del sistema (al abrirla por
     *  primera vez). Sirve para dos cosas: no volver a lanzar el dialogo en cada arranque
     *  y saber si un "denegado" ya es definitivo (ver NotificationPermission). */
    val notificationPermissionRequested: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_NOTIFICATION_PERMISSION_REQUESTED] ?: false }

    val notificationMinutesBefore: Flow<Int> = context.appSettingsDataStore.data
        .map { prefs ->
            val stored = prefs[KEY_NOTIFICATION_MINUTES] ?: DEFAULT_MINUTES_BEFORE
            if (stored in VALID_MINUTES) stored else DEFAULT_MINUTES_BEFORE
        }

    val competitiveNotificationsEnabled: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_COMPETITIVE_NOTIFICATIONS_ENABLED] ?: true }

    val practiceNotificationsEnabled: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_PRACTICE_NOTIFICATIONS_ENABLED] ?: false }

    /** Clasificaciones (standings): a diferencia de los avisos, por defecto está
     *  DESACTIVADO — requiere internet (primera vez que la app sale a la red) y es un
     *  opt-in explícito, no algo que deba activarse solo. */
    val standingsEnabled: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_STANDINGS_ENABLED] ?: false }

    val appTheme: Flow<AppTheme> = context.appSettingsDataStore.data
        .map { prefs ->
            prefs[KEY_APP_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM
        }

    /** Series sin avisos. Vacío = ninguna excluida (todas notifican). */
    val notificationDisabledSeries: Flow<Set<RaceSeries>> = context.appSettingsDataStore.data
        .map { prefs ->
            prefs[KEY_NOTIFICATION_DISABLED_SERIES]
                ?.asSequence()
                ?.mapNotNull { runCatching { RaceSeries.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    /** Series activas en el filtro rápido de Eventos. Vacío = todas (ver EventsScreen). */
    val eventScreenActiveSeries: Flow<Set<RaceSeries>> = context.appSettingsDataStore.data
        .map { prefs ->
            prefs[KEY_EVENT_SCREEN_SERIES]
                ?.asSequence()
                ?.mapNotNull { runCatching { RaceSeries.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    /** Tipos de sesión activos en el filtro rápido de Eventos (valores de SessionBadgeType:
     *  "C"/"Q"/"S"/"L"). Vacío = todos (mismo convenio que eventScreenActiveSeries). */
    val eventScreenActiveSessionTypes: Flow<Set<String>> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_EVENT_SCREEN_SESSION_TYPES] ?: emptySet() }

    /** 03/09/2026: se marca la primera vez que la app termina (con éxito o sin él) su
     *  sincronización de arranque de Eventos + Clasificaciones — ver MainActivity.
     *  Mientras esté en `false`, cada apertura de la app repite esa sincronización
     *  completa con pantalla de carga; en cuanto se marca, las siguientes aperturas ya
     *  no tocan Eventos (se queda con el ciclo diario en segundo plano) y solo
     *  refrescan Clasificaciones si el interruptor de Ajustes está activado. */
    val hasCompletedFirstSync: Flow<Boolean> = context.appSettingsDataStore.data
        .map { prefs -> prefs[KEY_HAS_COMPLETED_FIRST_SYNC] ?: false }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_PERMISSION_REQUESTED] = requested
        }
    }

    suspend fun setNotificationMinutesBefore(minutes: Int) {
        require(minutes in VALID_MINUTES) { "Antelación no soportada: $minutes" }
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_NOTIFICATION_MINUTES] = minutes }
    }

    suspend fun setCompetitiveNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_COMPETITIVE_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPracticeNotificationsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_PRACTICE_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setStandingsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_STANDINGS_ENABLED] = enabled }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_APP_THEME] = theme.name }
    }

    suspend fun setNotificationDisabledSeries(series: Set<RaceSeries>) {
        context.appSettingsDataStore.edit { prefs ->
            if (series.isEmpty()) {
                prefs.remove(KEY_NOTIFICATION_DISABLED_SERIES)
            } else {
                prefs[KEY_NOTIFICATION_DISABLED_SERIES] = series.asSequence().map { it.name }.toSet()
            }
        }
    }

    suspend fun setEventScreenActiveSeries(series: Set<RaceSeries>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[KEY_EVENT_SCREEN_SERIES] = series.asSequence().map { it.name }.toSet()
        }
    }

    suspend fun setEventScreenActiveSessionTypes(sessionTypes: Set<String>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[KEY_EVENT_SCREEN_SESSION_TYPES] = sessionTypes
        }
    }

    suspend fun setHasCompletedFirstSync(completed: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[KEY_HAS_COMPLETED_FIRST_SYNC] = completed }
    }

    suspend fun isNotificationsEnabledNow(): Boolean = notificationsEnabled.first()

    suspend fun wasNotificationPermissionRequestedNow(): Boolean =
        notificationPermissionRequested.first()

    suspend fun isStandingsEnabledNow(): Boolean = standingsEnabled.first()

    suspend fun hasCompletedFirstSyncNow(): Boolean = hasCompletedFirstSync.first()

    suspend fun notificationDisabledSeriesNow(): Set<RaceSeries> =
        notificationDisabledSeries.first()

    companion object {
        const val DEFAULT_MINUTES_BEFORE = 60
        val VALID_MINUTES = setOf(15, 30, 60)

        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_MINUTES = intPreferencesKey("notification_minutes_before")
        private val KEY_NOTIFICATION_PERMISSION_REQUESTED =
            booleanPreferencesKey("notification_permission_requested")
        private val KEY_COMPETITIVE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("competitive_notifications_enabled")
        private val KEY_PRACTICE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("practice_notifications_enabled")
        private val KEY_STANDINGS_ENABLED = booleanPreferencesKey("standings_enabled")
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_NOTIFICATION_DISABLED_SERIES =
            stringSetPreferencesKey("notification_disabled_series")
        private val KEY_EVENT_SCREEN_SERIES = stringSetPreferencesKey("event_screen_series")
        private val KEY_EVENT_SCREEN_SESSION_TYPES = stringSetPreferencesKey("event_screen_session_types")
        private val KEY_HAS_COMPLETED_FIRST_SYNC = booleanPreferencesKey("has_completed_first_sync")
    }
}
