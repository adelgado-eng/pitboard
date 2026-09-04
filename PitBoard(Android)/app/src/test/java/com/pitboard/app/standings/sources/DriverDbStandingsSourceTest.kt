package com.pitboard.app.standings.sources

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): DriverDbStandingsSource es la base compartida de F1
 * Academy, F2, F3 y Porsche Supercup (4 fuentes reales) — probarla una vez cubre el parsing
 * común de las cuatro. Se instancia a través de PorscheSupercupStandingsSource (una subclase
 * real, sin parámetros propios) porque parseDriverRows() es un método heredado, no estático.
 */
class DriverDbStandingsSourceTest {

    private val source = PorscheSupercupStandingsSource()

    private fun tableFrom(html: String) = Jsoup.parse(html).select("table").first()!!

    private val fixtureTable = tableFrom(
        """
        <table>
          <thead><tr><th>Pos</th><th>Driver</th><th>Team</th><th>Points</th></tr></thead>
          <tbody>
            <tr><td>1</td><td><a>Bastian Buus</a></td><td>Schumacher CLRT</td><td>320</td></tr>
            <tr><td>2</td><td><a>Julien Andlauer</a><img src="https://www.driverdb.com/_next/image?url=%2Fdefault%2Fdriver-profile.png&w=128"></td><td>—</td><td>295</td></tr>
          </tbody>
        </table>
        """.trimIndent()
    )

    @Test
    fun `lee nombre, equipo y puntos de la fila`() {
        val rows = source.parseDriverRows(fixtureTable, nowUtc = 0L)

        assertEquals(2, rows.size)
        assertEquals("Bastian Buus", rows[0].name)
        assertEquals("Schumacher CLRT", rows[0].team)
        assertEquals(320.0, rows[0].points, 0.0)
        assertEquals(1, rows[0].position)
    }

    @Test
    fun `la foto placeholder codificada de driverdb no se guarda como foto real`() {
        val rows = source.parseDriverRows(fixtureTable, nowUtc = 0L)

        assertNull(rows[1].photoUrl)
    }

    @Test
    fun `el guion largo en la celda de equipo se trata como sin equipo`() {
        val rows = source.parseDriverRows(fixtureTable, nowUtc = 0L)

        assertEquals("", rows[1].team)
    }

    @Test
    fun `sin columna de puntos reconocible, no falla, devuelve lista vacia`() {
        val sinPuntos = tableFrom(
            """
            <table>
              <thead><tr><th>Pos</th><th>Driver</th></tr></thead>
              <tbody><tr><td>1</td><td><a>Piloto</a></td></tr></tbody>
            </table>
            """.trimIndent()
        )

        assertEquals(emptyList<Any>(), source.parseDriverRows(sinPuntos, nowUtc = 0L))
    }
}
