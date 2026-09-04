package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.schedule.SessionBadgeMatcher
import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * imsa.com bloquea peticiones sospechosas en algunas rutas, pero la página del calendario y
 * las de cada evento son HTML normal (WordPress) sin JavaScript — verificado a mano el
 * 02/09/2026. Dos pasos:
 * 1. `weathertech/weathertech-{año}-schedule/` trae los enlaces "Event Details" de cada ronda.
 * 2. Cada evento (`imsa.com/events/{slug}/`) tiene una sección "Event Schedule" en HTML plano:
 *    un `.day-event-header` por día seguido de varios `.day-event-details-container`, cada uno
 *    con la hora ("10:05 AM to 11:35 AM ET") y el nombre de sesión.
 *
 * HONESTO: esa sección de horario mezcla la clase principal (WeatherTech Championship) con las
 * de apoyo (Mazda MX-5 Cup, Michelin Pilot Challenge...) sin ningún marcado que las separe más
 * que el propio texto del nombre — se filtra por palabras clave, así que una sesión de una
 * clase de apoyo con un nombre atípico podría colarse. Solo se guarda la hora de inicio del
 * rango ("10:05 AM" de "10:05 AM to 11:35 AM ET"), no el final.
 */
class ImsaScheduleSource : RaceScheduleSource {
    override val series: RaceSeries = RaceSeries.IMSA

    override suspend fun fetch(): List<EventEntity> {
        val year = Year.now().value
        val listingUrl = "https://www.imsa.com/weathertech/weathertech-$year-schedule/"
        val listingHtml = fetchHtml(listingUrl)
        val listingDoc = Jsoup.parse(listingHtml, listingUrl)

        val eventUrls = listingDoc.select("a:contains(Event Details)")
            .mapNotNull { it.attr("abs:href").takeIf { url -> url.isNotBlank() } }
            .distinct()

        return eventUrls.flatMap { url -> runCatching { sessionsForEvent(url) }.getOrElse { emptyList() } }
    }

    private fun sessionsForEvent(eventUrl: String): List<EventEntity> {
        val html = fetchHtml(eventUrl)
        val doc = Jsoup.parse(html, eventUrl)
        val slug = eventUrl.trimEnd('/').substringAfterLast('/')
        // La página no tiene <h1> — el nombre de la ronda sale del <title> ("2026 Rolex 24 At
        // DAYTONA | IMSA"), quitando el sufijo del sitio y el año inicial.
        val roundName = doc.title()
            .substringBefore("|")
            .trim()
            .replaceFirst(Regex("^\\d{4}\\s+"), "")

        val container = doc.selectFirst(".race-event-schedule-container-inner") ?: return emptyList()

        var currentDate: LocalDate? = null
        val results = mutableListOf<EventEntity>()
        var index = 0

        for (child in container.children()) {
            when {
                child.hasClass("day-event-header") -> currentDate = parseHeaderDate(child.text())
                child.hasClass("day-event-details-container") -> {
                    val date = currentDate ?: continue
                    val timeText = child.selectFirst(".event-time")?.text()?.trim() ?: continue
                    val name = child.selectFirst(".event-name")?.text()?.trim() ?: continue
                    if (!isMainClassSession(name)) continue

                    val startTime = parseRangeStartTime(timeText) ?: continue
                    val startTimeUtc = LocalDateTime.of(date, startTime)
                        .atZone(EASTERN)
                        .toInstant()
                        .toEpochMilli()

                    results.add(
                        EventEntity(
                            series = RaceSeries.IMSA,
                            uid = "IMSA-$slug-${index++}",
                            fullTitle = "${RaceSeries.IMSA.displayName} - $roundName - $name",
                            startTimeUtc = startTimeUtc,
                            timeZoneId = EASTERN.id,
                            inferredBadge = SessionBadgeMatcher.match(name)
                        )
                    )
                }
            }
        }

        return results
    }

    /** Las clases de apoyo se nombran explícitamente ("... - Mazda MX-5 Cup") — si el nombre
     *  no menciona ninguna de las conocidas, se asume que es de la clase principal
     *  (WeatherTech Championship), que a veces aparece sin sufijo (ej. "Rolex 24 At Daytona"). */
    private fun isMainClassSession(name: String): Boolean {
        val t = name.lowercase()
        return SUPPORT_SERIES.none { it in t }
    }

    private fun parseHeaderDate(text: String): LocalDate? {
        val cleaned = text.trim().substringAfter(",").trim()
        return runCatching {
            LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
        }.getOrNull()
    }

    /** "10:05 AM to 11:35 AM ET" -> 10:05 AM (solo el inicio). */
    private fun parseRangeStartTime(text: String): LocalTime? {
        val start = text.substringBefore(" to ").trim()
        return runCatching { LocalTime.parse(start, DateTimeFormatter.ofPattern("h:mm a", Locale.US)) }.getOrNull()
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .build()
        return StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }
    }

    companion object {
        private val EASTERN = ZoneId.of("America/New_York")
        private val SUPPORT_SERIES = listOf(
            "mazda mx-5 cup",
            "michelin pilot challenge",
            "vp racing sportscar challenge",
            "porsche carrera cup",
            "ferrari challenge",
            "lamborghini",
            "radical cup"
        )
    }
}
