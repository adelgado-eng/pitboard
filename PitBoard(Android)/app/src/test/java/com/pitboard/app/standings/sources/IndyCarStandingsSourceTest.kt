package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): indycar.com/Standings trae la celda de piloto con
 * varias imágenes a la vez (foto + endplate del coche) y el equipo a veces solo como logo
 * (sin texto) — el fixture reproduce ambos casos documentados en el propio KDoc de la clase.
 */
class IndyCarStandingsSourceTest {

    private val source = IndyCarStandingsSource()

    private val fixtureHtml = """
        <html><body>
        <table>
          <thead><tr><th>Rank</th><th>Driver</th><th>Team</th><th>Points</th></tr></thead>
          <tbody>
            <tr>
              <td>1</td>
              <td><a>Alex Palou</a><img src="/-/media/IndyCar/Headshot/palou.png?w=80"><img src="/-/media/IndyCar/Endplate/palou.png"></td>
              <td>Chip Ganassi Racing</td>
              <td>589</td>
            </tr>
            <tr>
              <td>2</td>
              <td><a>Pato O'Ward</a><img src="/-/media/IndyCar/Headshot/oward.png?w=80"></td>
              <td><img alt="Arrow McLaren Logo " src="/-/media/IndyCar/Team/ArrowMcLaren.png"></td>
              <td>560</td>
            </tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `coge la foto Headshot, no el endplate del coche, y quita el parametro de ancho`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        val palou = rows.first { it.name == "Alex Palou" }
        assertEquals("https://www.indycar.com/-/media/IndyCar/Headshot/palou.png", palou.photoUrl)
    }

    @Test
    fun `sin texto de equipo, cae al alt del logo sin la palabra Logo`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals("Arrow McLaren", rows.first { it.name == "Pato O'Ward" }.team)
    }

    @Test
    fun `agrupa pilotos por equipo para la clasificacion de equipos`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        val teams = rows.filter { it.type == StandingType.TEAM }.sortedBy { it.position }
        assertEquals(listOf("Chip Ganassi Racing", "Arrow McLaren"), teams.map { it.name })
        assertEquals(589.0, teams[0].points, 0.0)
    }

    @Test
    fun `un total de 2 pilotos y 2 equipos`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals(2, rows.count { it.type == StandingType.DRIVER })
        assertEquals(2, rows.count { it.type == StandingType.TEAM })
        assertTrue(rows.none { it.name.isBlank() })
    }
}
