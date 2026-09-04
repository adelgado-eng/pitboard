package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): AcoCarDriversSource es la fuente de pilotos compartida
 * por WEC y Le Mans Cup (misma plantilla ACO). Se instancia aquí igual que la usa
 * WecStandingsSource en producción: con sus propios classMatchers por el badge de clase.
 */
class AcoCarDriversSourceTest {

    private val source = AcoCarDriversSource(
        category = StandingsCategory.WEC,
        listingUrl = "https://www.fiawec.com/en/page/grid",
        classMatchers = listOf(
            StandingsClass.HYPERCAR to { t: String -> t.contains("Hypercar") },
            StandingsClass.LMGT3 to { t: String -> t.contains("LMGT3") }
        )
    )

    @Test
    fun `resuelve la clase por el badge y descarta tarjetas de clase desconocida`() {
        val html = """
            <html><body>
            <div class="card-team"><span class="fs-11">Hypercar</span><a class="stretched-link" href="/en/car/2026/50"></a></div>
            <div class="card-team"><span class="fs-11">LMGT3</span><a class="stretched-link" href="/en/car/2026/33"></a></div>
            <div class="card-team"><span class="fs-11">LMP2 (no cubierta este año)</span><a class="stretched-link" href="/en/car/2026/99"></a></div>
            </body></html>
        """.trimIndent()

        val refs = source.parseCarRefs(html)

        assertEquals(2, refs.size)
        assertEquals(StandingsClass.HYPERCAR to "https://www.fiawec.com/en/car/2026/50", refs[0])
        assertEquals(StandingsClass.LMGT3 to "https://www.fiawec.com/en/car/2026/33", refs[1])
    }

    @Test
    fun `lee nombre y foto real de cada piloto, descartando tarjetas sin nombre`() {
        val html = """
            <html><body>
            <a class="card-driver"><div class="py-4">Antonio Fuoco</div><img src="/photos/fuoco.jpg"></a>
            <a class="card-driver"><div class="py-4"></div><img src="/photos/empty.jpg"></a>
            </body></html>
        """.trimIndent()

        val drivers = source.parseCarPage(
            html,
            carUrl = "https://www.fiawec.com/en/car/2026/50",
            carNumber = "50",
            standingsClass = StandingsClass.HYPERCAR,
            nowUtc = 0L
        )

        assertEquals(1, drivers.size)
        assertEquals("Antonio Fuoco", drivers[0].name)
        assertEquals("50", drivers[0].carNumber)
        assertEquals("https://www.fiawec.com/photos/fuoco.jpg", drivers[0].photoUrl)
        assertEquals(StandingsCategory.WEC, drivers[0].category)
    }
}
