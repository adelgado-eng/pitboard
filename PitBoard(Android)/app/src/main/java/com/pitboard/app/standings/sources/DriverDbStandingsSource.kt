package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.pitboard.app.standings.StandingsSource
import java.time.Year

/**
 * driverdb.com: una sola tabla de pilotos por categoría, con foto, equipo y puntos en la
 * misma fila — usada por F1, NASCAR Cup, IndyCar, Porsche Supercup y F1 Academy (28/08/2026,
 * sustituye a speedsport-magazine.com: esa web tenía fotos genéricas para la mayoría de
 * pilotos en NASCAR/Porsche Supercup, y en F1 no listaba a los pilotos con 0 puntos —
 * Stroll, Pérez, Bottas, Tsunoda desaparecían de la clasificación).
 *
 * driverdb no publica una tabla de equipos aparte, así que la clasificación de equipos se
 * calcula agregando los puntos de sus pilotos (columna "Team" que sí trae cada fila de
 * piloto) — coincide con la fórmula real en todas estas categorías (constructores = suma
 * de los dos coches).
 *
 * HONESTO: igual que con las otras fuentes HTML, no he podido inspeccionar el DOM byte a
 * byte desde este entorno (sin navegador disponible esta sesión) — las columnas se
 * localizan por su encabezado, y una fila nunca se descarta solo por no tener foto (se usa
 * el icono por defecto en la UI) o por tener 0 puntos; solo se descarta si no hay nombre.
 */
