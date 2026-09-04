package com.pitboard.app.schedule.sources

import com.pitboard.app.data.RaceSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fase 1 del diagnóstico (graphify): las 4 variantes de GT World Challenge comparten esta
 * misma clase, parametrizada por serie y dominio — el fixture reproduce la sección
 * "Timetable" real (día en el `<caption>`, columna GMT usada directamente como UTC).
 */
class GtWorldChallengeScheduleSourceTest {

    private val source = GtWorldChallengeScheduleSource(
        series = RaceSeries.GT_CHALLENGE_EUROPE,
        baseUrl = "https://www.gt-world-challenge-europe.com"
    )

    @Test
    fun `extractItemListUrls saca la url de cada evento del JSON-LD`() {
        val html = """
            <html><body>
            <script type="application/ld+json">
            {"itemListElement":[
              {"url":"https://www.gt-world-challenge-europe.com/events/spa-24-hours"},
              {"url":"https://www.gt-world-challenge-europe.com/events/monza"}
            ]}
            </script>
            </body></html>
        """.trimIndent()

        val urls = source.extractItemListUrls(html)

        assertEquals(
            listOf(
                "https://www.gt-world-challenge-europe.com/events/spa-24-hours",
                "https://www.gt-world-challenge-europe.com/events/monza"
            ),
            urls
        )
    }

    @Test
    fun `una celda de sesion vacia se etiqueta Sesion N en vez de perderse`() {
        val year = Year.now().value
        val html = """
            <html><body>
            <h1>Spa 24 Hours</h1>
            <table class="timetable__table">
              <caption class="timetable__caption"><span>Friday, 18 September</span></caption>
              <thead><tr><th>Session</th><th>Local</th><th>GMT</th></tr></thead>
              <tbody>
                <tr><td>Free Practice 1</td><td>10:00</td><td>8:00</td></tr>
                <tr><td></td><td>14:00</td><td>12:00</td></tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val events = source.parseEventHtml(html, "https://www.gt-world-challenge-europe.com/events/spa-24-hours")

        assertEquals(2, events.size)
        assertEquals(true, events[0].fullTitle.contains("Free Practice 1"))
        assertEquals(true, events[1].fullTitle.contains("Sesión 2"))

        val date = LocalDate.parse("18 September $year", DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        val expected = LocalDateTime.of(date, LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, events[0].startTimeUtc)
    }

    @Test
    fun `la hora GMT de la tabla se usa directamente como UTC, sin convertir zona`() {
        val year = Year.now().value
        val html = """
            <html><body>
            <h1>Monza</h1>
            <table class="timetable__table">
              <caption class="timetable__caption"><span>Saturday, 20 June</span></caption>
              <thead><tr><th>Session</th><th>GMT</th></tr></thead>
              <tbody><tr><td>Qualifying</td><td>15:30</td></tr></tbody>
            </table>
            </body></html>
        """.trimIndent()

        val events = source.parseEventHtml(html, "https://www.gt-world-challenge-europe.com/events/monza")

        val date = LocalDate.parse("20 June $year", DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        val expected = LocalDateTime.of(date, LocalTime.of(15, 30)).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, events[0].startTimeUtc)
    }
}
