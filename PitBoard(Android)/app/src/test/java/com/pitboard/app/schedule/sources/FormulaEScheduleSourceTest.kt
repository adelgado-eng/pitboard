package com.pitboard.app.schedule.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): fiaformulae.com/en/calendar sirve un único JSON-LD
 * ItemList con toda la temporada — algunas rondas traen `subEvent` con sesiones, las más
 * lejanas todavía no (ver KDoc de la clase). El fixture reproduce ambos casos.
 */
class FormulaEScheduleSourceTest {

    private val source = FormulaEScheduleSource()

    private val fixtureHtml = """
        <html><body>
        <script type="application/ld+json">
        {"itemListElement":[
          {"item":{"name":"Sao Paulo E-Prix","startDate":"2026-01-31T18:00:00Z","location":{"name":"Sao Paulo Street Circuit"},"subEvent":[
            {"name":"Free Practice","startDate":"2026-01-31T13:00:00Z"},
            {"name":"Qualifying","startDate":"2026-01-31T15:00:00Z"},
            {"name":"Race","startDate":"2026-01-31T18:00:00Z"}
          ]}},
          {"item":{"name":"Mexico City E-Prix","startDate":"2026-02-14T20:00:00Z","location":{"name":"Autodromo Hermanos Rodriguez"}}}
        ]}
        </script>
        </body></html>
    """.trimIndent()

    @Test
    fun `una ronda con subEvent desglosa sus sesiones`() {
        val events = source.parseHtml(fixtureHtml)

        val saoPaulo = events.filter { it.fullTitle.contains("Sao Paulo") }
        assertEquals(3, saoPaulo.size)
    }

    @Test
    fun `una ronda sin subEvent todavia se guarda como una unica sesion de carrera`() {
        val events = source.parseHtml(fixtureHtml)

        val mexico = events.filter { it.fullTitle.contains("Mexico City") }
        assertEquals(1, mexico.size)
        assertTrue(mexico[0].fullTitle.endsWith("Race"))
        assertEquals(
            java.time.OffsetDateTime.parse("2026-02-14T20:00:00Z").toInstant().toEpochMilli(),
            mexico[0].startTimeUtc
        )
    }

    @Test
    fun `sin ningun JSON-LD reconocible, devuelve vacio en vez de fallar`() {
        assertEquals(emptyList<Any>(), source.parseHtml("<html><body><p>Sin datos</p></body></html>"))
    }
}
