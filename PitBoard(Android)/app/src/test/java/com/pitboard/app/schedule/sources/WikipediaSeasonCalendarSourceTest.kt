package com.pitboard.app.schedule.sources

import com.pitboard.app.data.RaceSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fase 1 del diagnóstico (graphify): esta es la fuente con más bugs reales ya documentados
 * en su propio KDoc — los dos fixtures principales reproducen exactamente los dos casos que
 * dejaban a Fórmula E con CERO carreras el 03/09/2026: la fecha que ya trae el año (se
 * duplicaba: "18 December 2026 2026", ilegible) y el `rowspan` de dos rondas que comparten
 * circuito (desalineaba las columnas de la segunda fila).
 */
class WikipediaSeasonCalendarSourceTest {

    private val source = WikipediaSeasonCalendarSource(series = RaceSeries.FORMULA_E, explicitArticleTitle = "x")
    private val porscheSource = WikipediaSeasonCalendarSource(series = RaceSeries.PORSCHE_SUPERCUP, explicitArticleTitle = "x")

    private fun noonUtc(date: LocalDate) = date.atStartOfDay(ZoneId.of("UTC")).plusHours(12).toInstant().toEpochMilli()

    @Test
    fun `una fecha que ya trae el año no se duplica y sigue siendo legible`() {
        val html = """
            <table class="wikitable">
              <tr><th>Round</th><th>E-Prix</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>10</td><td>London E-Prix</td><td>ExCeL London</td><td>18 December 2026</td></tr>
            </table>
        """.trimIndent()

        val events = source.parseHtml(html, year = 2026)

        assertEquals(1, events.size) // antes del fix, esta fila producía CERO eventos
        assertEquals(noonUtc(LocalDate.of(2026, 12, 18)), events[0].startTimeUtc)
    }

    @Test
    fun `dos rondas que comparten circuito por rowspan no pierden el nombre en la segunda fila`() {
        val html = """
            <table class="wikitable">
              <tr><th>Round</th><th>E-Prix</th><th>Circuit</th><th>Date</th></tr>
              <tr>
                <td rowspan="2">1</td>
                <td rowspan="2">Sao Paulo E-Prix</td>
                <td rowspan="2">Sao Paulo Street Circuit</td>
                <td>31 January 2026</td>
              </tr>
              <tr><td>1 February 2026</td></tr>
            </table>
        """.trimIndent()

        val events = source.parseHtml(html, year = 2026)

        assertEquals(2, events.size)
        assertTrue(events[1].fullTitle.contains("Sao Paulo E-Prix"))
        assertTrue(events[1].fullTitle.contains("Sao Paulo Street Circuit"))
        assertEquals(noonUtc(LocalDate.of(2026, 1, 31)), events[0].startTimeUtc)
        assertEquals(noonUtc(LocalDate.of(2026, 2, 1)), events[1].startTimeUtc)
    }

    @Test
    fun `sin año en la fecha de origen, se le añade el año pasado como parametro`() {
        val html = """
            <table class="wikitable">
              <tr><th>Rnd</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>1</td><td>Imola</td><td>6 April</td></tr>
            </table>
        """.trimIndent()

        val events = porscheSource.parseHtml(html, year = 2026)

        assertEquals(noonUtc(LocalDate.of(2026, 4, 6)), events[0].startTimeUtc)
        assertTrue(events[0].fullTitle.contains("Ronda 1")) // sin columna de nombre de carrera
    }

    @Test
    fun `un rango de fechas se queda con el segundo dia`() {
        val html = """
            <table class="wikitable">
              <tr><th>Rnd</th><th>Circuit</th><th>Date</th></tr>
              <tr><td>1</td><td>Daytona</td><td>January 24-25</td></tr>
            </table>
        """.trimIndent()

        val events = porscheSource.parseHtml(html, year = 2026)

        assertEquals(noonUtc(LocalDate.of(2026, 1, 25)), events[0].startTimeUtc)
    }

    @Test
    fun `sin ninguna tabla wikitable con fecha y circuito, devuelve vacio`() {
        assertEquals(emptyList<Any>(), source.parseHtml("<table><tr><th>Nada</th></tr></table>", year = 2026))
    }
}
