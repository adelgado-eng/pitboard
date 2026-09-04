package com.pitboard.app.schedule

import android.util.Log
import com.pitboard.app.data.EventDao
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.schedule.sources.EspnNascarScheduleSource
import com.pitboard.app.schedule.sources.F1AcademyScheduleSource
import com.pitboard.app.schedule.sources.FormulaEScheduleSource
import com.pitboard.app.schedule.sources.GtWorldChallengeScheduleSource
import com.pitboard.app.schedule.sources.ImsaScheduleSource
import com.pitboard.app.schedule.sources.IndyCarScheduleSource
import com.pitboard.app.schedule.sources.JolpicaF1ScheduleSource
import com.pitboard.app.schedule.sources.JsonLdSportsEventScheduleSource
import com.pitboard.app.schedule.sources.MotoGpPulseliveScheduleSource
import com.pitboard.app.schedule.sources.WikipediaSeasonCalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Sincroniza las 21 fuentes de calendario EN PARALELO y de forma independiente — mismo patrón
 * que StandingsRepository.syncAll(): si una serie falla (web caída, cambio de diseño...), las
 * demás se guardan igual, y si una fuente falla o no encuentra eventos, sus sesiones ya
 * guardadas en Room se dejan intactas en vez de borrarse.
 */
class RaceScheduleRepository(
    private val eventDao: EventDao,
    private val sources: List<RaceScheduleSource> = listOf(
        JolpicaF1ScheduleSource(),
        IndyCarScheduleSource(),
        EspnNascarScheduleSource(RaceSeries.NASCAR_CUP, "nascar-premier"),
        EspnNascarScheduleSource(RaceSeries.NASCAR_TRUCK, "truck"),
        // 02/09/2026: la NASCAR Xfinity Series se llama "NASCAR O'Reilly Auto Parts Series"
        // desde esta temporada (nuevo patrocinador principal), pero el slug de ESPN sigue
        // siendo "xfinity" — comprobado por búsqueda (espn.com/racing/schedule/_/series/xfinity),
        // no se pudo confirmar con una petición en vivo en esta sesión por un bloqueo puntual
        // de ESPN, pero es el mismo endpoint que ya usan Cup y Truck arriba.
        EspnNascarScheduleSource(RaceSeries.NASCAR_XFINITY, "xfinity"),
        JsonLdSportsEventScheduleSource(
            series = RaceSeries.F2,
            baseUrl = "https://www.fiaformula2.com",
            listingUrlTemplate = "https://www.fiaformula2.com/en/racing/{year}",
            roundHrefPrefixTemplate = "/en/racing/{year}/"
        ),
        JsonLdSportsEventScheduleSource(
            series = RaceSeries.F3,
            baseUrl = "https://www.fiaformula3.com",
            listingUrlTemplate = "https://www.fiaformula3.com/en/racing/{year}",
            roundHrefPrefixTemplate = "/en/racing/{year}/"
        ),
        F1AcademyScheduleSource(),
        JsonLdSportsEventScheduleSource(
            series = RaceSeries.ELMS,
            baseUrl = "https://www.europeanlemansseries.com",
            listingUrlTemplate = "https://www.europeanlemansseries.com/en/season/{year}",
            roundHrefPrefixTemplate = "/en/race/",
            excludeSlugContaining = listOf("test")
        ),
        // WEC: misma organización y misma plantilla de web que ELMS (fiawec.com, comprobado a
        // mano el 02/09/2026 — idéntico JSON-LD schema.org/SportsEvent), así que reutiliza la
        // misma fuente genérica. "prologue" es el test oficial de pretemporada, se descarta
        // igual que "test" en ELMS.
        JsonLdSportsEventScheduleSource(
            series = RaceSeries.WEC,
            baseUrl = "https://www.fiawec.com",
            listingUrlTemplate = "https://www.fiawec.com/en/season/{year}",
            roundHrefPrefixTemplate = "/en/race/",
            excludeSlugContaining = listOf("prologue")
        ),
        // Le Mans Cup: mismo organizador y misma plantilla que ELMS/WEC (lemanscup.com,
        // comprobado a mano el 03/09/2026 — idéntico JSON-LD). "collective-test" es el
        // día de test oficial antes de la primera cita, se descarta igual que "prologue"
        // en WEC.
        JsonLdSportsEventScheduleSource(
            series = RaceSeries.LEMANS_CUP,
            baseUrl = "https://www.lemanscup.com",
            listingUrlTemplate = "https://www.lemanscup.com/en/season/{year}",
            roundHrefPrefixTemplate = "/en/race/",
            excludeSlugContaining = listOf("test")
        ),
        // Fórmula E: corregido el 03/09/2026 tras confirmar que el calendario de Wikipedia
        // seguía sin aparecer pese al fix de parsing — fiaformulae.com/en/calendar SÍ tiene
        // los datos (a diferencia de la home, que fue lo que se comprobó la vez anterior y
        // llevó a la conclusión equivocada de que era una SPA sin datos server-side). Ver
        // FormulaEScheduleSource para el detalle.
        FormulaEScheduleSource(),
        ImsaScheduleSource(),
        // Porsche Supercup: su web oficial (racing.porsche.com) es una SPA sin datos de
        // sesiones en el HTML servido — no encontré una fuente con hora exacta, así que se
        // queda con el calendario de Wikipedia (solo fecha, ver WikipediaSeasonCalendarSource).
        WikipediaSeasonCalendarSource(RaceSeries.PORSCHE_SUPERCUP, "Porsche_Supercup"),
        MotoGpPulseliveScheduleSource(RaceSeries.MOTOGP, "MGP"),
        // Moto2/Moto3: misma API que MotoGP, solo cambia el acrónimo de clase (comprobado a
        // mano el 02/09/2026 contra la respuesta real de la API) — ver MotoGpPulseliveScheduleSource.
        MotoGpPulseliveScheduleSource(RaceSeries.MOTO2, "MT2"),
        MotoGpPulseliveScheduleSource(RaceSeries.MOTO3, "MT3"),
        GtWorldChallengeScheduleSource(RaceSeries.GT_CHALLENGE_EUROPE, "https://www.gt-world-challenge-europe.com"),
        GtWorldChallengeScheduleSource(RaceSeries.GT_CHALLENGE_AMERICA, "https://www.gt-world-challenge-america.com"),
        GtWorldChallengeScheduleSource(RaceSeries.GT_CHALLENGE_ASIA, "https://www.gt-world-challenge-asia.com"),
        GtWorldChallengeScheduleSource(RaceSeries.GT_CHALLENGE_AUSTRALIA, "https://www.gt-world-challenge-australia.com")
    )
) {

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) { source.series to runCatching { source.fetch() } }
            }.awaitAll()
        }

        val outcomes = results.map { (series, result) ->
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.e("RaceScheduleSync", "$series: fallo al obtener el calendario", error)
                return@map SeriesOutcome(series, ok = false, sessionCount = 0, detail = describe(error))
            }

            val rawEvents = result.getOrNull().orEmpty()
            // Filtro defensivo (03/09/2026, tras un aviso de eventos duplicados en Eventos):
            // una fuente puede listar la misma sesión dos veces bajo un `uid` distinto cada
            // vez (ej. el mismo enlace de ronda repetido en dos secciones de la web de
            // origen, o un índice que no es estable entre dos filas idénticas) — eso NO
            // choca con el índice único (series, uid) de EventEntity, así que sin este
            // filtro ambas filas se guardarían y aparecerían como el mismo evento repetido.
            // Se deduplica por contenido real (título completo + hora exacta), no por uid,
            // conservando la primera aparición.
            val events = rawEvents.distinctBy { it.fullTitle to it.startTimeUtc }
            if (events.size != rawEvents.size) {
                Log.w(
                    "RaceScheduleSync",
                    "$series: se descartaron ${rawEvents.size - events.size} sesiones duplicadas (mismo título y hora)"
                )
            }
            if (events.isEmpty()) {
                Log.w("RaceScheduleSync", "$series: la fuente respondió pero sin sesiones")
                return@map SeriesOutcome(
                    series = series,
                    ok = false,
                    sessionCount = 0,
                    detail = "La web respondió pero no se encontró ninguna sesión (¿cambio de diseño en la fuente?)"
                )
            }

            eventDao.replaceSeries(series, events)
            SeriesOutcome(series, ok = true, sessionCount = events.size, detail = null)
        }

        SyncResult(outcomes)
    }

    private fun describe(error: Throwable): String =
        generateSequence(error) { it.cause }
            .take(3)
            .map { t ->
                val type = t::class.java.simpleName
                val message = t.message?.trim()?.takeIf { it.isNotBlank() }?.take(200)
                if (message == null) type else "$type: $message"
            }
            .joinToString(separator = "\n   ← causa: ")

    data class SeriesOutcome(
        val series: RaceSeries,
        val ok: Boolean,
        val sessionCount: Int,
        val detail: String?
    )

    data class SyncResult(val outcomes: List<SeriesOutcome>) {
        val succeeded: List<RaceSeries> get() = outcomes.filter { it.ok }.map { it.series }
        val failed: List<RaceSeries> get() = outcomes.filterNot { it.ok }.map { it.series }
    }
}
