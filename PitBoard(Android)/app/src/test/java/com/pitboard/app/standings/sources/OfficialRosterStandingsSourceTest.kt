package com.pitboard.app.standings.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): OfficialRosterStandingsSource es la base compartida de
 * F1 y NASCAR Cup. Se testea a través de esas dos subclases reales (no de la base
 * directamente) porque el bug histórico más importante — el nombre de piloto pegado al
 * código de 3 letras ("Kimi AntonelliANT") — y el extractor de foto de NASCAR son
 * comportamiento real de producción, documentados en el propio código.
 */
class OfficialRosterStandingsSourceTest {

    private val f1 = F1StandingsSource()
    private val nascar = NascarStandingsSource()

    private val f1RosterHtml = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/drivers/antonelli">Kimi AntonelliANT</a></td><td>410</td></tr>
            <tr><td>2</td><td><a href="/drivers/verstappen">Max VerstappenVER</a></td><td>395</td></tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `quita el codigo de 3 letras pegado al apellido`() {
        val rows = f1.parseRosterHtml(f1RosterHtml)

        assertEquals("Kimi Antonelli", rows[0].name)
        assertEquals("Max Verstappen", rows[1].name)
    }

    @Test
    fun `lee los puntos de la tabla de autoridad`() {
        val rows = f1.parseRosterHtml(f1RosterHtml)

        assertEquals(410.0, rows[0].points, 0.0)
        assertEquals(395.0, rows[1].points, 0.0)
    }

    private val nascarRosterHtml = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a href="/racing/driver/_/id/4531/ryan-blaney">Ryan Blaney</a></td><td>2050</td></tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `saca el id de ESPN del href y construye la url del CDN de fotos`() {
        val rows = nascar.parseRosterHtml(nascarRosterHtml)

        assertEquals(
            "https://a.espncdn.com/combiner/i?img=/i/headshots/rpm/players/full/4531.png&w=500&h=500",
            rows[0].photoUrl
        )
    }

    private val driverDbHtml = """
        <html><body>
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Team</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a>Jimmie Johnson</a></td><td>—</td></tr>
            <tr><td>2</td><td><a>B.J. McLeod</a><img src="/default/driver-profile.png"></td><td>Live Fast Motorsports</td></tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `el guion largo en la celda de equipo no cuenta como nombre de equipo`() {
        val enrichment = f1.parseDriverDbHtml(driverDbHtml, "https://www.driverdb.com/x")

        assertEquals("", enrichment["Jimmie Johnson"]?.team)
    }

    @Test
    fun `la foto placeholder de driverdb no se guarda como foto real`() {
        val enrichment = f1.parseDriverDbHtml(driverDbHtml, "https://www.driverdb.com/x")

        assertNull(enrichment["B.J. McLeod"]?.photoUrl)
        assertEquals("Live Fast Motorsports", enrichment["B.J. McLeod"]?.team)
    }
}
