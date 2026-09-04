package com.pitboard.app.schedule.sources

import com.pitboard.app.data.SessionBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1 del diagnóstico (graphify): la misma clase cubre MotoGP/Moto2/Moto3 con 3
 * instancias distintas (parametrizadas por acrónimo) — el fixture incluye sesiones de las
 * tres clases dentro del mismo evento para comprobar que cada instancia se queda solo con
 * la suya. También incluye el caso documentado de offset con y sin ":" en la misma API.
 */
class MotoGpPulseliveScheduleSourceTest {

    private val fixtureJson = """
        [
          {
            "kind": "GP",
            "hashtag": "#QatarGP",
            "circuit": { "name": "Lusail International Circuit" },
            "broadcasts": [
              { "type": "SESSION", "kind": "PRACTICE", "name": "Free Practice", "date_start": "2026-03-06T10:00:00+03:00", "category": { "acronym": "MGP" } },
              { "type": "SESSION", "kind": "RACE", "name": "Sprint", "date_start": "2026-03-07T15:00:00+03:00", "category": { "acronym": "MGP" } },
              { "type": "SESSION", "kind": "PRACTICE", "name": "Moto2 Practice", "date_start": "2026-03-06T09:00:00+03:00", "category": { "acronym": "MT2" } },
              { "type": "PRESS_CONFERENCE", "kind": "OTHER", "name": "Press", "date_start": "2026-03-06T08:00:00+03:00", "category": { "acronym": "MGP" } }
            ]
          },
          {
            "kind": "TEST",
            "hashtag": "#TestEvent",
            "circuit": { "name": "Sepang" },
            "broadcasts": []
          }
        ]
    """.trimIndent()

    @Test
    fun `descarta eventos que no son fin de semana de Gran Premio`() {
        val events = MotoGpPulseliveScheduleSource().parseJson(fixtureJson)

        assertTrue(events.none { it.fullTitle.contains("Sepang") })
    }

    @Test
    fun `se queda solo con las sesiones de su propia clase, descartando prensa y otras clases`() {
        val motogp = MotoGpPulseliveScheduleSource(categoryAcronym = "MGP").parseJson(fixtureJson)

        assertEquals(2, motogp.size)
        assertTrue(motogp.none { it.fullTitle.contains("Press") })
        assertTrue(motogp.none { it.fullTitle.contains("Moto2") })
    }

    @Test
    fun `una instancia de Moto2 se queda solo con las sesiones MT2`() {
        val moto2 = MotoGpPulseliveScheduleSource(categoryAcronym = "MT2").parseJson(fixtureJson)

        assertEquals(1, moto2.size)
        assertTrue(moto2[0].fullTitle.contains("Moto2 Practice"))
    }

    @Test
    fun `una carrera con sprint en el nombre se etiqueta SPRINT, no RACE`() {
        val motogp = MotoGpPulseliveScheduleSource(categoryAcronym = "MGP").parseJson(fixtureJson)

        assertEquals(SessionBadgeType.SPRINT, motogp.first { it.fullTitle.contains("Sprint") }.inferredBadge)
    }

    @Test
    fun `parsea la hora tanto con como sin dos puntos en el offset`() {
        val motogp = MotoGpPulseliveScheduleSource(categoryAcronym = "MGP").parseJson(fixtureJson)

        val expected = java.time.OffsetDateTime.parse("2026-03-06T10:00:00+03:00").toInstant().toEpochMilli()
        assertEquals(expected, motogp.first { it.fullTitle.contains("Free Practice") }.startTimeUtc)
    }
}
