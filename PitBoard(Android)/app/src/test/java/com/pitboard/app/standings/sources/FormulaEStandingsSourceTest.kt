package com.pitboard.app.standings.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): a diferencia del resto de fuentes, Fórmula E lee JSON
 * de la API interna de Pulselive en vez de HTML — el fixture reproduce su forma real (ver
 * KDoc de FormulaEStandingsSource, comprobada a mano con curl el 03/09/2026 según el propio
 * comentario del código).
 */
class FormulaEStandingsSourceTest {

    private val source = FormulaEStandingsSource()

    @Test
    fun `elige la temporada con status Present, no la primera de la lista`() {
        val json = """
            { "championships": [
                { "id": "11", "status": "Past" },
                { "id": "12", "status": "Present" }
            ] }
        """.trimIndent()

        assertEquals("12", source.parseChampionshipsJson(json))
    }

    @Test
    fun `sin ninguna Present, cae a la ultima de la lista`() {
        val json = """{ "championships": [ { "id": "10", "status": "Past" }, { "id": "11", "status": "Past" } ] }"""

        assertEquals("11", source.parseChampionshipsJson(json))
    }

    @Test
    fun `une nombre y apellido del piloto`() {
        val json = """
            [
              { "driverId": "d1", "driverTeamName": "Jaguar TCS Racing", "driverFirstName": "Nick", "driverLastName": "Cassidy", "driverPoints": 210 },
              { "driverId": "d2", "driverTeamName": "DS Penske", "driverFirstName": "Jean-Eric", "driverLastName": "Vergne", "driverPoints": 195 }
            ]
        """.trimIndent()

        val rows = source.parseDriverRowsJson(json)

        assertEquals("Nick Cassidy", rows[0].name)
        assertEquals("Jaguar TCS Racing", rows[0].team)
        assertEquals(210.0, rows[0].points, 0.0)
    }

    @Test
    fun `construye la url de foto de piloto con el id de la temporada actual`() {
        val rows = listOf(FormulaEStandingsSource.Row(key = "d1", name = "Nick Cassidy", team = "Jaguar TCS Racing", points = 210.0, imageId = "d1"))

        val entities = source.buildDriverEntities(rows, championshipId = "12", nowUtc = 0L)

        assertEquals(
            "https://static-files.formula-e.pulselive.com/drivers/12/right/large/d1.png",
            entities[0].photoUrl
        )
    }

    @Test
    fun `sin imageId, no hay foto en vez de una url rota`() {
        val rows = listOf(FormulaEStandingsSource.Row(key = "d1", name = "Nick Cassidy", team = "", points = 210.0, imageId = null))

        val entities = source.buildDriverEntities(rows, championshipId = "12", nowUtc = 0L)

        assertNull(entities[0].photoUrl)
    }

    @Test
    fun `se reordena por puntos y se renumera, sin fiarse de la posicion de origen`() {
        val rows = listOf(
            FormulaEStandingsSource.Row(key = "a", name = "A", team = "", points = 50.0),
            FormulaEStandingsSource.Row(key = "b", name = "B", team = "", points = 300.0)
        )

        val entities = source.buildTeamEntities(rows, championshipId = "12", nowUtc = 0L)

        assertEquals("B", entities[0].name)
        assertEquals(1, entities[0].position)
        assertEquals("A", entities[1].name)
        assertEquals(2, entities[1].position)
    }
}
