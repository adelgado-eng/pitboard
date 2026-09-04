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
import java.time.Year

/**
 * Fuente genérica para las webs que publican cada ronda con datos estructurados
 * `schema.org/SportsEvent` (bloque `<script type="application/ld+json">` con un array
 * `subEvent`, cada uno con `name` tipo "Practice - Australian Grand Prix" y `startDate` en
 * ISO-8601 con offset) — verificado a mano el 02/09/2026 en fiaformula2.com, fiaformula3.com
 * y europeanlemansseries.com, que comparten esta misma estructura pese a ser sitios distintos.
 *
 * Funciona en dos pasos:
 * 1. Se lee la página de listado de la temporada y se sacan los enlaces a cada ronda
 *    (empiezan por [roundHrefPrefixTemplate], con el año ya sustituido).
 * 2. Se visita cada ronda y se lee su JSON-LD.
 *
 * HONESTO: si el sitio cambia el marcado del listado o dejara de usar JSON-LD, esta fuente
 * empieza a devolver listas vacías (fallo silencioso y aislado, ver RaceScheduleRepository) —
 * no hay tabla HTML de la que caer hacia atrás en este caso.
 */
class JsonLdSportsEventScheduleSource(
    override val series: RaceSeries,
    private val baseUrl: String,
    /** URL de listado de la temporada, con "{year}" como marcador (ej.
     *  "https://www.fiaformula2.com/en/racing/{year}"). */
    private val listingUrlTemplate: String,
    /** Prefijo del href de cada ronda dentro del listado, con "{year}" como marcador (ej.
     *  "/en/racing/{year}/"). */
    private val roundHrefPrefixTemplate: String,
    /** Fragmentos que, si aparecen en el slug de la ronda, la descartan (ej. "test" para no
     *  colar los días de test oficiales en el calendario de carreras). */
    private val excludeSlugContaining: List<String> = emptyList()
) : RaceScheduleSource {

    override suspend fun fetch(): List<EventEntity> {
        val year = Year.now().value
        val listingUrl = listingUrlTemplate.replace("{year}", year.toString())
        val roundHrefPrefix = roundHrefPrefixTemplate.replace("{year}", year.toString())

        val listingHtml = fetchHtml(listingUrl)
        val roundUrls = extractRoundUrls(listingHtml, roundHrefPrefix)

        return roundUrls.flatMap { roundUrl -> runCatching { sessionsForRound(roundUrl) }.getOrElse { emptyList() } }
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el filtro
    // de enlaces de ronda (prefijo + exclusión de slugs de test) contra un fixture HTML sin
    // red — ver JsonLdSportsEventScheduleSourceTest.
    internal fun extractRoundUrls(listingHtml: String, roundHrefPrefix: String): List<String> {
        val listingDoc = Jsoup.parse(listingHtml, baseUrl)
        return listingDoc.select("a[href]")
            .filter { it.attr("href").startsWith(roundHrefPrefix) }
            .mapNotNull { it.attr("abs:href").takeIf { url -> url.isNotBlank() } }
            .distinct()
            .filterNot { url -> excludeSlugContaining.any { it.lowercase() in url.lowercase() } }
    }

    private fun sessionsForRound(roundUrl: String): List<EventEntity> = parseRoundHtml(fetchHtml(roundUrl), roundUrl)

    // 04/09/2026 (Fase 1 del diagnóstico): separado de sessionsForRound() para poder
    // testear el parsing del JSON-LD de una ronda contra un fixture HTML sin red.
    internal fun parseRoundHtml(html: String, roundUrl: String): List<EventEntity> {
        val doc = Jsoup.parse(html, roundUrl)
        val adapter = StandingsMoshi.instance.adapter(JsonLdSportsEvent::class.java)

        val event = doc.select("script[type=application/ld+json]")
            .mapNotNull { script -> runCatching { adapter.fromJson(script.data()) }.getOrNull() }
            .firstOrNull { !it.subEvent.isNullOrEmpty() }
            ?: return emptyList()

        val slug = roundUrl.trimEnd('/').substringAfterLast('/')
        // El nombre del subEvent trae "Sesión - Nombre del Gran Premio" (ej. "Practice -
        // Australian Grand Prix") — la parte de después del guion es más corta y legible que
        // el "name" del evento raíz (que en fiaformula2/3.com viene con el título completo del
        // GP de F1, patrocinador incluido).
        val roundName = event.subEvent.orEmpty().firstNotNullOfOrNull { sub ->
            sub.name?.substringAfter(" - ", missingDelimiterValue = "")?.trim()?.takeIf { it.isNotBlank() }
        } ?: event.name?.trim().orEmpty()
        val circuitName = event.location?.name?.trim().orEmpty()

        return event.subEvent.orEmpty().mapIndexedNotNull { index, sub ->
            val rawName = sub.name?.trim() ?: return@mapIndexedNotNull null
            val startTimeUtc = sub.startDate?.let { parseInstant(it) } ?: return@mapIndexedNotNull null
            val label = rawName.substringBefore(" - ").trim().ifBlank { rawName }

            EventEntity(
                series = series,
                uid = "${series.name}-$slug-$index",
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

data class JsonLdSportsEvent(
    val name: String?,
    val startDate: String?,
    val location: JsonLdLocation?,
    val subEvent: List<JsonLdSportsEvent>?
)

data class JsonLdLocation(val name: String?)
