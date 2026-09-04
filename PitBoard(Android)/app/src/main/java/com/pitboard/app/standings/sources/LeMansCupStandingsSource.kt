package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsSource
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Year

/**
 * Le Mans Cup (Michelin Le Mans Cup): misma organización que WEC (ACO) y mismo mecanismo
 * para encontrar cada tabla — por el texto exacto del botón que la despliega, entrando
 * por su `id` (`data-bs-target`), no por posición en el documento.
 *
 * 03/09/2026 — REESCRITA de cero tras comprobar que la forma de fila NO es la misma que
 * WecStandingsSource, pese a ser la misma plantilla de web (el fallo real que describió
 * el usuario: números de coche, equipos y logos completamente descolocados). La tabla de
 * WEC trae una columna de logo entre "Pos." y "N°"; la de Le Mans Cup NO — aquí la
 * cabecera es literalmente "Pos. | N° | Team | ... | Total points", comprobado celda a
 * celda contra el HTML real (`<td>1</td><td>#85</td><td>R-ACE GP</td>...`). Con los
 * índices de WEC (pensados para 4 columnas iniciales) el número de coche leía en
 * realidad el nombre de equipo, el equipo leía la primera columna de puntos, y el logo
 * nunca se encontraba — de ahí que "no tuviera sentido cómo se veía".
 *
 * Como esta tabla no trae logo, se saca de otra página — lemanscup.com/en/car/{año}, el
 * mismo listado que ya usa AcoCarDriversSource para los pilotos — cruzando por número de
 * coche (comprobado que ambas páginas usan la misma numeración).
 *
 * LMP2: comprobado a fondo el 03/09/2026 (ni la página de clasificación ni el listado
 * completo de coches de la temporada mencionan "LMP2" ni una sola vez) — Le Mans Cup solo
 * tiene LMP3, LMP3 Pro/Am y GT3 esta temporada, no 4 clases.
 */
class LeMansCupStandingsSource : StandingsSource {

    override val category = StandingsCategory.LEMANS_CUP

    private val classificationUrl = "https://www.lemanscup.com/en/page/classification"
    private val gridUrl get() = "https://www.lemanscup.com/en/car/${Year.now().value}"

    private val sections: List<Pair<String, StandingsClass>> = listOf(
        "LMP3 Pro/Am Teams Classification" to StandingsClass.LMP3_PRO_AM,
        "LMP3 Teams Classification" to StandingsClass.LMP3,
        "GT3 Teams Classification" to StandingsClass.GT3
    )

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val classificationDoc = Jsoup.parse(fetchHtml(classificationUrl), classificationUrl)
        val logoByCarNumber = runCatching { fetchLogosByCarNumber() }.getOrElse { emptyMap() }

        return sections.flatMap { (buttonText, standingsClass) ->
            findSection(classificationDoc, buttonText)
                ?.let { parseRows(it, standingsClass, logoByCarNumber, nowUtc) }
                .orEmpty()
        }
    }

    /** Logo de equipo por número de coche, sacado de la página de listado (ver KDoc) — mapa
     *  vacío (nunca excepción) si esa página fallara, para no tumbar toda la clasificación
     *  solo porque los logos no se pudieran obtener. */
    private fun fetchLogosByCarNumber(): Map<String, String> {
        val doc = Jsoup.parse(fetchHtml(gridUrl), gridUrl)
        return doc.select("div.card-team").mapNotNull { card ->
            val carUrl = card.selectFirst("a.stretched-link")?.attr("href").orEmpty()
            val carNumber = carUrl.trimEnd('/').substringAfterLast('/').takeIf { it.toIntOrNull() != null }
                ?: return@mapNotNull null
            val logoUrl = card.selectFirst("div.brand-logo img")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            carNumber to logoUrl
        }.toMap()
    }

    private fun findSection(doc: Document, buttonText: String): Element? {
        val button = doc.select("button[data-bs-target]").firstOrNull { it.text().trim() == buttonText }
            ?: return null
        val targetId = button.attr("data-bs-target").removePrefix("#").takeIf { it.isNotBlank() } ?: return null
        return doc.getElementById(targetId)
    }

    /** Fila real: Pos. (0) | N° (1) | Team (2) | ... puntos por carrera ... | Total points
     *  (última celda) — SIN columna de logo, a diferencia de WecStandingsSource. */
    private fun parseRows(section: Element, standingsClass: StandingsClass, logoByCarNumber: Map<String, String>, nowUtc: Long): List<StandingEntity> {
        val table = section.selectFirst("table") ?: return emptyList()

        // Se numera 1..N sobre la lista ya filtrada (nunca con el índice de la fila
        // original) — mismo motivo que en WecStandingsSource: una sola fila que fallara
        // desplazaría todas las posiciones siguientes y se comería la primera.
        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null

            val carNumber = cells.getOrNull(1)?.text()?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val team = cells.getOrNull(2)?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val points = cells.lastOrNull()?.text()?.trim()?.toDoubleOrNull() ?: 0.0

            Triple(carNumber, team, points)
        }.mapIndexed { index, (carNumber, team, points) ->
            StandingEntity(
                category = category,
                standingsClass = standingsClass,
                type = StandingType.TEAM,
                entrantKey = "${category.name}-${standingsClass.name}-TEAM-$team-$carNumber",
                position = index + 1,
                name = "#$carNumber",
                team = team,
                points = points,
                photoUrl = logoByCarNumber[carNumber],
                updatedAtUtc = nowUtc
            )
        }
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
}
