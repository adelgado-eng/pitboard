package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): Le Mans Cup se reescribió el 03/09/2026 tras
 * comprobar que su tabla NO trae columna de logo (a diferencia de WEC, misma
 * organización/plantilla) — el logo se cruza aparte por número de coche desde otra página.
 * El fixture reproduce justo esa forma de fila de 5 columnas sin logo, y el cruce con y sin
 * coincidencia en el mapa de logos.
 */
class LeMansCupStandingsSourceTest {

    private val source = LeMansCupStandingsSource()

    private fun section(id: String, buttonText: String, rowsHtml: String) = """
        <button data-bs-target="#$id">$buttonText</button>
        <div id="$id"><table><tbody>$rowsHtml</tbody></table></div>
    """.trimIndent()

    private val classificationHtml = """
        <html><body>
        ${section("s1", "LMP3 Pro/Am Teams Classification", """<tr><td>1</td><td>#7</td><td>TEAM VIRAGE</td><td>10</td><td>55</td></tr>""")}
        ${section("s2", "LMP3 Teams Classification", """<tr><td>1</td><td>#85</td><td>R-ACE GP</td><td>12</td><td>60</td></tr>""")}
        ${section("s3", "GT3 Teams Classification", """<tr><td>1</td><td>#33</td><td>TF SPORT</td><td>15</td><td>70</td></tr>""")}
        </body></html>
    """.trimIndent()

    @Test
    fun `lee las 3 clases de Le Mans Cup, sin columna de logo en la propia tabla`() {
        val rows = source.parseClassificationHtml(classificationHtml, logoByCarNumber = emptyMap(), nowUtc = 0L)

        assertEquals(3, rows.size)
        assertEquals("R-ACE GP", rows.first { it.standingsClass == StandingsClass.LMP3 }.team)
        assertEquals(60.0, rows.first { it.standingsClass == StandingsClass.LMP3 }.points, 0.0)
        assertEquals("TEAM VIRAGE", rows.first { it.standingsClass == StandingsClass.LMP3_PRO_AM }.team)
        assertEquals("TF SPORT", rows.first { it.standingsClass == StandingsClass.GT3 }.team)
    }

    @Test
    fun `cruza el logo por numero de coche cuando esta en el mapa`() {
        val rows = source.parseClassificationHtml(
            classificationHtml,
            logoByCarNumber = mapOf("85" to "https://www.lemanscup.com/logos/race-gp.png"),
            nowUtc = 0L
        )

        assertEquals("https://www.lemanscup.com/logos/race-gp.png", rows.first { it.standingsClass == StandingsClass.LMP3 }.photoUrl)
        assertNull(rows.first { it.standingsClass == StandingsClass.LMP3_PRO_AM }.photoUrl)
    }

    @Test
    fun `parseLogosByCarNumber saca el numero de coche de la url de la ficha, no de texto`() {
        val html = """
            <html><body>
            <div class="card-team">
              <a class="stretched-link" href="/en/car/2026/85"></a>
              <div class="brand-logo"><img src="/logos/race-gp.png"></div>
            </div>
            <div class="card-team">
              <a class="stretched-link" href="/en/car/2026/33"></a>
            </div>
            </body></html>
        """.trimIndent()

        val logos = source.parseLogosByCarNumber(html)

        assertEquals(mapOf("85" to "https://www.lemanscup.com/logos/race-gp.png"), logos)
    }
}
