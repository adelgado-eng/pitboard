package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): WEC es de las categorías con más lógica propia (agrupado
 * por número de coche, ver KDoc de WecStandingsSource) — el fixture reproduce justo el caso
 * que el propio código documenta como bug real ya corregido: dos filas de Hypercar con el
 * MISMO número de coche (dos pilotos del mismo equipo), que debe colapsar en una sola fila
 * quedándose con la de más puntos. Si una futura refactorización rompe ese agrupado, este test
 * lo detecta.
 */
class WecStandingsSourceTest {

    private val source = WecStandingsSource()

    private val fixtureHtml = """
        <html><body>
        <button data-bs-target="#results-1">FIA Hypercar World Endurance Drivers Championship</button>
        <div id="results-1">
          <table><tbody>
            <tr><td>1</td><td><img alt="Ferrari" src="/logos/ferrari.png"></td><td>#50</td><td>A. Fuoco</td><td>210</td></tr>
            <tr><td>2</td><td><img alt="Ferrari" src="/logos/ferrari.png"></td><td>#50</td><td>N. Nielsen</td><td>195</td></tr>
            <tr><td>3</td><td><img alt="Toyota" src="/logos/toyota.png"></td><td>#7</td><td>K. Kobayashi</td><td>180</td></tr>
          </tbody></table>
        </div>
        <button data-bs-target="#results-2">FIA Endurance Trophy for LMGT3 Teams</button>
        <div id="results-2">
          <table><tbody>
            <tr><td>1</td><td><img alt="Corvette" src="/logos/corvette.png"></td><td>#33</td><td>TF Sport</td><td>150</td></tr>
            <tr><td>2</td><td><img alt="BMW" src="/logos/bmw.png"></td><td>#46</td><td>Team WRT</td><td>140</td></tr>
          </tbody></table>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `dos pilotos del mismo coche colapsan en una fila con los puntos del mejor`() {
        val hypercar = source.parseHtml(fixtureHtml, nowUtc = 0L)
            .filter { it.standingsClass == StandingsClass.HYPERCAR }

        assertEquals(2, hypercar.size) // 3 filas de origen, 2 coches reales
        val car50 = hypercar.first { it.name == "#50" }
        assertEquals(210.0, car50.points, 0.0) // se queda Fuoco (210), no Nielsen (195)
        assertEquals("Ferrari", car50.team)
    }

    @Test
    fun `hypercar y lmgt3 se renumeran 1-N por separado tras el agrupado`() {
        val hypercar = source.parseHtml(fixtureHtml, nowUtc = 0L)
            .filter { it.standingsClass == StandingsClass.HYPERCAR }
            .sortedBy { it.position }

        assertEquals(listOf("#50", "#7"), hypercar.map { it.name })
        assertEquals(listOf(1, 2), hypercar.map { it.position })
    }

    @Test
    fun `LMGT3 usa el nombre de equipo real, no el fabricante`() {
        val lmgt3 = source.parseHtml(fixtureHtml, nowUtc = 0L)
            .filter { it.standingsClass == StandingsClass.LMGT3 }
            .sortedBy { it.position }

        assertEquals(listOf("TF Sport", "Team WRT"), lmgt3.map { it.team })
        assertEquals(listOf(150.0, 140.0), lmgt3.map { it.points })
    }

    @Test
    fun `todas las filas son de tipo TEAM`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals(4, rows.size)
        assertEquals(4, rows.count { it.type == StandingType.TEAM })
    }

    @Test
    fun `si la web cambia el texto del boton, esa seccion vuelve vacia en vez de fallar`() {
        val htmlSinBoton = fixtureHtml.replace(
            "FIA Hypercar World Endurance Drivers Championship",
            "Hypercar Championship (nuevo texto)"
        )

        val hypercar = source.parseHtml(htmlSinBoton, nowUtc = 0L)
            .filter { it.standingsClass == StandingsClass.HYPERCAR }

        assertEquals(0, hypercar.size)
    }
}
