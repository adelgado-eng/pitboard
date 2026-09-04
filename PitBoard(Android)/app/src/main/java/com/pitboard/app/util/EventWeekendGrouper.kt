package com.pitboard.app.util

import com.pitboard.app.data.EventEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class EventWeekendGroups(
    val weekendLabel: String,
    /** Misma etiqueta que [weekendLabel] pero como clave de com.pitboard.app.i18n.Strings
     *  (ej. "events_weekend_today") en vez de texto fijo en español — [weekendLabel] se deja
     *  igual a propósito (lo siguen leyendo RaceWidget.kt y quien construya notificaciones
     *  a partir de aquí) y esta clave nueva es solo para quien quiera traducir la etiqueta
     *  (ver EventsScreen.kt). Vacía cuando [weekendLabel] también lo está (sin eventos). */
    val weekendLabelKey: String,
    val weekendEvents: List<EventEntity>,
    val laterEvents: List<EventEntity>,
)

object EventWeekendGrouper {

    /**
     * Separa eventos en el bloque del fin de semana más cercano que contenga eventos
     * (viernes–domingo) y el resto de eventos futuros.
     */
    fun split(events: List<EventEntity>, zone: ZoneId = ZoneId.systemDefault()): EventWeekendGroups {
        if (events.isEmpty()) {
            return EventWeekendGroups(weekendLabel = "", weekendLabelKey = "", weekendEvents = emptyList(), laterEvents = emptyList())
        }

        // Buscamos el primer evento para determinar cuál es el "fin de semana más próximo" con actividad
        val firstDate = Instant.ofEpochMilli(events.first().startTimeUtc).atZone(zone).toLocalDate()
        
        // Definimos el margen del fin de semana (Viernes a Domingo) que envuelve a ese primer evento
        val friday = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        val sunday = firstDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        
        val weekendStartMs = friday.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekendEndMs = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val today = LocalDate.now(zone)
        val (label, labelKey) = when {
            firstDate == today -> "Hoy" to "events_weekend_today"
            today in friday..sunday -> "Este fin de semana" to "events_weekend_this"
            friday == today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)) -> "Próximo fin de semana" to "events_weekend_next"
            else -> "Próxima cita" to "events_weekend_upcoming"
        }

        val weekend = events.filter { it.startTimeUtc in weekendStartMs..weekendEndMs }
        val later = events.filter { it.startTimeUtc > weekendEndMs }

        return EventWeekendGroups(
            weekendLabel = label,
            weekendLabelKey = labelKey,
            weekendEvents = weekend,
            laterEvents = later
        )
    }
}
