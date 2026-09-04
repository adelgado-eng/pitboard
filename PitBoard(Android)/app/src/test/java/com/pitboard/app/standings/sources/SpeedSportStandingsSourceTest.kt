package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): SpeedSportStandingsSource localiza columnas por su
 * encabezado (no por índice fijo) porque el propio KDoc admite no haber podido inspeccionar
 * el HTML byte a byte — el fixture incluye el sufijo "N win(s)" pegado al nombre que el
 * código recorta explícitamente.
 */
class SpeedSportStandingsSourceTest {

    private val source = SpeedSportStandingsSource(category = StandingsCategory.F1, pointsUrl = "unused")

    private fun tableFrom(html: String) =
        Jsoup.parse(html, "https://speedsport-magazine.com/x").select("table").first()!!

    @Test
    fun `quita el sufijo de victorias pegado al nombre del piloto`() {
        val table = tableFrom(
            """
            <table>
              <thead><tr><th>Pos</th><th>Photo</th><th>Driver</th><th>Points</th></tr></thead>
              <tbody>
                <tr><td>1</td><td><img src="/photos/verstappen.jpg"></td><td>Max Verstappen 5 wins</td><td>410</td></tr>
                <tr><td>2</td><td><img src="/photos/norris.jpg"></td><td>Lando Norris 1 win</td><td>395</td></tr>
              </tbody>
            </table>
            """.trimIndent()
        )

        val rows = source.parseStandingsTable(table, StandingType.DRIVER, nowUtc = 0L)

        assertEquals("Max Verstappen", rows[0].name)
        assertEquals("Lando Norris", rows[1].name)
        assertEquals(410.0, rows[0].points, 0.0)
    }

    @Test
    fun `resuelve la foto de la columna Photo a una url absoluta`() {
        val table = tableFrom(
            """
            <table>
              <thead><tr><th>Pos</th><th>Photo</th><th>Driver</th><th>Points</th></tr></thead>
              <tbody><tr><td>1</td><td><img src="/photos/verstappen.jpg"></td><td>Max Verstappen 5 wins</td><td>410</td></tr></tbody>
            </table>
            """.trimIndent()
        )

        val rows = source.parseStandingsTable(table, StandingType.DRIVER, nowUtc = 0L)

        assertEquals("https://speedsport-magazine.com/photos/verstappen.jpg", rows[0].photoUrl)
    }

    @Test
    fun `las filas de piloto se dejan sin equipo, es una decision consciente`() {
        val table = tableFrom(
            """
            <table>
              <thead><tr><th>Pos</th><th>Driver</th><th>Points</th></tr></thead>
              <tbody><tr><td>1</td><td>Max Verstappen</td><td>410</td></tr></tbody>
            </table>
            """.trimIndent()
        )

        val rows = source.parseStandingsTable(table, StandingType.DRIVER, nowUtc = 0L)

        assertEquals("", rows[0].team)
    }

    @Test
    fun `sin columna Photo, no hay foto en vez de fallar`() {
        val table = tableFrom(
            """
            <table>
              <thead><tr><th>Pos</th><th>Team</th><th>Points</th></tr></thead>
              <tbody><tr><td>1</td><td>Red Bull Racing</td><td>620</td></tr></tbody>
            </table>
            """.trimIndent()
        )

        val rows = source.parseStandingsTable(table, StandingType.TEAM, nowUtc = 0L)

        assertEquals("Red Bull Racing", rows[0].name)
        assertEquals(620.0, rows[0].points, 0.0)
        assertNull(rows[0].photoUrl)
    }
}
