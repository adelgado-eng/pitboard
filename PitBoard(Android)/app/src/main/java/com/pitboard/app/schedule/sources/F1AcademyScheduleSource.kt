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
 * f1academy.com es una SPA en Next.js, pero — a diferencia de motogp.com — sí deja los datos
 * de la temporada completa incrustados en el propio HTML servido, dentro del script
 * `#__NEXT_DATA__` (el mecanismo estándar de Next.js para hidratar la página sin una llamada
 * de red aparte) — verificado a mano el 02/09/2026. Con una sola petición a la página del
 * calendario se obtienen TODAS las rondas y TODAS las sesiones de la temporada actual, con
 * hora y zona horaria ya incluidas — no hace falta ni calcular el año ni visitar una página
 * por ronda.
 */
class F1AcademyScheduleSource : RaceScheduleSource {
    override val series: RaceSeries = RaceSeries.F1_ACADEMY

    override suspend fun fetch(): List<EventEntity> {
        val url = "https://www.f1academy.com/Racing-Series/Calendar"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        val json = Jsoup.parse(html).selectFirst("script[id=__NEXT_DATA__]")?.data() ?: return emptyList()
        val adapter = StandingsMoshi.instance.adapter(NextDataRoot::class.java)
        val races = adapter.fromJson(json)?.props?.pageProps?.pageData?.Races.orEmpty()

        return races.flatMap { race -> sessionsForRace(race) }
    }

    private fun sessionsForRace(race: F1AcademyRace): List<EventEntity> {
        val circuitName = race.CircuitShortName ?: race.CircuitName.orEmpty()
        val round = race.RoundNumber ?: 0

        return race.Sessions.orEmpty().mapIndexedNotNull { index, session ->
            val name = session.SessionName?.trim() ?: return@mapIndexedNotNull null
            val startTimeUtc = session.SessionStartTime?.let { parseInstant(it) } ?: return@mapIndexedNotNull null

            EventEntity(
                series = RaceSeries.F1_ACADEMY,
                uid = "F1ACADEMY-R$round-$index",
                fullTitle = "${RaceSeries.F1_ACADEMY.displayName} - $circuitName - $name",
                startTimeUtc = startTimeUtc,
                timeZoneId = null,
                inferredBadge = SessionBadgeMatcher.match(name)
            )
        }
    }

    private fun parseInstant(raw: String): Long? =
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
}

data class NextDataRoot(val props: NextDataProps?)
data class NextDataProps(val pageProps: NextDataPageProps?)
data class NextDataPageProps(val pageData: F1AcademySeasonData?)
data class F1AcademySeasonData(val Races: List<F1AcademyRace>?)

data class F1AcademyRace(
    val RoundNumber: Int?,
    val CircuitName: String?,
    val CircuitShortName: String?,
    val Sessions: List<F1AcademySession>?
)

data class F1AcademySession(
    val SessionName: String?,
    val SessionStartTime: String?
)
