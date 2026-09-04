package com.pitboard.app.schedule.sources

import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): espn.com separa fecha/hora y nombre/circuito con
 * `<br>` dentro de la misma celda — el fixture reproduce justo esa forma (incluido el
 * espacio no separable "&nbsp;" que ESPN usa entre "Wed" y "Feb").
 */
class EspnNascarScheduleSourceTest {

    private val source = EspnNascarScheduleSource(series = RaceSeries.NASCAR_CUP, espnSeriesSlug = "nascar-premier")

    private val fixtureHtml = """
        <html><body>
        <table class="tablehead">
          <tr class="oddrow">
            <td>Wed,&nbsp;Feb&nbsp;4<br>7:30 PM ET</td>
            <td><b>Cook Out Clash</b><br>Bowman Gray Stadium</td>
          </tr>
          <tr class="evenrow">
            <td>Sun,&nbsp;Feb&nbsp;15<br>2:30 PM ET</td>
            <td><b>Daytona 500</b><br>Daytona International Speedway</td>
          </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `separa nombre de carrera y circuito por el salto de linea`() {
        val events = source.parseHtml(fixtureHtml)

        assertEquals(2, events.size)
        assertTrue(events[0].fullTitle.contains("Cook Out Clash"))
        assertTrue(events[0].fullTitle.contains("Bowman Gray Stadium"))
        assertTrue(events[1].fullTitle.contains("Daytona 500"))
    }

    @Test
    fun `todo se etiqueta como Carrera, no distingue sesiones`() {
        val events = source.parseHtml(fixtureHtml)

        assertTrue(events.all { it.inferredBadge == SessionBadgeType.RACE })
    }

    @Test
    fun `resuelve fecha y hora igual que UsScheduleDateParsing`() {
        val events = source.parseHtml(fixtureHtml)
        val expected = UsScheduleDateParsing.toUtcMillis("Feb 4", "7:30 PM ET")

        assertEquals(expected, events[0].startTimeUtc)
    }

    @Test
    fun `sin tabla reconocible, devuelve vacio en vez de fallar`() {
        assertEquals(emptyList<Any>(), source.parseHtml("<html><body><p>Sin calendario</p></body></html>"))
    }
}
