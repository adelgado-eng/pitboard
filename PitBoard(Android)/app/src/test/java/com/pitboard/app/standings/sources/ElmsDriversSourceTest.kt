package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): ElmsDriversSource asocia cada piloto a un coche
 * recordando el último `<h2>` de clase visto mientras recorre el documento en orden — el
 * fixture reproduce dos clases seguidas para comprobar que el piloto de la segunda tarjeta
 * no hereda la clase de la primera.
 */
class ElmsDriversSourceTest {

    private val source = ElmsDriversSource()

    private val fixtureHtml = """
        <html><body>
        <h2 class="h3 text-center">LMP2</h2>
        <div class="card-driver">
          <div class="driver-thumb"><img src="/photos/chatin.jpg"></div>
          <div class="driver-name">Paul-Loup Chatin</div>
          <div class="driver-team">IDEC SPORT #18</div>
        </div>
        <h2 class="h3 text-center">LMGT3</h2>
        <div class="card-driver">
          <div class="driver-thumb"><img src="/photos/keating.jpg"></div>
          <div class="driver-name">Ben Keating</div>
          <div class="driver-team">TF SPORT #33</div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `cada piloto se asocia a la clase de su seccion, no a la anterior`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals(2, rows.size)
        assertEquals(StandingsClass.LMP2, rows.first { it.name == "Paul-Loup Chatin" }.standingsClass)
        assertEquals(StandingsClass.LMGT3, rows.first { it.name == "Ben Keating" }.standingsClass)
    }

    @Test
    fun `extrae el numero de coche del final del texto de equipo`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals("18", rows.first { it.name == "Paul-Loup Chatin" }.carNumber)
        assertEquals("33", rows.first { it.name == "Ben Keating" }.carNumber)
    }

    @Test
    fun `resuelve la foto a una url absoluta`() {
        val rows = source.parseHtml(fixtureHtml, nowUtc = 0L)

        assertEquals(
            "https://www.europeanlemansseries.com/photos/chatin.jpg",
            rows.first { it.name == "Paul-Loup Chatin" }.photoUrl
        )
    }

    @Test
    fun `una tarjeta sin numero de coche se descarta en vez de asociarse mal`() {
        val html = """
            <html><body>
            <h2 class="h3 text-center">LMP2</h2>
            <div class="card-driver">
              <div class="driver-name">Piloto Sin Coche</div>
              <div class="driver-team">EQUIPO SIN NUMERO</div>
            </div>
            </body></html>
        """.trimIndent()

        assertEquals(emptyList<Any>(), source.parseHtml(html, nowUtc = 0L))
    }
}
