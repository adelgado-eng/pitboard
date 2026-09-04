package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 02/09/2026: tras investigar a fondo, F2, F3, F1 Academy, ELMS, IMSA, MotoGP y las 4
 * variantes de GT World Challenge pasaron a tener fuente propia con hora exacta (ver
 * JsonLdSportsEventScheduleSource, F1AcademyScheduleSource, ImsaScheduleSource,
 * MotoGpPulseliveScheduleSource y GtWorldChallengeScheduleSource). Esta fuente genérica por
 * Wikipedia se queda para **Porsche Supercup** y **Fórmula E**: sus webs oficiales son SPA
 * sin datos de sesiones en el HTML servido, y no encontré ninguna otra fuente estructurada
 * con hora exacta para ninguna de las dos.
 *
 * Wikipedia mantiene un artículo con una tabla de calendario (columnas Round/Rnd./Date/
 * Circuit/Race o similar, detectadas por su encabezado igual que
 * MotorsportStandingsHtmlSource hace con las tablas de clasificación) — verificado a mano el
 * 01/09/2026 (Porsche Supercup) y 03/09/2026 (Fórmula E).
 *
 * 03/09/2026 — CORREGIDOS DOS FALLOS REALES que dejaban a Fórmula E con CERO carreras
 * (comprobado con curl contra la tabla real, no una suposición):
 * 1. LA FECHA YA TRAÍA EL AÑO. El código asumía que la celda de fecha nunca lo trae (cierto
 *    en Porsche Supercup, "6 April") y le pegaba el año calculado detrás sin mirar — pero la
 *    tabla de Fórmula E ya escribe "18 December 2026" completo, así que el resultado quedaba
 *    "18 December 2026 2026" y el parseo fallaba SIEMPRE. Ahora solo se añade el año si el
 *    texto no termina ya en uno de 4 cifras.
 * 2. ROWSPAN. Cuando dos rondas comparten circuito (dos E-Prix seguidos en la misma ciudad),
 *    Wikipedia fusiona la celda de circuito/país/nombre con `rowspan="2"` y la segunda fila
 *    NO repite esas celdas — solo trae su propio número de ronda y su propia fecha. Leer
 *    `row.select("th, td")` a pelo desalineaba los índices en esa segunda fila (le faltaban
 *    celdas por el medio) y la fecha se perdía. Ahora la tabla se "expande" primero a una
 *    rejilla completa (rellenando las celdas fusionadas hacia abajo) antes de leer columnas
 *    por índice — ver expandToGrid().
 *
 * HONESTO — dos limitaciones aceptadas conscientemente:
 * 1. Wikipedia da la fecha de la ronda, no la hora de cada sesión — así que cada ronda se
 *    guarda como una única sesión "Carrera" a mediodía UTC (hora de referencia, no real).
 * 2. Cuando la fecha es un rango de dos días se toma el ÚLTIMO día como fecha de carrera — es
 *    una heurística, no siempre es exacto para eventos con formato atípico.
 */
