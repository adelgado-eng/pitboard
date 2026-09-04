package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): ElmsStandingsSource es la fuente con más lógica propia
 * documentada como corregida a mano (ver su KDoc): el orden de las tablas en el HTML no
 * coincide con el de las pestañas visuales, la cabecera mezcla `<th>`/`<td>`, y "Total
 * points" no está en la posición de su propio encabezado. Los números de coche del fixture
 * son reales (los mismos que ya vive OFFICIAL_TEAM_BY_CAR en producción), así que el test
 * también protege que esa tabla siga bien enlazada con el nombre oficial de cada equipo.
 */
class ElmsStandingsSourceTest {

    private val source = ElmsStandingsSource()

    // Cabecera mixta th/td (el bug real del 30/08/2026 (4)): "N°" es un <td>, no un <th>.
    private fun teamsTable(rows: String) = """
        <table>
          <thead><tr><th>Pos.</th><td>N°</td><th>Team</th><th>Race pts</th><th>Total points</th></tr></thead>
          <tbody>$rows</tbody>
        </table>
    """.trimIndent()

    @Test
    fun `empareja titulo y tabla por posicion y usa el nombre oficial del equipo`() {
        val html = """
            <html><body>
            <div>LMP2 Teams Classification</div>
            ${teamsTable("""
                <tr><td>1</td><td>#18</td><td>IDEC SPORT</td><td>10</td><td>65</td></tr>
                <tr><td>2</td><td>#9</td><td>PROTON COMPETITION</td><td>8</td><td>58</td></tr>
            """)}
            <div>LMGT3 Teams Classification</div>
            ${teamsTable("""<tr><td>1</td><td>#33</td><td>TF SPORT</td><td>12</td><td>70</td></tr>""")}
            </body></html>
        """.trimIndent()

        val rows = source.parseHtml(html, nowUtc = 0L)

        assertEquals(3, rows.size)
        val lmp2First = rows.first { it.standingsClass == StandingsClass.LMP2 && it.position == 1 }
        assertEquals("IDEC Sport", lmp2First.team) // grafía oficial, no "IDEC SPORT" tal cual
        assertEquals(65.0, lmp2First.points, 0.0) // la última celda, no la columna "Race pts"
        val lmgt3 = rows.first { it.standingsClass == StandingsClass.LMGT3 }
        assertEquals("TF Sport", lmgt3.team)
    }

    @Test
    fun `si el numero de titulos no coincide con el de tablas, identifica la clase por los numeros de coche`() {
        // Sin ningún título de clase — solo el plan B (pairByCarNumbers) puede etiquetarlas.
        val html = """
            <html><body>
            ${teamsTable("""
                <tr><td>1</td><td>#18</td><td>IDEC SPORT</td><td>10</td><td>65</td></tr>
                <tr><td>2</td><td>#9</td><td>PROTON COMPETITION</td><td>8</td><td>58</td></tr>
            """)}
            ${teamsTable("""<tr><td>1</td><td>#33</td><td>TF SPORT</td><td>12</td><td>70</td></tr>""")}
            </body></html>
        """.trimIndent()

        val rows = source.parseHtml(html, nowUtc = 0L)

        assertEquals(2, rows.count { it.standingsClass == StandingsClass.LMP2 })
        assertEquals(1, rows.count { it.standingsClass == StandingsClass.LMGT3 })
    }

    @Test
    fun `sin ninguna tabla de equipos reconocible, devuelve vacio en vez de fallar`() {
        val html = "<html><body><table><thead><tr><th>Drivers</th></tr></thead></table></body></html>"

        assertEquals(emptyList<Any>(), source.parseHtml(html, nowUtc = 0L))
    }
}
