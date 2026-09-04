package com.pitboard.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateTimeFormatters {

    private val syncFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "ES"))
    private val eventDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale("es", "ES"))
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "ES"))
    private val eventDateFormatterLong = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "ES"))

    fun formatSyncTimestamp(lastSyncedAt: Long?, importedAt: Long): String {
        val isImportOnly = lastSyncedAt == null
        val millis = lastSyncedAt ?: importedAt
        val zone = ZoneId.systemDefault()
        val zoned = Instant.ofEpochMilli(millis).atZone(zone)
        val now = Instant.now().atZone(zone)
        val prefix = if (isImportOnly) "Importado" else "Actualizado"
        val absolute = syncFormatter.format(zoned)

        val minutesAgo = ChronoUnit.MINUTES.between(zoned, now)
        if (isImportOnly) return "$prefix $absolute"

        return when {
            minutesAgo < 1 -> "$prefix hace un momento"
            minutesAgo < 60 -> "$prefix hace $minutesAgo min"
            ChronoUnit.HOURS.between(zoned, now) < 24 -> {
                val hours = ChronoUnit.HOURS.between(zoned, now)
                "$prefix hace $hours h"
            }
            ChronoUnit.DAYS.between(zoned.toLocalDate(), now.toLocalDate()) == 1L ->
                "$prefix ayer, $absolute"
            else -> "$prefix $absolute"
        }
    }

    fun formatEventDateTime(startTimeUtc: Long): String {
        val zoned = Instant.ofEpochMilli(startTimeUtc).atZone(ZoneId.systemDefault())
        return eventDateFormatter.format(zoned)
    }

    /** Solo la hora, en la zona horaria local del dispositivo (ej: "15:00"). Usado para
     *  mostrar la hora exacta de inicio en las notificaciones. */
    fun formatTimeOnly(startTimeUtc: Long): String {
        val zoned = Instant.ofEpochMilli(startTimeUtc).atZone(ZoneId.systemDefault())
        return timeOnlyFormatter.format(zoned)
    }

    /** Fecha y hora completas (día de la semana, día, mes y año) en la zona horaria del
     *  DISPOSITIVO — para el panel de detalle de un evento (ver EventsScreen), donde
     *  "EEE d MMM · HH:mm" (formatEventDateTime) se queda corto porque no lleva el año. */
    fun formatEventDateTimeLong(startTimeUtc: Long): String {
        val zoned = Instant.ofEpochMilli(startTimeUtc).atZone(ZoneId.systemDefault())
        return eventDateFormatterLong.format(zoned).replaceFirstChar { it.uppercase() }
    }

    /** Igual que [formatEventDateTimeLong] pero en la zona horaria indicada (ej. la del
     *  circuito, "America/New_York") en vez de la del dispositivo — null si el id de zona no
     *  es válido, para no mostrar una hora inventada. */
    fun formatEventDateTimeInZone(startTimeUtc: Long, zoneId: String): String? {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return null
        val zoned = Instant.ofEpochMilli(startTimeUtc).atZone(zone)
        return eventDateFormatterLong.format(zoned).replaceFirstChar { it.uppercase() }
    }
}