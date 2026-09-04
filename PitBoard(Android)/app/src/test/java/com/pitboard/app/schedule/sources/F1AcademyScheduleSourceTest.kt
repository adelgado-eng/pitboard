package com.pitboard.app.schedule.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): f1academy.com es una SPA en Next.js que sí incrusta la
 * temporada completa en el script `#__NEXT_DATA__` — el fixture reproduce esa forma exacta
 * (ver KDoc de la clase).
 */
class F1AcademyScheduleSourceTest {

    private val source = F1AcademyScheduleSource()

    @Test
    fun `lee las sesiones desde el bloque NEXT_DATA y prefiere el nombre corto de circuito`() {
        val html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageData":{"Races":[
              {"RoundNumber":1,"CircuitName":"Albert Park Circuit","CircuitShortName":"Melbourne","Sessions":[
                {"SessionName":"Practice 1","SessionStartTime":"2026-03-06T02:30:00Z"},
                {"SessionName":"Race 1","SessionStartTime":"2026-03-07T05:00:00Z"}
              ]}
            ]}}}}
            </script>
            </body></html>
        """.trimIndent()

        val events = source.parseHtml(html)

        assertEquals(2, events.size)
        assertTrue(events[0].fullTitle.contains("Melbourne"))
        assertEquals(java.time.OffsetDateTime.parse("2026-03-06T02:30:00Z").toInstant().toEpochMilli(), events[0].startTimeUtc)
    }

    @Test
    fun `sin nombre corto de circuito, cae al nombre completo`() {
        val html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageData":{"Races":[
              {"RoundNumber":2,"CircuitName":"Shanghai International Circuit","Sessions":[
                {"SessionName":"Race 1","SessionStartTime":"2026-03-14T05:00:00Z"}
              ]}
            ]}}}}
            </script>
            </body></html>
        """.trimIndent()

        val events = source.parseHtml(html)

        assertTrue(events[0].fullTitle.contains("Shanghai International Circuit"))
    }

    @Test
    fun `sin el bloque NEXT_DATA, devuelve vacio en vez de fallar`() {
        assertEquals(emptyList<Any>(), source.parseHtml("<html><body><p>SPA sin hidratar</p></body></html>"))
    }
}
