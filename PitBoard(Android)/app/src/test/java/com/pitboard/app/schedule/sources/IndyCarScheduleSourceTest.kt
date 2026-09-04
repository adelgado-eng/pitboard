package com.pitboard.app.schedule.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): indycar.com/schedule marca las carreras ya disputadas
 * con la clase "completed" en la propia tarjeta — el fixture comprueba que esas se
 * descartan en vez de aparecer con una cuenta atrás sin sentido.
 */
class IndyCarScheduleSourceTest {

    private val source = IndyCarScheduleSource()

    private val fixtureHtml = """
        <html><body>
        <div class="event-card completed">
          <div class="event-card-header-date">Aug 24</div>
          <div class="event-card-header-time">3:00 PM ET</div>
          <div class="event-card-title">Firestone Grand Prix</div>
          <div class="event-card-track-name">Nashville</div>
        </div>
        <div class="event-card">
          <div class="event-card-header-date">Sep 6</div>
          <div class="event-card-header-time">2:30 PM ET</div>
          <div class="event-card-title">Milwaukee Mile</div>
          <div class="event-card-track-name">Milwaukee</div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `descarta las tarjetas de carreras ya disputadas`() {
        val events = source.parseHtml(fixtureHtml)

        assertEquals(1, events.size)
        assertTrue(events[0].fullTitle.contains("Milwaukee Mile"))
        assertTrue(events.none { it.fullTitle.contains("Firestone") })
    }

    @Test
    fun `resuelve fecha y hora igual que UsScheduleDateParsing`() {
        val events = source.parseHtml(fixtureHtml)
        val expected = UsScheduleDateParsing.toUtcMillis("Sep 6", "2:30 PM ET")

        assertEquals(expected, events[0].startTimeUtc)
    }

    @Test
    fun `sin tarjetas reconocibles, devuelve vacio en vez de fallar`() {
        assertEquals(emptyList<Any>(), source.parseHtml("<html><body><p>Sin calendario</p></body></html>"))
    }
}
