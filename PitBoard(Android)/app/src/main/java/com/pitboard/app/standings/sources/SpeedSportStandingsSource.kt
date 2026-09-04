package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsSource
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * speedsport-magazine.com: una sola página por categoría con clasificación de pilotos
 * (con foto) y clasificación de equipos, en el mismo HTML — usado por F1, NASCAR Cup,
 * IndyCar y Porsche Supercup. A cambio de tener fotos, esta plantilla no relaciona
 * piloto↔equipo en la fila de piloto, así que `team` se deja vacío para las filas DRIVER
 * (decisión consciente, no un bug — ver conversación del 28/08/2026).
 *
 * HONESTO: no he podido inspeccionar el HTML byte a byte desde este entorno (solo un
 * resumen de su contenido), así que las columnas se localizan por el texto de sus
 * encabezados en vez de por índice fijo, para aguantar mejor pequeñas diferencias de
 * maquetación entre categorías. Si una columna no aparece donde se espera, es lo primero
 * a revisar.
 */
open class SpeedSportStandingsSource(
    override val category: StandingsCategory,
    private val pointsUrl: String
) : StandingsSource {

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val request = Request.Builder()
            .url(pointsUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$pointsUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$pointsUrl: cuerpo vacío")
        }

        // Base URI necesaria para que absUrl() resuelva las fotos si vienen como ruta
        // relativa en vez de URL completa.
        val doc = Jsoup.parse(html, pointsUrl)
        val tables = doc.select("table")

        val driverTable = tables.firstOrNull { table ->
            table.select("th").any { it.text().contains("Driver", ignoreCase = true) }
        }
        val teamTable = tables.firstOrNull { table ->
            table.select("th").any {
                val text = it.text()
                text.contains("Team", ignoreCase = true) && !text.contains("Driver", ignoreCase = true)
            }
        }

        val driverRows = driverTable?.let { parseStandingsTable(it, StandingType.DRIVER, nowUtc) }.orEmpty()
        val teamRows = teamTable?.let { parseStandingsTable(it, StandingType.TEAM, nowUtc) }.orEmpty()

        return driverRows + teamRows
    }

    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) para poder testear el
    // parsing con un fixture HTML real sin red — ver SpeedSportStandingsSourceTest. Ya
    // tomaba un Element en vez de una URL, así que no hace falta separar nada más.
    internal fun parseStandingsTable(table: Element, type: StandingType, nowUtc: Long): List<StandingEntity> {
        val headers = table.select("th").map { it.text() }
        val pointsIndex = headers.indexOfFirst {
            it.contains("Points", ignoreCase = true) || it.contains("Pts", ignoreCase = true)
        }
        val nameIndex = headers.indexOfFirst {
            if (type == StandingType.DRIVER) it.contains("Driver", ignoreCase = true) else it.contains("Team", ignoreCase = true)
        }
        val photoIndex = headers.indexOfFirst { it.contains("Photo", ignoreCase = true) }
        if (nameIndex < 0 || pointsIndex < 0) return emptyList()

        return table.select("tbody tr").mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapIndexedNotNull null

            val rawName = cells.getOrNull(nameIndex)?.text()?.trim() ?: return@mapIndexedNotNull null
            // Quita el sufijo "N win(s)" pegado al nombre en algunas filas (ej. "Andrea
            // Kimi Antonelli 7 wins" -> "Andrea Kimi Antonelli").
            val name = rawName.replace(Regex("\\s+\\d+\\s+wins?$"), "").trim()
            if (name.isBlank()) return@mapIndexedNotNull null

            val points = cells.getOrNull(pointsIndex)?.text()?.trim()?.toDoubleOrNull() ?: 0.0
            val photoUrl = if (photoIndex >= 0) {
                cells.getOrNull(photoIndex)?.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
            } else {
                null
            }

            StandingEntity(
                category = category,
                standingsClass = StandingsClass.OVERALL,
                type = type,
                entrantKey = "${category.name}-$type-$name",
                position = index + 1,
                name = name,
                team = "",
                points = points,
                photoUrl = photoUrl,
                updatedAtUtc = nowUtc
            )
        }
    }
}
