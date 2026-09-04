package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.schedule.SessionBadgeMatcher
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.OffsetDateTime

/**
 * Fórmula E: se descartó Wikipedia (03/09/2026, tras un aviso del usuario de que seguía sin
 * traer carreras) — la propia fiaformulae.com resultó SÍ tener los datos, solo que en la
 * página equivocada: `/en/calendar` (no la home, que es donde se comprobó la vez anterior)
 * sirve, ya renderizado en el HTML (no hace falta ejecutar JS), un único bloque
 * `<script type="application/ld+json">` con un `ItemList` de toda la temporada — cada ronda es
 * un `SportsEvent` con `startDate`/`location`, y algunas (las más próximas, con horarios ya
 * confirmados) traen además un `subEvent` con Free Practice/Qualifying/Race por separado;
 * las rondas más lejanas en el calendario solo traen la hora de la carrera todavía.
 * Comprobado a mano contra el HTML real descargado con curl.
 *
 * No reutiliza JsonLdSportsEventScheduleSource porque esa fuente espera un listado con un
 * enlace por ronda y visita cada ronda por separado — aquí toda la temporada vive en una sola
 * página y el `SportsEvent` de cada ronda no es la raíz del JSON-LD sino que cuelga de
 * `itemListElement[].item`. Sí reutiliza sus tipos `JsonLdSportsEvent`/`JsonLdLocation`
 * (mismo paquete), que ya representan exactamente esa forma "evento con subEvent opcional".
 */
class FormulaEScheduleSource(
    private val calendarUrl: String = "https://www.fiaformulae.com/en/calendar"
) : RaceScheduleSource {
    override val series: RaceSeries = RaceSeries.FORMULA_E

    override suspend fun fetch(): List<EventEntity> {
        val html = fetchHtml(calendarUrl)
        val doc = Jsoup.parse(html, calendarUrl)
        val adapter = StandingsMoshi.instance.adapter(FormulaEItemList::class.java)

        val itemList = doc.select("script[type=application/ld+json]")
            .mapNotNull { script -> runCatching { adapter.fromJson(script.data()) }.getOrNull() }
            .firstOrNull { !it.itemListElement.isNullOrEmpty() }
            ?: return emptyList()

        return itemList.itemListElement.orEmpty().flatMapIndexed { index, listItem ->
            val event = listItem.item ?: return@flatMapIndexed emptyList<EventEntity>()
            sessionsForEvent(event, index)
        }
    }

    private fun sessionsForEvent(event: JsonLdSportsEvent, index: Int): List<EventEntity> {
        val roundName = event.name?.trim().orEmpty()
        val circuitName = event.location?.name?.trim().orEmpty()
        val subEvents = event.subEvent.orEmpty()

        if (subEvents.isEmpty()) {
            // Ronda todavía sin desglose de sesiones publicado — se usa la propia carrera
            // (fecha/hora del SportsEvent raíz) como única sesión, mejor que dejarla fuera.
            val startTimeUtc = event.startDate?.let { parseInstant(it) } ?: return emptyList()
            return listOf(
                EventEntity(
                    series = series,
                    uid = "${series.name}-$index-race",
                    fullTitle = "${series.displayName} - $roundName - $circuitName - Race",
                    startTimeUtc = startTimeUtc,
                    timeZoneId = null,
                    inferredBadge = SessionBadgeMatcher.match("Race")
                )
            )
        }

        return subEvents.mapIndexedNotNull { subIndex, sub ->
            val label = sub.name?.trim() ?: return@mapIndexedNotNull null
            val startTimeUtc = sub.startDate?.let { parseInstant(it) } ?: return@mapIndexedNotNull null
            EventEntity(
                series = series,
                uid = "${series.name}-$index-$subIndex",
                fullTitle = "${series.displayName} - $roundName - $circuitName - $label",
                startTimeUtc = startTimeUtc,
                timeZoneId = null,
                inferredBadge = SessionBadgeMatcher.match(label)
            )
        }
    }

    private fun parseInstant(iso: String): Long? =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }
    }
}

data class FormulaEItemList(val itemListElement: List<FormulaEListItem>?)
data class FormulaEListItem(val item: JsonLdSportsEvent?)
