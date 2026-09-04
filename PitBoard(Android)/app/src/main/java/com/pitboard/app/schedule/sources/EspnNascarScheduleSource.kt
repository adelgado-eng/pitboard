package com.pitboard.app.schedule.sources

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.schedule.RaceScheduleSource
import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * nascar.com es JavaScript puro y además bloquea peticiones sin navegador real (403) — mismo
 * problema que ya resolvió NascarStandingsSource pasándose a espn.com. Aquí se reutiliza esa
 * misma solución para el calendario: espn.com/racing/schedule/_/series/{slug} es HTML de toda
 * la vida (tabla clásica sin JS), verificado a mano el 01/09/2026 para Cup ("nascar-premier")
 * y Truck ("truck").
 *
 * HONESTO: esta tabla es la agenda de EMISIÓN (carreras, incluidos "Duels"/exhibiciones), no
 * distingue libres/clasificación como sesiones aparte — todo se etiqueta como Carrera.
 */
class EspnNascarScheduleSource(
    override val series: RaceSeries,
    private val espnSeriesSlug: String
) : RaceScheduleSource {

    override suspend fun fetch(): List<EventEntity> {
        val url = "https://www.espn.com/racing/schedule/_/series/$espnSeriesSlug"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        return parseHtml(html)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el troceo
    // de celdas por <br> contra un fixture HTML sin red — ver EspnNascarScheduleSourceTest.
    internal fun parseHtml(html: String): List<EventEntity> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table.tablehead").firstOrNull() ?: return emptyList()

        return table.select("tr.oddrow, tr.evenrow").mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            val dateCell = cells.getOrNull(0) ?: return@mapIndexedNotNull null
            val raceCell = cells.getOrNull(1) ?: return@mapIndexedNotNull null

            val dateLines = cellLines(dateCell)
            val dateText = dateLines.getOrNull(0)?.substringAfter(',')?.trim() ?: return@mapIndexedNotNull null
            val timeText = dateLines.getOrNull(1)

            val raceLines = cellLines(raceCell)
            val raceName = raceCell.selectFirst("b")?.text()?.trim()
                ?: raceLines.getOrNull(0)
                ?: return@mapIndexedNotNull null
            val circuitName = raceLines.firstOrNull { it != raceName && !it.startsWith("*") }.orEmpty()

            val startTimeUtc = UsScheduleDateParsing.toUtcMillis(dateText, timeText) ?: return@mapIndexedNotNull null

            EventEntity(
                series = series,
                uid = "${series.name}-$index-${raceName.hashCode()}",
                fullTitle = "${series.displayName} - $raceName - $circuitName - Carrera",
                startTimeUtc = startTimeUtc,
                timeZoneId = UsScheduleDateParsing.eastZoneId(),
                inferredBadge = SessionBadgeType.RACE
            )
        }
    }

    /** ESPN separa fecha/hora y nombre/circuito con `<br>` dentro de la misma celda, sin
     *  ninguna otra marca — Element.text() los uniría en una sola línea con espacios, así que
     *  hay que trocear a mano por cada salto de línea. */
    private fun cellLines(cell: Element): List<String> {
        val lines = mutableListOf(StringBuilder())
        for (node in cell.childNodes()) {
            when {
                node is Element && node.tagName() == "br" -> lines.add(StringBuilder())
                node is Element -> lines.last().append(node.text())
                node is TextNode -> lines.last().append(node.text())
            }
        }
        // ESPN separa "Wed" de "Feb" de "4" con &nbsp; (U+00A0) en vez de un espacio normal —
        // String.trim()/DateTimeFormatter no lo tratan como espacio de verdad, así que se
        // normaliza aquí explícitamente por punto de código para no depender de cómo el editor
        // represente el carácter.
        return lines
            .map { line -> line.toString().map { c -> if (c.code == 0x00A0) ' ' else c }.joinToString("") }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
    }
}
