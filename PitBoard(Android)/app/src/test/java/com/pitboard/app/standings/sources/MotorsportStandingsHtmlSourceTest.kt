package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): MotorsportStandingsHtmlSource es la fuente compartida de
 * MotoGP (y, con otra URL, otras categorías que usan el mismo formato de tabla de
 * autosport.com). El fixture reproduce la forma que el propio código dice esperar en sus
 * comentarios: cabecera "Rider" (no "Driver", ver KDoc de la clase) y celda de nombre con un
 * único <a> — la celda no separa piloto y equipo con HTML propio, así que el equipo sale de
 * cortar el texto por el nombre de equipo conocido (parámetro knownTeamNames).
 */
class MotorsportStandingsHtmlSourceTest {

    // Instanciable directamente: es una clase abierta, no abstracta, y aquí no se llama a
    // fetch() en ningún momento — solo al parsing puro (parseTableHtml).
    private val source = MotorsportStandingsHtmlSource(
        category = StandingsCategory.MOTOGP,
        driverUrl = "unused",
        teamUrl = null
    )

    private val fixtureHtml = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Rider</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/driver/1">F. Bagnaia</a> Ducati Team</td><td>310</td></tr>
            <tr><td>2</td><td><a href="/driver/2">M. Marquez</a> Gresini Racing</td><td>295</td></tr>
            <tr><td>3</td><td><a href="/driver/3">J. Martin</a> Pramac Racing</td><td>280</td></tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    private val knownTeamNames = listOf("Ducati Team", "Gresini Racing", "Pramac Racing")

    @Test
    fun `encuentra la tabla por cabecera Rider, no solo Driver`() {
        val rows = source.parseTableHtml(fixtureHtml, knownTeamNames)

        assertEquals(3, rows.size)
    }

    @Test
    fun `separa piloto y equipo cortando por el nombre de equipo conocido`() {
        val rows = source.parseTableHtml(fixtureHtml, knownTeamNames)

        assertEquals("F. Bagnaia", rows[0].name)
        assertEquals("Ducati Team", rows[0].team)
        assertEquals(310.0, rows[0].points, 0.0)
    }

    @Test
    fun `sin nombres de equipo conocidos, el equipo se queda vacio en vez de fallar`() {
        val rows = source.parseTableHtml(fixtureHtml, knownTeamNames = emptyList())

        assertEquals("F. Bagnaia Ducati Team", rows[0].name)
        assertEquals("", rows[0].team)
    }

    @Test
    fun `localiza la columna de puntos por su cabecera, no por posicion fija`() {
        val rows = source.parseTableHtml(fixtureHtml, knownTeamNames)

        assertEquals(295.0, rows[1].points, 0.0)
        assertEquals(280.0, rows[2].points, 0.0)
    }

    @Test
    fun `una tabla sin cabecera reconocible devuelve lista vacia en vez de lanzar`() {
        val noTable = "<html><body><p>Sin resultados todavía</p></body></html>"

        assertEquals(emptyList<Any>(), source.parseTableHtml(noTable))
    }
}
