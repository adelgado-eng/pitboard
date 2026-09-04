package com.pitboard.app.util

import com.pitboard.app.data.EventEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class EventWeekendGroups(
    val weekendLabel: String,
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
            return EventWeekendGroups(weekendLabel = "", weekendEvents = emptyList(), laterEvents = emptyList())
        }

        // Buscamos el primer evento para determinar cuál es el "fin de semana más próximo" con actividad
        val firstDate = Instant.ofEpochMilli(events.first().startTimeUtc).atZone(zone).toLocalDate()
        
        // Definimos el margen del fin de semana (Viernes a Domingo) que envuelve a ese primer evento
        val friday = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        val sunday = firstDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        
        val weekendStartMs = friday.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekendEndMs = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val today = LocalDate.now(zone)
        val label = when {
            firstDate == today -> "Hoy"
            today in friday..sunday -> "Este fin de semana"
            friday == today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)) -> "Próximo fin de semana"
            else -> "Próxima cita"
        }

        val weekend = events.filter { it.startTimeUtc in weekendStartMs..weekendEndMs }
        val later = events.filter { it.startTimeUtc > weekendEndMs }

        return EventWeekendGroups(
            weekendLabel = label,
            weekendEvents = weekend,
            laterEvents = later
        )
    }
}
