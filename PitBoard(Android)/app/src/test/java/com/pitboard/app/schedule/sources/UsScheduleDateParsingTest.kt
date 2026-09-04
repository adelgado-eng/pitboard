package com.pitboard.app.schedule.sources

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fase 1 del diagnóstico (graphify): utilidad compartida por IndyCar y NASCAR (vía ESPN) —
 * ya era pura, sin red, así que se testea directamente sin ningún refactor.
 */
class UsScheduleDateParsingTest {

    @Test
    fun `una fecha que ya paso este año se resuelve al año que viene`() {
        val today = LocalDate.of(2026, 12, 20)

        assertEquals(LocalDate.of(2027, 1, 15), UsScheduleDateParsing.resolveUpcomingMonthDay("Jan 15", today))
    }

    @Test
    fun `una fecha que todavia no ha pasado se queda en este año`() {
        val today = LocalDate.of(2026, 3, 1)

        assertEquals(LocalDate.of(2026, 9, 6), UsScheduleDateParsing.resolveUpcomingMonthDay("Sep 6", today))
    }

    @Test
    fun `parseTimeOfDay entiende Noon y Midnight ademas de la hora numerica`() {
        assertEquals(LocalTime.NOON, UsScheduleDateParsing.parseTimeOfDay("Noon ET"))
        assertEquals(LocalTime.MIDNIGHT, UsScheduleDateParsing.parseTimeOfDay("Midnight"))
        assertEquals(LocalTime.of(14, 30), UsScheduleDateParsing.parseTimeOfDay("2:30 PM ET"))
    }

    @Test
    fun `sin hora, toUtcMillis usa mediodia del Este como marcador`() {
        val today = LocalDate.of(2026, 3, 1)

        val millis = UsScheduleDateParsing.toUtcMillis("Sep 6", timeText = null, today = today)!!
        val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.of("America/New_York"))

        assertEquals(LocalTime.NOON, zoned.toLocalTime())
        assertEquals(LocalDate.of(2026, 9, 6), zoned.toLocalDate())
    }

    @Test
    fun `eastZoneId devuelve la zona del Este`() {
        assertEquals("America/New_York", UsScheduleDateParsing.eastZoneId())
    }
}
