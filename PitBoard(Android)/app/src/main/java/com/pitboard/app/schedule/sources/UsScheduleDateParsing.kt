package com.pitboard.app.schedule.sources

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fecha+hora compartida por las fuentes que scrapean webs en inglés de EE. UU. (IndyCar,
 * NASCAR vía ESPN): dan el día sin año ("Sep 6") y la hora en horario del Este ("2:30 PM ET"),
 * que es como estas webs muestran los horarios de emisión sin importar dónde esté el circuito.
 */
object UsScheduleDateParsing {
    private val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val EASTERN = ZoneId.of("America/New_York")

    /**
     * "Sep 6" -> la próxima fecha con ese día/mes (este año si aún no ha pasado, si no el que
     * viene) — así una temporada que arranca en enero y esta ejecutándose en diciembre no
     * coloca sus primeras carreras "hace un año".
     */
    fun resolveUpcomingMonthDay(text: String, today: LocalDate = LocalDate.now(EASTERN)): LocalDate? {
        val monthDay = runCatching { monthDayFormatter.parse(text.trim()) }.getOrNull() ?: return null
        val month = monthDay.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        val day = monthDay.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (thisYear.isBefore(today.minusDays(3))) {
            runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
        } else {
            thisYear
        }
    }

    /** "2:30 PM ET" / "2:30 PM" / "Noon ET" -> LocalTime, ignorando cualquier sufijo de zona
     *  horaria. ESPN usa "Noon"/"Midnight" en vez de la hora numérica en algunas filas. */
    fun parseTimeOfDay(text: String): LocalTime? {
        val cleaned = text.trim().removeSuffix("ET").trim()
        return when {
            cleaned.equals("Noon", ignoreCase = true) -> LocalTime.NOON
            cleaned.equals("Midnight", ignoreCase = true) -> LocalTime.MIDNIGHT
            else -> runCatching { LocalTime.parse(cleaned, timeFormatter) }.getOrNull()
        }
    }

    /** Combina fecha (sin año, se resuelve al año que toque) + hora en horario del Este y
     *  devuelve epoch millis UTC. Si no hay hora, se usa mediodía como marcador — mejor que
     *  nada, pero es una fecha sin hora real (ver comentario en cada fuente que lo use). */
    fun toUtcMillis(dateText: String, timeText: String?, today: LocalDate = LocalDate.now(EASTERN)): Long? {
        val date = resolveUpcomingMonthDay(dateText, today) ?: return null
        val time = timeText?.let { parseTimeOfDay(it) } ?: LocalTime.NOON
        return LocalDateTime.of(date, time).atZone(EASTERN).toInstant().toEpochMilli()
    }

    fun eastZoneId(): String = EASTERN.id
}
