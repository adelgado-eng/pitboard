package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsClass
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): IMSA es la fuente con más pasos encadenados (API de
 * clases -> tabla AJAX por clase -> ficha de coche) — se testean los tres tramos de parsing
 * puro por separado. El caso más importante es la detección de logo: el propio KDoc de la
 * clase documenta que "buscar TeamLogo en la URL" no funcionaba para la mayoría de equipos,
 * y que lo fiable es descartar por posición (1º = logo fijo de la serie, luego placeholder).
 */
class ImsaStandingsSourceTest {

    private val source = ImsaStandingsSource()

    private fun rowFrom(html: String) =
        Jsoup.parse(html, "https://www.imsa.com/weathertech/standings/").select("tr").first()!!

    @Test
    fun `parseClassIds resuelve el id de cada clase por su shortcode`() {
        val json = """[{"id":194,"shortcode":"GTP"},{"id":196,"shortcode":"LMP2"},{"id":192,"shortcode":"GTD PRO"},{"id":191,"shortcode":"GTD"}]"""

        val ids = source.parseClassIds(json)

        assertEquals("194", ids["GTP"])
        assertEquals("192", ids["GTD PRO"])
    }

    @Test
    fun `parseTeamRow separa numero de coche y nombre de equipo del texto de la celda`() {
        val row = rowFrom(
            """<table><tr>
                <td class="team-col"><a class="team-name" href="/racing-teams/13-autosport/">#13 13 Autosport</a></td>
                <td class="totalpoints">245</td>
            </tr></table>"""
        )

        val teamRow = source.parseTeamRow(row, StandingsClass.GTP, position = 1)!!

        assertEquals("13", teamRow.carNumber)
        assertEquals("13 Autosport", teamRow.teamName)
        assertEquals(245.0, teamRow.points, 0.0)
        assertEquals("https://www.imsa.com/racing-teams/13-autosport/", teamRow.teamUrl)
    }

    @Test
    fun `un equipo sin ficha propia todavia se parsea igual, solo sin teamUrl`() {
        val row = rowFrom(
            """<table><tr>
                <td class="team-col">#99 Equipo Nuevo</td>
                <td class="totalpoints">10</td>
            </tr></table>"""
        )

        val teamRow = source.parseTeamRow(row, StandingsClass.GTD, position = 5)!!

        assertEquals("Equipo Nuevo", teamRow.teamName)
        assertNull(teamRow.teamUrl)
    }

    private val teamPageHtml = """
        <html><body>
        <div class="team-logos">
          <img src="/logos/weathertech_championship.png">
          <img src="/logos/13autosport_logo.png">
        </div>
        <div class="imsa-card_item_widget">
          <p class="imsa-ciw-title">Ben Keating</p>
          <img class="imsa-ciw-image" src="/placeholder.gif" data-src="/photos/keating.jpg">
        </div>
        <div class="imsa-card_item_widget">
          <p class="imsa-ciw-title"></p>
          <img class="imsa-ciw-image" src="/placeholder.gif" data-src="/photos/empty.jpg">
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `descarta el logo fijo de la serie y se queda con el del equipo`() {
        val page = source.parseTeamPage(
            teamPageHtml,
            teamUrl = "https://www.imsa.com/racing-teams/13-autosport/",
            standingsClass = StandingsClass.GTD,
            carNumber = "13",
            nowUtc = 0L
        )

        assertEquals("https://www.imsa.com/logos/13autosport_logo.png", page.logoUrl)
    }

    @Test
    fun `sin logo de equipo real, ni el fijo ni el placeholder cuentan como logo`() {
        val html = """
            <html><body>
            <div class="team-logos">
              <img src="/logos/weathertech_championship.png">
              <img src="/logos/nologo_0.jpg">
            </div>
            </body></html>
        """.trimIndent()

        val page = source.parseTeamPage(html, "https://www.imsa.com/x", StandingsClass.GTD, "13", 0L)

        assertNull(page.logoUrl)
    }

    @Test
    fun `la foto de piloto sale del data-src, no del src placeholder de carga perezosa`() {
        val page = source.parseTeamPage(
            teamPageHtml,
            teamUrl = "https://www.imsa.com/racing-teams/13-autosport/",
            standingsClass = StandingsClass.GTD,
            carNumber = "13",
            nowUtc = 0L
        )

        assertEquals(1, page.drivers.size) // la tarjeta sin nombre se descarta
        assertEquals("Ben Keating", page.drivers[0].name)
        assertEquals("https://www.imsa.com/photos/keating.jpg", page.drivers[0].photoUrl)
        assertEquals("13", page.drivers[0].carNumber)
    }
}
