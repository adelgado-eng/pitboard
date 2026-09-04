package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.schedule.SessionBadgeMatcher
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Las 4 variantes de GT World Challenge (Europa/América/Asia/Australia) las organiza el mismo
 * promotor (SRO Motorsports Group) y comparten literalmente la misma web, solo con el dominio
 * cambiado por región — verificado a mano el 02/09/2026 contra las 4.
 *
 * 1. `{baseUrl}/calendar` trae un JSON-LD `ItemList` con la URL de cada evento de la
 *    temporada actual (no hace falta año en la URL, la propia web solo lista lo que viene).
 * 2. Cada evento tiene una sección "Timetable" ya en HTML normal (sin JS): una tabla por día,
 *    con el día en el `<caption>` ("Friday, 18 September") y filas Sesión, Hora local y GMT.
 *    Se usa la columna GMT directamente como hora UTC — nos ahorra calcular la zona horaria
 *    del circuito.
 *
 * HONESTO: el nombre de sesión a veces viene vacío en la web de origen (visto en alguna
 * clasificación) — esas filas se guardan igual con una etiqueta genérica "Sesión N" en vez de
 * descartarse, para no perder la hora.
 */
class GtWorldChallengeScheduleSource(
    override val series: RaceSeries,
    private val baseUrl: String
) : RaceScheduleSource {

    override suspend fun fetch(): List<EventEntity> {
        val calendarHtml = fetchHtml("$baseUrl/calendar")
        val eventUrls = extractItemListUrls(calendarHtml)
        return eventUrls.flatMap { url -> runCatching { sessionsForEvent(url) }.getOrElse { emptyList() } }
    }

    private fun extractItemListUrls(calendarHtml: String): List<String> {
        val doc = Jsoup.parse(calendarHtml, baseUrl)
        val adapter = StandingsMoshi.instance.adapter(JsonLdItemList::class.java)
        return doc.select("script[type=application/ld+json]")
            .mapNotNull { script -> runCatching { adapter.fromJson(script.data()) }.getOrNull() }
            .firstOrNull { it.itemListElement != null }
            ?.itemListElement.orEmpty()
            .mapNotNull { it.url }
    }

    private fun sessionsForEvent(eventUrl: String): List<EventEntity> {
        val html = fetchHtml(eventUrl)
        val doc = Jsoup.parse(html, eventUrl)

        val slug = eventUrl.trimEnd('/').substringAfterLast('/')
        val roundName = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val year = Year.now().value

        val sessions = mutableListOf<Pair<String, Long>>()
        doc.select("table.timetable__table").forEach { table ->
            val dateText = table.selectFirst(".timetable__caption span")?.text()
                ?: table.selectFirst("caption")?.text()
                ?: return@forEach
            val date = parseCaptionDate(dateText, year) ?: return@forEach

            val headers = table.select("thead th").map { it.text().trim() }
            val gmtIndex = headers.indexOfFirst { it.contains("GMT", ignoreCase = true) }
            if (gmtIndex < 0) return@forEach

            table.select("tbody tr").forEachIndexed { rowIndex, row ->
                val cells = row.select("td")
                val gmtText = cells.getOrNull(gmtIndex)?.text()?.trim() ?: return@forEachIndexed
                val time = parseHourMinute(gmtText) ?: return@forEachIndexed
                val sessionName = cells.getOrNull(0)?.text()?.trim().orEmpty().ifBlank { "Sesión ${rowIndex + 1}" }
                val startTimeUtc = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
                sessions.add(sessionName to startTimeUtc)
            }
        }

        return sessions.mapIndexed { index, (sessionName, startTimeUtc) ->
            EventEntity(
                series = series,
                uid = "${series.name}-$slug-$index",
                fullTitle = "${series.displayName} - $roundName - $sessionName",
                startTimeUtc = startTimeUtc,
                timeZoneId = null,
                inferredBadge = SessionBadgeMatcher.match(sessionName)
            )
        }
    }

    /** "Friday, 18 September" -> LocalDate del año en curso (estas webs solo publican la
     *  temporada actual, así que no hace falta la lógica de "saltar al año siguiente"). */
    private fun parseCaptionDate(text: String, year: Int): LocalDate? {
        val dayMonth = text.substringAfter(",").trim()
        return runCatching {
            LocalDate.parse("$dayMonth $year", DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        }.getOrNull()
    }

    private fun parseHourMinute(text: String): LocalTime? =
        runCatching { LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()

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

data class JsonLdItemList(val itemListElement: List<JsonLdListItem>?)
data class JsonLdListItem(val url: String?)