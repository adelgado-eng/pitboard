package com.pitboard.app.schedule.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fase 1 del diagnóstico (graphify): imsa.com mezcla la clase principal (WeatherTech
 * Championship) con las de apoyo en la misma sección de horario, sin más marca que el
 * propio texto del nombre — el fixture reproduce esa mezcla para comprobar el filtro por
 * palabra clave (ver SUPPORT_SERIES en el KDoc de la clase).
 */
class ImsaScheduleSourceTest {

    private val source = ImsaScheduleSource()

    private val fixtureHtml = """
        <html><head><title>2026 Rolex 24 At DAYTONA | IMSA</title></head><body>
        <div class="race-event-schedule-container-inner">
          <div class="day-event-header">Friday, January 23, 2026</div>
          <div class="day-event-details-container">
            <div class="event-time">10:05 AM to 11:35 AM ET</div>
            <div class="event-name">WeatherTech Championship Practice</div>
          </div>
          <div class="day-event-details-container">
            <div class="event-time">1:00 PM to 2:00 PM ET</div>
            <div class="event-name">Mazda MX-5 Cup Race</div>
          </div>
          <div class="day-event-header">Saturday, January 24, 2026</div>
          <div class="day-event-details-container">
            <div class="event-time">3:40 PM to 4:00 PM ET</div>
            <div class="event-name">Rolex 24 At Daytona</div>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `saca el nombre de la ronda del title, quitando el sufijo del sitio y el año`() {
        val events = source.parseEventHtml(fixtureHtml, "https://www.imsa.com/events/rolex-24/")

        assertTrue(events.all { it.fullTitle.contains("Rolex 24 At DAYTONA") })
        assertTrue(events.none { it.fullTitle.contains("| IMSA") })
    }

    @Test
    fun `descarta las sesiones de clases de apoyo por su nombre`() {
        val events = source.parseEventHtml(fixtureHtml, "https://www.imsa.com/events/rolex-24/")

        assertEquals(2, events.size)
        assertTrue(events.none { it.fullTitle.contains("Mazda MX-5") })
    }

    @Test
    fun `solo guarda la hora de inicio del rango, no la de fin`() {
        val events = source.parseEventHtml(fixtureHtml, "https://www.imsa.com/events/rolex-24/")

        val practice = events.first { it.fullTitle.contains("Practice") }
        val expected = LocalDateTime.of(LocalDate.of(2026, 1, 23), LocalTime.of(10, 5))
            .atZone(ZoneId.of("America/New_York"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, practice.startTimeUtc)
    }

    @Test
    fun `cada dia agrupa sus propias sesiones, el segundo dia no hereda la fecha del primero`() {
        val events = source.parseEventHtml(fixtureHtml, "https://www.imsa.com/events/rolex-24/")

        val race = events.first { it.fullTitle.contains("Rolex 24 At Daytona") }
        val expected = LocalDateTime.of(LocalDate.of(2026, 1, 24), LocalTime.of(15, 40))
            .atZone(ZoneId.of("America/New_York"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, race.startTimeUtc)
    }
}
