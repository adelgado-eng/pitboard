package com.pitboard.app.schedule.sources

import com.pitboard.app.data.SessionBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): F1 es la serie más usada y su fuente de calendario
 * (Jolpica, sucesora de Ergast) es una API JSON estable — buen primer caso para fijar el
 * formato esperado con un test de regresión. El fixture reproduce a mano la forma real de
 * la respuesta de api.jolpi.ca/ergast/f1/current.json (ver JolpicaF1ScheduleSource): un
 * fin de semana normal (ronda 1) y uno de sprint (ronda 2), que son las dos formas que el
 * parser tiene que soportar.
 */
class JolpicaF1ScheduleSourceTest {

    private val source = JolpicaF1ScheduleSource()

    private val fixtureJson = """
        {
          "MRData": {
            "RaceTable": {
              "Races": [
                {
                  "round": "1",
                  "raceName": "Bahrain Grand Prix",
                  "Circuit": { "circuitName": "Bahrain International Circuit" },
                  "date": "2026-03-08",
                  "time": "15:00:00Z",
                  "FirstPractice": { "date": "2026-03-06", "time": "11:30:00Z" },
                  "SecondPractice": { "date": "2026-03-06", "time": "15:00:00Z" },
                  "ThirdPractice": { "date": "2026-03-07", "time": "11:30:00Z" },
                  "Qualifying": { "date": "2026-03-07", "time": "15:00:00Z" }
                },
                {
                  "round": "2",
                  "raceName": "Saudi Arabian Grand Prix",
                  "Circuit": { "circuitName": "Jeddah Corniche Circuit" },
                  "date": "2026-03-15",
                  "time": "17:00:00Z",
                  "FirstPractice": { "date": "2026-03-13", "time": "13:30:00Z" },
                  "SprintQualifying": { "date": "2026-03-13", "time": "17:30:00Z" },
                  "Sprint": { "date": "2026-03-14", "time": "13:00:00Z" },
                  "Qualifying": { "date": "2026-03-14", "time": "17:00:00Z" }
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parsea un fin de semana normal y uno de sprint sin perder sesiones`() {
        val events = source.parseJson(fixtureJson)

        assertEquals(10, events.size) // 5 sesiones por ronda (ninguna carrera pierde sesiones)
    }

    @Test
    fun `un fin de semana normal trae FP1, FP2, FP3, clasificacion y carrera`() {
        val bahrain = source.parseJson(fixtureJson).filter { it.uid.startsWith("F1-R01-") }

        assertEquals(5, bahrain.size)
        assertEquals(SessionBadgeType.RACE, bahrain.first { it.uid == "F1-R01-Carrera" }.inferredBadge)
        assertEquals(SessionBadgeType.QUALY, bahrain.first { it.uid == "F1-R01-Clasificación" }.inferredBadge)
    }

    @Test
    fun `un fin de semana de sprint trae shootout y sprint en vez de FP2 y FP3`() {
        val saudi = source.parseJson(fixtureJson).filter { it.uid.startsWith("F1-R02-") }

        assertEquals(5, saudi.size)
        assertTrue(saudi.any { it.uid == "F1-R02-SprintShootout" && it.inferredBadge == SessionBadgeType.QUALY })
        assertTrue(saudi.any { it.uid == "F1-R02-Sprint" && it.inferredBadge == SessionBadgeType.SPRINT })
        assertTrue(saudi.none { it.fullTitle.contains("Libres 2") }) // sin FP2 en sprint
    }

    @Test
    fun `convierte fecha y hora UTC a epoch millis`() {
        val race = source.parseJson(fixtureJson).first { it.uid == "F1-R01-Carrera" }

        assertEquals(java.time.Instant.parse("2026-03-08T15:00:00Z").toEpochMilli(), race.startTimeUtc)
    }

    @Test
    fun `una respuesta sin carreras no falla, devuelve lista vacia`() {
        val empty = """{ "MRData": { "RaceTable": { "Races": [] } } }"""

        assertEquals(emptyList<Any>(), source.parseJson(empty))
    }
}
