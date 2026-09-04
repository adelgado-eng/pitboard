package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import com.squareup.moshi.Json
import com.squareup.moshi.Types
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.Year
import java.time.format.DateTimeFormatter

/**
 * motogp.com es una aplicación en JavaScript sin datos en el HTML servido, pero por debajo usa
 * una API pública propia (api.pulselive.motogp.com) — verificada a mano el 02/09/2026. Cada fin
 * de semana real de Gran Premio viene marcado con `kind: "GP"` (para distinguirlo de tests y
 * presentaciones de equipo) y trae dentro un array `broadcasts` con TODAS las sesiones de las 3
 * clases (MotoGP/Moto2/Moto3) — se filtra por `category.acronym` y `type == "SESSION"` (lo demás
 * son ruedas de prensa, "Group Photo", etc.) para quedarnos solo con las de una clase.
 *
 * 02/09/2026: generalizada para las 3 clases en vez de estar fija a MotoGP — comprobados los
 * acrónimos reales en la API ("MGP" MotoGP, "MT2" Moto2, "MT3" Moto3). Las 3 comparten esta
 * misma clase con 3 instancias distintas (ver RaceScheduleRepository).
 */
class MotoGpPulseliveScheduleSource(
    override val series: RaceSeries = RaceSeries.MOTOGP,
    private val categoryAcronym: String = "MGP"
) : RaceScheduleSource {

    override suspend fun fetch(): List<EventEntity> {
        val year = Year.now().value
        val url = "https://api.pulselive.motogp.com/motogp/v1/events?seasonYear=$year"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val json = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        val listType = Types.newParameterizedType(List::class.java, PulseliveEvent::class.java)
        val adapter = StandingsMoshi.instance.adapter<List<PulseliveEvent>>(listType)
        val events = adapter.fromJson(json).orEmpty()

        return events
            .filter { it.kind == "GP" }
            .flatMap { event -> sessionsForEvent(event) }
    }

    private fun sessionsForEvent(event: PulseliveEvent): List<EventEntity> {
        val circuitName = event.circuit?.name?.trim().orEmpty()
        val eventKey = event.hashtag?.removePrefix("#")?.takeIf { it.isNotBlank() } ?: circuitName

        return event.broadcasts.orEmpty()
            .filter { it.type == "SESSION" && it.category?.acronym == categoryAcronym }
            .mapIndexedNotNull { index, broadcast ->
                val startTimeUtc = broadcast.dateStart?.let { parseInstant(it) } ?: return@mapIndexedNotNull null
                val label = sessionLabel(broadcast)
                val badge = badgeFor(broadcast)

                EventEntity(
                    series = series,
                    uid = "${series.name}-$eventKey-$index",
                    fullTitle = "${series.displayName} - $circuitName - $label",
                    startTimeUtc = startTimeUtc,
                    timeZoneId = null,
                    inferredBadge = badge
                )
            }
    }

    private fun sessionLabel(broadcast: PulseliveBroadcast): String =
        broadcast.name?.trim()?.takeIf { it.isNotBlank() } ?: broadcast.shortname.orEmpty()

    private fun badgeFor(broadcast: PulseliveBroadcast): String {
        val name = broadcast.name.orEmpty().lowercase()
        return when (broadcast.kind) {
            "PRACTICE" -> SessionBadgeType.PRACTICE
            "WARM_UP" -> SessionBadgeType.PRACTICE
            "QUALIFYING" -> SessionBadgeType.QUALY
            "RACE" -> if ("sprint" in name) SessionBadgeType.SPRINT else SessionBadgeType.RACE
            else -> SessionBadgeType.OTHER
        }
    }

    // La API mezcla "-0300" (sin dos puntos) y "-03:00" (con dos puntos) para el mismo campo
    // según la sesión — comprobado a mano el 02/09/2026 — así que se prueban ambos patrones.
    private val instantFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    )

    private fun parseInstant(raw: String): Long? =
        instantFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { OffsetDateTime.parse(raw, formatter).toInstant().toEpochMilli() }.getOrNull()
        }
}

data class PulseliveEvent(
    val kind: String?,
    val hashtag: String?,
    val circuit: PulseliveCircuit?,
    val broadcasts: List<PulseliveBroadcast>?
)

data class PulseliveCircuit(val name: String?)

data class PulseliveBroadcast(
    val type: String?,
    val kind: String?,
    val shortname: String?,
    val name: String?,
    @Json(name = "date_start") val dateStart: String?,
    val category: PulseliveCategory?
)

data class PulseliveCategory(val acronym: String?)