open class DriverDbStandingsSource(
    override val category: StandingsCategory,
    private val slug: String,
    // Si se indica, se descarta a cualquier piloto que no aparezca en esta página de
    // referencia (parrilla confirmada de la temporada) — evita pilotos reserva/test que
    // driverdb sí lista con 0 puntos. Ver RosterNameFilter.
    private val knownRosterUrl: String? = null,
    // Logos de equipo por nombre normalizado, aplicados a las filas TEAM — driverdb no trae
    // ningún logo propio en esta plantilla, así que por defecto las filas TEAM se quedan sin
    // foto (como hasta ahora, ver F1AcademyStandingsSource para un caso que sí lo usa).
    private val teamLogoUrls: Map<String, String> = emptyMap(),
    /** Fotos de piloto por nombre normalizado, de respaldo cuando driverdb no trae una foto
     *  real para ese piloto (02/09/2026: F1 Academy es el caso — driverdb solo tiene foto
     *  para una minoría de las pilotas, ver F1AcademyStandingsSource). Solo se usa si
     *  driverdb no dio ya una foto válida — nunca la sustituye. */
    private val driverPhotoUrls: Map<String, String> = emptyMap(),
    /** 03/09/2026: F2 y F3 — a diferencia de F1 Academy (mapa fijo hallado a mano),
     *  fiaformula2.com/fiaformula3.com tienen una ficha propia por piloto
     *  ("/en/drivers/{slug}") con foto real, así que aquí se resuelve EN VIVO en cada
     *  sincronización en vez de un mapa hardcodeado — se actualiza sola si cambia la
     *  parrilla a mitad de temporada. Plantilla con "{slug}" como marcador (ej.
     *  "https://www.fiaformula2.com/en/drivers/{slug}"); solo se usa para pilotos que
     *  sigan sin foto después de driverdb y [driverPhotoUrls]. */
    private val officialProfileUrlTemplate: String? = null
) : StandingsSource {

    private val standingsUrl get() = "https://www.driverdb.com/championships/$slug/${Year.now().value}/standings"

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> = coroutineScope {
        val request = Request.Builder()
            .url(standingsUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$standingsUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$standingsUrl: cuerpo vacío")
        }

        val doc = Jsoup.parse(html, standingsUrl)
        val table = doc.select("table").firstOrNull { table ->
            table.select("th").any { it.text().contains("Driver", ignoreCase = true) }
        } ?: return@coroutineScope emptyList()

        val parsedRows = parseDriverRows(table, nowUtc)

        // Filtro de reservas: si hay una página de referencia configurada, solo se quedan
        // los pilotos que aparecen en ella — mejor esfuerzo, si no se pudo obtener la
        // parrilla de referencia no se filtra nada (ver RosterNameFilter.isInRoster).
        val knownNames = knownRosterUrl?.let { runCatching { RosterNameFilter.fetchKnownNames(it) }.getOrElse { emptySet() } }.orEmpty()
        val filteredRows0 = RosterNameFilter.filterKeepingReal(parsedRows, knownNames) { it.name }

        // Fotos oficiales EN VIVO (ver officialProfileUrlTemplate) para quien siga sin
        // foto tras driverdb/driverPhotoUrls — en paralelo con un tope de 6 a la vez
        // (mismo criterio que OfficialRosterStandingsSource: no abrir todas las
        // conexiones de golpe). Mejor esfuerzo: si una ficha concreta falla, esa fila se
        // queda sin foto, nunca desaparece.
        val filteredRows = if (officialProfileUrlTemplate == null) {
            filteredRows0
        } else {
            val gate = Semaphore(MAX_PARALLEL_PHOTO_REQUESTS)
            filteredRows0.map { row ->
                async(Dispatchers.IO) {
                    if (row.photoUrl != null) return@async row
                    val url = officialProfileUrlTemplate.replace("{slug}", slugify(row.name))
                    val photo = gate.withPermit { runCatching { fetchOfficialPhoto(url) }.getOrNull() }
                    if (photo != null) row.copy(photoUrl = photo) else row
                }
            }.awaitAll()
        }

        // 28/08/2026: no nos fiamos de la posición que trae la propia tabla de driverdb —
        // en tablas con muchos empates a puntos (ej. Porsche Supercup con varios pilotos a 0
        // puntos) esa web repite el mismo número de posición para todos los empatados, y
        // además, tras filtrar pilotos reserva arriba, la numeración original se queda con
        // huecos. Se renumera de forma secuencial según el orden en que ya vienen listados
        // (que es el orden real de clasificación), igual que se hace en las demás fuentes.
        val driverRows = filteredRows.mapIndexed { index, entity -> entity.copy(position = index + 1) }

        // Equipos: se agrupan los pilotos (ya filtrados) por su columna "Team" y se suman
        // los puntos — driverdb no trae una tabla de constructores separada en esta plantilla.
        val teamRows = driverRows
            .filter { it.team.isNotBlank() }
            .groupBy { it.team }
            .map { (teamName, entrants) -> teamName to entrants.sumOf { it.points } }
            .sortedByDescending { (_, points) -> points }
            .mapIndexed { index, (teamName, points) ->
                StandingEntity(
                    category = category,
                    standingsClass = StandingsClass.OVERALL,
                    type = StandingType.TEAM,
                    entrantKey = "${category.name}-TEAM-$teamName",
                    position = index + 1,
                    name = teamName,
                    team = "",
                    points = points,
                    photoUrl = teamLogoUrls[normalize(teamName)],
                    updatedAtUtc = nowUtc
                )
            }

        driverRows + teamRows
    }

    // 30/08/2026: normaliza también las tildes a su letra base (ver el mismo fix en
    // OfficialRosterStandingsSource) — aquí solo se usa para el nombre de equipo, se deja
    // consistente con el resto de fuentes.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun slugify(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")

    /** 03/09/2026: comprobado a mano en fiaformula2.com/fiaformula3.com — la ficha de cada
     *  piloto trae varias veces la misma foto de estudio recortada (retrato, sin marco de
     *  ficha para redes) en `<img src=".../{código}right.webp">` (F2) o
     *  `.../{código}right-1.webp` (F3, con un "-1" que F2 no tiene — comprobado con Brando
     *  Badoer). Se prefiere esa a la meta[og:image], que en estos dos sitios es una
     *  tarjeta 1200x630 con relleno de color — se ve mal recortada en el círculo de la
     *  fila. */
    private fun fetchOfficialPhoto(url: String): String? {
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html, url)
        return doc.select("img")
            .map { it.absUrl("src") }
            .firstOrNull { RIGHT_PHOTO_SUFFIX.containsMatchIn(it) }
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

    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) para poder testear el
    // parsing con un fixture HTML real sin red — ver DriverDbStandingsSourceTest. Ya
    // tomaba un Element en vez de una URL, así que no hace falta separar nada más.
    internal fun parseDriverRows(table: Element, nowUtc: Long): List<StandingEntity> {
        val headers = table.select("th").map { it.text().trim() }

        val posIndex = headers.indexOfFirst { it.contains("Pos", ignoreCase = true) }
        val driverIndex = headers.indexOfFirst {
            it.contains("Driver", ignoreCase = true) &&
                !it.contains("No", ignoreCase = true) &&
                !it.contains("Rating", ignoreCase = true)
        }.let { if (it >= 0) it else 1 }
        val teamIndex = headers.indexOfFirst { it.contains("Team", ignoreCase = true) }
        val pointsIndex = headers.indexOfFirst {
            it.contains("Points", ignoreCase = true) || it.contains("Pts", ignoreCase = true) || it.contains("Champ", ignoreCase = true)
        }
        if (pointsIndex < 0) return emptyList()

        return table.select("tbody tr").mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapIndexedNotNull null

            val driverCell = cells.getOrNull(driverIndex) ?: return@mapIndexedNotNull null
            // El nombre suele venir en un <a> que enlaza al perfil del piloto — si no está,
            // se cae al texto completo de la celda. Nunca se descarta la fila por no tener
            // foto ni por tener 0 puntos: solo si de verdad no hay nombre.
            val rawName = driverCell.selectFirst("a")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: driverCell.text().trim()
            val name = rawName.replace(Regex("^#?\\d+\\s+"), "").trim()
            if (name.isBlank()) return@mapIndexedNotNull null

            // driverdb usa una foto placeholder fija para pilotos sin foto real — se trata
            // como "sin foto" (icono por defecto en la UI) en vez de mostrar esa imagen gris.
            // 30/08/2026: el rediseño de driverdb.com sirve las imágenes a través de su
            // optimizador de Next.js ("/_next/image?url=<url real codificada>&w=...") en vez
            // de una URL directa — la ruta del placeholder llega como "...%2Fdefault%2F
            // driver-profile.png..." (la barra "/" codificada como "%2F"), así que la
            // comprobación con la barra sin codificar ya no la detectaba y el icono gris se
            // guardaba como si fuera una foto real para TODOS los pilotos sin foto.
            val photoUrl = driverCell.selectFirst("img")?.absUrl("src")
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.contains("default/driver-profile", ignoreCase = true) &&
                        !it.contains("default%2Fdriver-profile", ignoreCase = true)
                }
                ?: driverPhotoUrls[normalize(name)]

            // driverdb muestra un guion largo ("—") en la celda de equipo cuando no tiene ese
            // dato para el piloto — es texto no vacío, así que sin este filtro se agrupaba
            // como si "—" fuera un nombre de equipo real (mismo caso que en
            // OfficialRosterStandingsSource, comprobado el 30/08/2026 en NASCAR Cup).
            val team = teamIndex.takeIf { it >= 0 }
                ?.let { cells.getOrNull(it)?.text()?.trim() }
                ?.takeUnless { it.matches(Regex("^[-–—]+$")) }
                .orEmpty()
            val points = cells.getOrNull(pointsIndex)?.text()?.trim()?.toDoubleOrNull() ?: 0.0
            val position = posIndex.takeIf { it >= 0 }
                ?.let { cells.getOrNull(it)?.text()?.trim()?.toIntOrNull() }
                ?: (index + 1)

            StandingEntity(
                category = category,
                standingsClass = StandingsClass.OVERALL,
                type = StandingType.DRIVER,
                entrantKey = "${category.name}-DRIVER-$name",
                position = position,
                name = name,
                team = team,
                points = points,
                photoUrl = photoUrl,
                updatedAtUtc = nowUtc
            )
        }
    }

    private companion object {
        /** Fichas de piloto en vuelo como mucho a la vez (ver officialProfileUrlTemplate) —
         *  mismo valor y mismo motivo que OfficialRosterStandingsSource. */
        const val MAX_PARALLEL_PHOTO_REQUESTS = 6

        /** "right.webp" (F2) o "right-1.webp"/"right-1.jpg" (F3) al final de la URL — ver
         *  fetchOfficialPhoto(). */
        val RIGHT_PHOTO_SUFFIX = Regex("right(-\\d+)?\\.(webp|jpg)$", RegexOption.IGNORE_CASE)
    }
}
