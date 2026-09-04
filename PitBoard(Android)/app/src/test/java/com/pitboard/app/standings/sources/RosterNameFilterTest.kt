package com.pitboard.app.standings.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): RosterNameFilter la usan MotoGP, Moto2, Moto3 y F1
 * Academy para excluir pilotos reserva/wildcard. `isInRoster` y `filterKeepingReal` ya eran
 * puras (sin red); `parseNames` es la única función que hacía falta separar.
 */
class RosterNameFilterTest {

    @Test
    fun `parseNames reconoce nombres propios en titulos, enlaces y negritas`() {
        val html = """
            <html><body>
            <h2>2026 MotoGP Rider Line-Ups</h2>
            <ul>
              <li>Jorge Martin</li>
              <li><a>Marc Marquez</a></li>
              <li><strong>Fabio Di Giannantonio</strong></li>
              <li>Menú de navegación</li>
            </ul>
            </body></html>
        """.trimIndent()

        val names = RosterNameFilter.parseNames(html, "https://example.com")

        assertTrue(names.contains("Jorge Martin"))
        assertTrue(names.contains("Marc Marquez"))
        assertTrue(names.contains("Fabio Di Giannantonio"))
        // "de" no empieza en mayúscula, así que no cumple la heurística de "nombre propio"
        assertFalse(names.contains("Menú de navegación"))
    }

    @Test
    fun `sin ningun nombre reconocible, devuelve vacio en vez de fallar`() {
        val html = "<html><body><p>contenido en minúsculas, sin nombres</p></body></html>"

        assertEquals(emptySet<String>(), RosterNameFilter.parseNames(html, "https://example.com"))
    }

    @Test
    fun `isInRoster compara tambien por apellido cuando el nombre viene abreviado`() {
        // autosport.com abrevia a "J. Martin"; la pagina de referencia trae "Jorge Martin"
        assertTrue(RosterNameFilter.isInRoster("J. Martin", setOf("Jorge Martin", "Marc Marquez")))
    }

    @Test
    fun `isInRoster con lista vacia no filtra a nadie`() {
        assertTrue(RosterNameFilter.isInRoster("Cualquier Piloto", emptySet()))
    }

    @Test
    fun `filterKeepingReal no vacia la lista si el filtro no reconoce a nadie`() {
        val rows = listOf("Piloto A", "Piloto B")
        val knownNamesQueNoCoincidenConNadie = setOf("Nombre Totalmente Distinto")

        val result = RosterNameFilter.filterKeepingReal(rows, knownNamesQueNoCoincidenConNadie) { it }

        assertEquals(rows, result) // se queda con todos antes que vaciar la categoría
    }

    @Test
    fun `filterKeepingReal si filtra pero deja al menos uno, se queda solo con esos`() {
        val rows = listOf("Jorge Martin", "Piloto Reserva Desconocido")

        val result = RosterNameFilter.filterKeepingReal(rows, setOf("Jorge Martin")) { it }

        assertEquals(listOf("Jorge Martin"), result)
    }
}
