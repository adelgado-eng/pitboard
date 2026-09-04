package com.pitboard.app.schedule.sources

import com.pitboard.app.data.RaceSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): fuente genérica reusada por F2, F3 y ELMS —
 * probarla una vez cubre el patrón común de las tres. El fixture usa la configuración real
 * de F2 (listado + exclusión de días de test).
 */
class JsonLdSportsEventScheduleSourceTest {

    private val source = JsonLdSportsEventScheduleSource(
        series = RaceSeries.F2,
        baseUrl = "https://www.fiaformula2.com",
        listingUrlTemplate = "https://www.fiaformula2.com/en/racing/{year}",
        roundHrefPrefixTemplate = "/en/racing/{year}/",
        excludeSlugContaining = listOf("test")
    )

    @Test
    fun `extractRoundUrls descarta los dias de test por el slug`() {
        val html = """
            <html><body>
            <a href="/en/racing/2026/bahrain">Bahrain</a>
            <a href="/en/racing/2026/jeddah-test">Jeddah Test</a>
            <a href="/other/page">Other</a>
            </body></html>
        """.trimIndent()

        val urls = source.extractRoundUrls(html, "/en/racing/2026/")

        assertEquals(listOf("https://www.fiaformula2.com/en/racing/2026/bahrain"), urls)
    }

    @Test
    fun `el nombre de ronda sale de la parte corta del subEvent, no del titulo completo`() {
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"name":"FORMULA 2 BAHRAIN GRAND PRIX 2026","location":{"name":"Bahrain International Circuit"},"subEvent":[
              {"name":"Practice - Bahrain Grand Prix","startDate":"2026-03-06T10:00:00Z"},
              {"name":"Qualifying - Bahrain Grand Prix","startDate":"2026-03-06T14:00:00Z"}
            ]}
            </script>
            </body></html>
        """.trimIndent()

        val events = source.parseRoundHtml(html, "https://www.fiaformula2.com/en/racing/2026/bahrain")

        assertEquals(2, events.size)
        assertTrue(events[0].fullTitle.contains("Bahrain Grand Prix"))
        assertTrue(events[0].fullTitle.contains("Bahrain International Circuit"))
        assertTrue(events[0].fullTitle.endsWith("Practice"))
        assertTrue(events[1].fullTitle.endsWith("Qualifying"))
    }

    @Test
    fun `sin JSON-LD con subEvent, devuelve vacio en vez de fallar`() {
        assertEquals(
            emptyList<Any>(),
            source.parseRoundHtml("<html><body><p>Sin datos</p></body></html>", "https://www.fiaformula2.com/en/racing/2026/bahrain")
        )
    }
}
