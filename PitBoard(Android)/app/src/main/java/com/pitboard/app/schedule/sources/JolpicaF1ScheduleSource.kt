package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import com.squareup.moshi.Json
import okhttp3.Request
import java.time.Instant

/**
 * F1 es la única serie con una API JSON pública y gratuita para el calendario completo,
 * sesión por sesión: la API de Jolpica (sucesora de Ergast), en el alias "current" — no hace
 * falta escribir el año en ningún sitio, así que sigue funcionando en 2027, 2028... sin tocar
 * código. Verificado a mano el 01/09/2026 contra la temporada 2026 real.
 */
class JolpicaF1ScheduleSource : RaceScheduleSource {
    override val series: RaceSeries = RaceSeries.F1

    override suspend fun fetch(): List<EventEntity> {
        val request = Request.Builder()
            .url("https://api.jolpi.ca/ergast/f1/current.json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val json = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("jolpica: HTTP ${response.code}")
            response.body?.string() ?: error("jolpica: cuerpo vacío")
        }

        val adapter = StandingsMoshi.instance.adapter(JolpicaResponse::class.java)
        val races = adapter.fromJson(json)?.mrData?.raceTable?.races.orEmpty()

        return races.flatMap { race -> sessionsFor(race) }
    }

    private fun sessionsFor(race: JolpicaRace): List<EventEntity> {
        val round = race.round.toIntOrNull() ?: 0
        val roundLabel = "R%02d".format(round)
        val roundName = race.raceName
        val circuitName = race.circuit?.circuitName.orEmpty()

        val sessions = buildList {
            race.firstPractice?.let { add(Triple("Libres 1", SessionBadgeType.PRACTICE, it)) }
            race.secondPractice?.let { add(Triple("Libres 2", SessionBadgeType.PRACTICE, it)) }
            race.thirdPractice?.let { add(Triple("Libres 3", SessionBadgeType.PRACTICE, it)) }
            race.sprintQualifying?.let { add(Triple("Sprint Shootout", SessionBadgeType.QUALY, it)) }
            race.sprintShootout?.let { add(Triple("Sprint Shootout", SessionBadgeType.QUALY, it)) }
            race.sprint?.let { add(Triple("Sprint", SessionBadgeType.SPRINT, it)) }
            race.qualifying?.let { add(Triple("Clasificación", SessionBadgeType.QUALY, it)) }
            if (race.date != null) {
                add(Triple("Carrera", SessionBadgeType.RACE, JolpicaSession(race.date, race.time)))
            }
        }

        return sessions.mapNotNull { (label, badge, session) ->
            val startTimeUtc = parseInstant(session) ?: return@mapNotNull null
            EventEntity(
                series = RaceSeries.F1,
                uid = "F1-$roundLabel-${label.replace(" ", "")}",
                fullTitle = "${RaceSeries.F1.displayName} - $roundName - $circuitName - $label",
                startTimeUtc = startTimeUtc,
                timeZoneId = null,
                inferredBadge = badge
            )
        }
    }

    private fun parseInstant(session: JolpicaSession): Long? {
        val date = session.date ?: return null
        val time = session.time ?: "00:00:00Z"
        val normalizedTime = if (time.endsWith("Z")) time else "${time}Z"
        return runCatching { Instant.parse("${date}T$normalizedTime").toEpochMilli() }.getOrNull()
    }
}

// Reflection pura (KotlinJsonAdapterFactory, ver StandingsMoshi) — el proyecto no aplica
// moshi-kotlin-codegen, así que @JsonClass(generateAdapter = true) no generaría nada aquí.
data class JolpicaResponse(@Json(name = "MRData") val mrData: JolpicaMrData?)

data class JolpicaMrData(@Json(name = "RaceTable") val raceTable: JolpicaRaceTable?)

data class JolpicaRaceTable(@Json(name = "Races") val races: List<JolpicaRace>?)

data class JolpicaRace(
    val round: String,
    val raceName: String,
    @Json(name = "Circuit") val circuit: JolpicaCircuit?,
    val date: String?,
    val time: String?,
    @Json(name = "FirstPractice") val firstPractice: JolpicaSession?,
    @Json(name = "SecondPractice") val secondPractice: JolpicaSession?,
    @Json(name = "ThirdPractice") val thirdPractice: JolpicaSession?,
    @Json(name = "Sprint") val sprint: JolpicaSession?,
    // Distintas temporadas han usado nombres distintos para la sesión de clasificación del
    // sprint — se aceptan los dos, solo uno vendrá presente cada vez.
    @Json(name = "SprintQualifying") val sprintQualifying: JolpicaSession?,
    @Json(name = "SprintShootout") val sprintShootout: JolpicaSession?,
    @Json(name = "Qualifying") val qualifying: JolpicaSession?
)

data class JolpicaCircuit(val circuitName: String?)

data class JolpicaSession(val date: String?, val time: String?)