class WikipediaSeasonCalendarSource(
    override val series: RaceSeries,
    /** Sufijo del título del artículo, sin el año (ej. "MotoGP_season" para
     *  "2026_MotoGP_season") — ignorado si se indica [explicitArticleTitle]. */
    private val articleSlugSuffix: String = "",
    /** Título completo del artículo tal cual, para series cuya temporada cruza dos años
     *  naturales (ej. Fórmula E: "2026–27_Formula_E_World_Championship", con guion largo)
     *  y por tanto no encajan en el patrón "{año} {serie}". HONESTO: a diferencia del
     *  cálculo automático con Year.now(), esto hay que actualizarlo a mano cada
     *  temporada (03/09/2026). */
    private val explicitArticleTitle: String? = null
) : RaceScheduleSource {

    override suspend fun fetch(): List<EventEntity> {
        val year = Year.now().value
        val url = "https://en.wikipedia.org/wiki/" + (explicitArticleTitle ?: "${year}_$articleSlugSuffix")
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html, url)

        val table = doc.select("table.wikitable").firstOrNull { candidate ->
            val headerRowTexts = headerRowTexts(candidate)
            headerRowTexts.any { it.contains("date", ignoreCase = true) } &&
                headerRowTexts.any {
                    it.contains("circuit", ignoreCase = true) ||
                        it.contains("race", ignoreCase = true) ||
                        it.contains("round", ignoreCase = true) ||
                        it.contains("rnd", ignoreCase = true)
                }
        } ?: return emptyList()

        val headers = headerRowTexts(table)
        val dateIndex = headers.indexOfFirst { it.contains("date", ignoreCase = true) }
        if (dateIndex < 0) return emptyList()
        val raceIndex = headers.indexOfFirst {
            it.contains("grand prix", ignoreCase = true) ||
                it.contains("e-prix", ignoreCase = true) ||
                it.contains("race", ignoreCase = true) ||
                it.contains("event", ignoreCase = true)
        }
        val circuitIndex = headers.indexOfFirst { it.contains("circuit", ignoreCase = true) }

        // La primera fila de la rejilla es la propia cabecera (headers ya calculado arriba a
        // partir de ella) — se descarta con drop(1).
        val grid = expandToGrid(table).drop(1)

        return grid.mapIndexedNotNull { index, row ->
            val dateText = row.getOrNull(dateIndex)?.trim() ?: return@mapIndexedNotNull null
            val startTimeUtc = parseRaceDate(dateText, year) ?: return@mapIndexedNotNull null

            val raceName = raceIndex.takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }
                .orEmpty()
                .ifBlank { "Ronda ${index + 1}" }
            val circuitName = circuitIndex.takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }.orEmpty()

            EventEntity(
                series = series,
                uid = "${series.name}-WIKI-$year-R${index + 1}",
                fullTitle = "${series.displayName} - $raceName - $circuitName - Carrera",
                startTimeUtc = startTimeUtc,
                timeZoneId = null,
                inferredBadge = SessionBadgeType.RACE
            )
        }
    }

    /** Cabecera real de la tabla (solo la primera fila) — a diferencia de un `table.select("th")`
     *  a pelo, que también cogería el número de ronda de cada fila de datos (Wikipedia lo marca
     *  como `<th>` de fila, no de columna) y podría desplazar cualquier búsqueda por posición. */
    private fun headerRowTexts(table: Element): List<String> {
        val headerRow = table.select("tr").firstOrNull() ?: return emptyList()
        return headerRow.select("th, td").map { it.text().trim() }
    }

    /**
     * Expande la tabla (cabecera incluida, en la posición 0) a una rejilla completa,
     * rellenando hacia abajo las celdas que una fila posterior "hereda" por `rowspan` — sin
     * esto, una fila cuyo circuito viene fusionado con la de arriba solo trae sus propias
     * celdas (ronda + fecha) y todo lo demás se lee desplazado o directamente desaparece
     * (ver el punto 2 del HONESTO de la clase). No se contempla `colspan` — no se ha visto
     * en ninguna de las tablas de calendario reales usadas aquí.
     */
    private fun expandToGrid(table: Element): List<List<String>> {
        val rows = table.select("tr")
        val grid = mutableListOf<List<String>>()
        // Columna -> (filas restantes incluyendo esta, texto) para las celdas con rowspan activo.
        val pending = mutableMapOf<Int, Pair<Int, String>>()

        for (row in rows) {
            val cells = row.select("th, td")
            val outputRow = mutableListOf<String>()
            var cellPtr = 0
            var col = 0
            // Sigue mientras queden celdas propias de esta fila por colocar O columnas con un
            // rowspan todavía activo más allá de la última celda propia.
            while (cellPtr < cells.size || pending.keys.any { it >= col }) {
                val active = pending[col]
                if (active != null) {
                    outputRow += active.second
                    val remaining = active.first - 1
                    if (remaining <= 0) pending.remove(col) else pending[col] = remaining to active.second
                } else {
                    val cell = cells.getOrNull(cellPtr) ?: break
                    cellPtr++
                    val text = cell.text().trim()
                    outputRow += text
                    val rowspan = cell.attr("rowspan").toIntOrNull() ?: 1
                    if (rowspan > 1) pending[col] = (rowspan - 1) to text
                }
                col++
            }
            grid += outputRow
        }
        return grid
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()
        return StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }
    }

    /** Mediodía UTC del día de carrera resuelto — ver limitación 1 en el comentario de clase. */
    private fun parseRaceDate(raw: String, year: Int): Long? {
        val date = resolveDate(raw, year) ?: return null
        return date.atStartOfDay(ZoneId.of("UTC")).plusHours(12).toInstant().toEpochMilli()
    }

    private fun resolveDate(raw: String, year: Int): LocalDate? {
        // &nbsp; (U+00A0) y los guiones largos que usa Wikipedia para separar rangos de fecha
        // se normalizan por punto de código, no por el carácter literal en el código fuente —
        // así no depende de cómo el editor lo represente.
        var text = raw
            .replace(Regex("\\[[0-9]+\\]"), "") // referencias tipo "[63]"
            .map { c ->
                when (c.code) {
                    0x00A0 -> ' ' // &nbsp;
                    0x2013, 0x2014 -> '-' // en dash / em dash
                    else -> c
                }
            }
            .joinToString("")
            .trim()
        if (text.isBlank()) return null

        // "6/7 April" (ELMS): el día de carrera es el segundo número, mismo mes.
        val slashMatch = Regex("(\\d{1,2})/(\\d{1,2})\\s+([A-Za-z]+)").find(text)
        if (slashMatch != null) {
            text = "${slashMatch.groupValues[2]} ${slashMatch.groupValues[3]}"
        } else if ("-" in text) {
            // "January 24-25" o "27 Feb-1 Mar": nos quedamos con el segundo tramo; si no trae
            // su propio mes, se lo tomamos prestado del primero.
            val parts = text.split("-").map { it.trim() }
            val first = parts.getOrNull(0).orEmpty()
            val last = parts.getOrNull(1).orEmpty()
            text = if (last.any { it.isLetter() }) {
                last
            } else {
                val month = Regex("[A-Za-z]+").find(first)?.value
                if (month != null) "$last $month" else last
            }
        }

        text = text.trim()
        if (text.isBlank()) return null

        // 03/09/2026: Porsche Supercup nunca trae el año en la celda ("6 April"), pero
        // Fórmula E sí ("18 December 2026") — añadir el año siempre duplicaba ese caso
        // ("18 December 2026 2026", que ningún formateador reconoce) y por eso esa
        // categoría se quedaba sin ni una sola carrera. Ahora solo se añade si el texto no
        // termina ya en un año de 4 cifras.
        val candidate = if (Regex("\\d{4}$").containsMatchIn(text)) text else "$text $year"
        return DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(candidate, formatter) }.getOrNull()
        }
    }

    companion object {
        private val DATE_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
        )
    }
}
