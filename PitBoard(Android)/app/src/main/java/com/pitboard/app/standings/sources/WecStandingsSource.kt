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
import org.jsoup.select.Elements

/**
 * WEC (FIA World Endurance Championship): igual que ELMS, se trata "por coche" — pero a
 * diferencia de ELMS, comprobado a mano el 03/09/2026 que fiawec.com solo tiene 2 clases
 * puntuables hoy: **Hypercar** y **LMGT3** — LMP2 se retiró como clase de campeonato de
 * WEC tras 2023 (sigue corriendo en ELMS/Le Mans Cup, no aquí).
 *
 * Fuente: fiawec.com/en/page/manufacturers-classification — una sola página con varias
 * tablas colapsables, cada una con su propio `id` ("results-N") enlazado desde el botón
 * que la despliega. Se busca cada tabla por el TEXTO EXACTO de su botón y se entra por
 * ese `id` (no por posición en el documento, que es justo lo que dio problemas en
 * ElmsStandingsSource) — mucho más fiable.
 *
 * Las dos clases usan una tabla con la MISMA forma de fila (Pos. | logo | N° | ... |
 * Total points), pero la columna 4 significa cosas distintas:
 * - Hypercar no tiene tabla de "Equipos" propiamente dicha, solo "FIA Hypercar World
 *   Endurance Drivers Championship" — la columna 4 son los pilotos, y el "equipo" que se
 *   guarda aquí es el FABRICANTE (su logo ya viene en la fila, columna "Man."), no el
 *   nombre de la escudería — Hypercar en WEC es, a efectos de puntos, un campeonato de
 *   fabricantes. IMPORTANTE (03/09/2026): esa tabla puntúa PILOTOS, no coches — un mismo
 *   número de coche aparece dos veces (uno por piloto) con puntos distintos si un piloto
 *   se ha perdido alguna carrera. Se agrupa por número de coche y se toma el piloto con
 *   más puntos como representante de esa fila — sin esto, dos filas con el mismo coche
 *   se pisaban entre sí en Room (misma clave), lo que se comía coches enteros y
 *   desplazaba las posiciones (el bug real que describió el usuario).
 * - LMGT3 sí tiene "FIA Endurance Trophy for LMGT3 Teams" con el nombre de equipo real
 *   (ej. "TF SPORT") en su propia columna — el logo de esa fila sigue siendo el del
 *   fabricante del coche (Corvette, BMW...), no uno propio del equipo: es una
 *   simplificación consciente (esta web no publica un logo de equipo aparte en esta
 *   tabla), documentada aquí en vez de dejarla caer.
 */
class WecStandingsSource : StandingsSource {

    override val category = StandingsCategory.WEC

    private val pageUrl = "https://www.fiawec.com/en/page/manufacturers-classification"

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$pageUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$pageUrl: cuerpo vacío")
        }

        val doc = Jsoup.parse(html, pageUrl)

        val hypercarRows = findSection(doc, "FIA Hypercar World Endurance Drivers Championship")
            ?.let { parseRows(it, StandingsClass.HYPERCAR, nowUtc) { cells -> cells.getOrNull(1)?.selectFirst("img")?.attr("alt") } }
            .orEmpty()

        val lmgt3Rows = findSection(doc, "FIA Endurance Trophy for LMGT3 Teams")
            ?.let { parseRows(it, StandingsClass.LMGT3, nowUtc) { cells -> cells.getOrNull(3)?.text() } }
            .orEmpty()

        return hypercarRows + lmgt3Rows
    }

    /** Busca el botón cuyo texto sea EXACTAMENTE [buttonText] y entra a su sección
     *  colapsable por el `id` que declara en `data-bs-target` — null si esta temporada
     *  cambiara el texto del botón o desapareciera esa sección. */
    private fun findSection(doc: org.jsoup.nodes.Document, buttonText: String): Element? {
        val button = doc.select("button[data-bs-target]").firstOrNull { it.text().trim() == buttonText }
            ?: return null
        val targetId = button.attr("data-bs-target").removePrefix("#").takeIf { it.isNotBlank() } ?: return null
        return doc.getElementById(targetId)
    }

    /** Fila común a las dos tablas: Pos. | logo (columna 1) | N° (columna 2) | ... |
     *  Total points (última celda) — [teamText] decide qué poner como "equipo" según la
     *  tabla (ver KDoc de la clase). */
    private fun parseRows(section: Element, standingsClass: StandingsClass, nowUtc: Long, teamText: (Elements) -> String?): List<StandingEntity> {
        val table = section.selectFirst("table") ?: return emptyList()

        // 03/09/2026: CORREGIDO — antes la posición salía de mapIndexedNotNull { index, ... },
        // pero ese "index" es la posición en la lista ORIGINAL de filas, no en la ya filtrada.
        // Si una sola fila fallaba (ej. el "alt" del logo del fabricante viniera vacío en
        // Hypercar), todas las posiciones posteriores quedaban desplazadas una a una y la
        // primera fila desaparecía sin más — el bug real que describió el usuario ("empieza
        // en el 2, no existe el 1", y coches sueltos que faltaban). Aquí se parsea primero SIN
        // posición y se renumera 1..N ya sobre la lista filtrada, mismo criterio que
        // ElmsStandingsSource/DriverDbStandingsSource/ImsaStandingsSource.
        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) return@mapNotNull null

            val carNumber = cells.getOrNull(2)?.text()?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val team = teamText(cells)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val logoUrl = cells.getOrNull(1)?.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
            val points = cells.lastOrNull()?.text()?.trim()?.toDoubleOrNull() ?: 0.0

            ParsedRow(carNumber, team, logoUrl, points)
        }
            // Un coche por fila: si el mismo número aparece más de una vez (Hypercar, ver
            // KDoc), se queda el piloto con más puntos. Reordenar por puntos descendente
            // después del agrupado es necesario porque quitar duplicados puede alterar el
            // orden original de la tabla.
            .groupBy { it.carNumber }
            .map { (_, rows) -> rows.maxBy { it.points } }
            .sortedByDescending { it.points }
            .mapIndexed { index, row ->
            StandingEntity(
                category = category,
                standingsClass = standingsClass,
                type = StandingType.TEAM,
                entrantKey = "${category.name}-${standingsClass.name}-TEAM-${row.team}-${row.carNumber}",
                position = index + 1,
                name = "#${row.carNumber}",
                team = row.team,
                points = row.points,
                photoUrl = row.logoUrl,
                updatedAtUtc = nowUtc
            )
        }
    }

    private data class ParsedRow(val carNumber: String, val team: String, val logoUrl: String?, val points: Double)
}
