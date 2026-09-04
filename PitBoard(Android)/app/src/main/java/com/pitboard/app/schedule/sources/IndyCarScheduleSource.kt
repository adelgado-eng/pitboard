package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * indycar.com/schedule es una web normal (no una SPA), sin año en la URL — se mantiene igual
 * de un año a otro. Verificado a mano el 01/09/2026: cada carrera es una tarjeta
 * ".event-card" con fecha (".event-card-header-date", ej. "Sep 6"), hora en horario del Este
 * (".event-card-header-time", ej. "2:30 PM ET"), nombre (".event-card-title") y circuito
 * (".event-card-track-name").
 *
 * HONESTO: la tarjeta solo trae la hora de la carrera principal, no los libres/clasificación
 * por separado (esos viven en la página propia de cada evento, un salto más que no hacemos
 * aquí) — así que por ahora IndyCar solo aporta la sesión de carrera. Si esto se nota a
 * faltar, el siguiente paso es seguir el enlace ".event-card-link" de cada tarjeta.
 */
class IndyCarScheduleSource : RaceScheduleSource {
    override val series: RaceSeries = RaceSeries.INDYCAR

    override suspend fun fetch(): List<EventEntity> {
        val request = Request.Builder()
            .url("https://www.indycar.com/schedule")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("indycar: HTTP ${response.code}")
            response.body?.string() ?: error("indycar: cuerpo vacío")
        }

        return parseHtml(html)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // descarte de carreras ya disputadas (clase "completed") contra un fixture HTML sin red
    // — ver IndyCarScheduleSourceTest.
    internal fun parseHtml(html: String): List<EventEntity> {
        val doc = Jsoup.parse(html)
        return doc.select("div.event-card").mapIndexedNotNull { index, card ->
            // Las tarjetas de carreras ya disputadas llevan "completed" en la clase — no nos
            // interesan (EventDao ya filtra por fecha, pero estas ni siquiera traen countdown).
            val classes = card.className()
            if ("completed" in classes) return@mapIndexedNotNull null

            val dateText = card.selectFirst(".event-card-header-date")?.text() ?: return@mapIndexedNotNull null
            val timeText = card.selectFirst(".event-card-header-time")?.text()
            val title = card.selectFirst(".event-card-title")?.text()?.trim() ?: return@mapIndexedNotNull null
            val trackName = card.selectFirst(".event-card-track-name")?.text()?.trim().orEmpty()

            val startTimeUtc = UsScheduleDateParsing.toUtcMillis(dateText, timeText) ?: return@mapIndexedNotNull null

            EventEntity(
                series = RaceSeries.INDYCAR,
                uid = "INDYCAR-$index-${title.hashCode()}",
                fullTitle = "${RaceSeries.INDYCAR.displayName} - $title - $trackName - Carrera",
                startTimeUtc = startTimeUtc,
                timeZoneId = UsScheduleDateParsing.eastZoneId(),
                inferredBadge = SessionBadgeType.RACE
            )
        }
    }
}
