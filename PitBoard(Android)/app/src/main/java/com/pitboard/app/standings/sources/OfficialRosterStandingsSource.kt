package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Year

/**
 * Combina dos fuentes: una tabla "de autoridad" (`rosterUrl`) con posición, piloto y puntos
 * ya correctos — sin pilotos reserva, con el desempate real de la categoría ya aplicado — y,
 * opcionlmente, driverdb.com, que solo se usa para intentar emparejar foto y equipo por
 * nombre. Si driverdb no tiene un piloto que sí está en la tabla de autoridad (o el nombre
 * no coincide lo bastante), esa fila sale sin foto/equipo — pero nunca desaparece: quién
 * sale y en qué puntos/posición viene siempre de la tabla de autoridad, nunca de driverdb.
 *
 * 28/08/2026: usada por F1 (formula1.com) y NASCAR Cup — driverdb.com por sí solo incluía
 * pilotos reserva/sustitutos que no han corrido, y no siempre desempataba bien las
 * posiciones en caso de empate a puntos.
 *
 * 28/08/2026: además se intenta la foto oficial de la página de perfil de cada piloto antes
 * de recurrir a la de driverdb (ver officialProfileUrlTemplate) — a costa de una petición
 * HTTP extra por piloto en cada sincronización.
 *
 * 28/08/2026: el nombre que trae la tabla de autoridad a veces viene pegado al código de 3
 * letras del piloto sin espacio de por medio (ej. "Kimi AntonelliANT" en vez de "Kimi
 * Antonelli") porque en el HTML de origen el nombre y el código son elementos separados sin
 * espacio real entre ellos — se limpia con una expresión regular antes de usar el nombre
 * para nada (mostrarlo, buscar su foto oficial o emparejarlo con driverdb).
 */
open class OfficialRosterStandingsSource(
    override val category: StandingsCategory,
    private val rosterUrl: String,
    private val driverDbSlug: String?,
    /** Plantilla de URL de perfil oficial por piloto, con "{slug}" como marcador del nombre
     *  convertido a slug (ej. "https://www.formula1.com/en/drivers/{slug}"). Si se indica, su
     *  foto (etiqueta og:image) tiene prioridad sobre la de driverdb — suele ser más
     *  profesional/oficial — pero si falla para un piloto concreto, se usa igualmente la de
     *  driverdb como respaldo antes de quedarse sin foto.
     *
     *  02/09/2026: NASCAR Cup ya NO usa esto (ver rosterPhotoUrlExtractor) — nascar.com
     *  resultó poco fiable página a página: Ty Gibbs redirige a un archivo de noticias en vez
     *  de a su ficha (así que la foto salía siendo la miniatura de una noticia cualquiera),
     *  Casey Mears trae una foto de 2017 sin actualizar, y B.J. McLeod ni siquiera tiene esa
     *  URL (404). Se mantiene aquí solo para F1 (formula1.com), que sí es consistente. */
    private val officialProfileUrlTemplate: String? = null,
    /** Extrae la foto DIRECTAMENTE de la celda del nombre en la tabla de autoridad — sin
     *  ninguna petición HTTP extra — en vez de visitar una página de perfil aparte. Tiene
     *  prioridad sobre [officialProfileUrlTemplate] cuando ambos están presentes.
     *
     *  02/09/2026: la usa NASCAR Cup — la fila de espn.com/racing/standings ya trae un enlace
     *  `/racing/driver/_/id/{id}/{slug}` con el id de ESPN de cada piloto, que compuesto con
     *  la URL de su CDN de fotos (a.espncdn.com/combiner/i?img=/i/headshots/rpm/players/full/
     *  {id}.png) da una foto de estudio fiable para los ~40 pilotos de golpe, incluidos los de
     *  temporada parcial que nascar.com no siempre tiene bien mantenidos. Comprobada a mano
     *  con Gibbs, Mears y McLeod — las 3 correctas y profesionales. */
    private val rosterPhotoUrlExtractor: ((nameCell: Element) -> String?)? = null
) : StandingsSource {

    private data class RosterRow(val name: String, val points: Double, val photoUrl: String? = null)
    private data class Enrichment(val photoUrl: String?, val team: String)

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> = coroutineScope {
        val rosterRows = fetchRoster()
        if (rosterRows.isEmpty()) return@coroutineScope emptyList()

        val enrichment = driverDbSlug
            ?.let { runCatching { fetchDriverDbEnrichment(it) }.getOrElse { emptyMap() } }
            .orEmpty()

        // 30/08/2026 (fase 3, robustez de red). Antes las fotos de perfil se pedían DENTRO
        // del mapIndexed de abajo, es decir una detrás de otra: 23 peticiones a
        // formula1.com en serie. Con la web lenta o algún perfil que no responde, esa suma
        // se comía el tiempo entero de la sincronización y F1 acababa fallando por timeout
        // aunque su tabla de puntos se hubiera leído bien. Ahora van en paralelo, con un
        // semáforo de 6 en vuelo a la vez: rápido, pero sin abrir 23 conexiones de golpe
        // contra el mismo servidor (eso es justo lo que suele contestarse con un 429/403).
        //
        // OJO con el detalle de OkHttp: sus límites de concurrencia por host
        // (maxRequestsPerHost) solo se aplican a las llamadas ASÍNCRONAS (enqueue), no a
        // execute() bloqueante como el que usamos aquí — así que sin este semáforo no
        // habría ningún tope. Cada foto sigue siendo "mejor esfuerzo": si una falla, esa
        // fila se queda con la de driverdb (o sin foto), nunca desaparece.
        val officialPhotos: List<String?> = when {
            // Ya viene extraída de la propia tabla de autoridad (ver rosterPhotoUrlExtractor)
            // — sin peticiones extra, así que ni pasa por el semáforo de abajo.
            rosterRows.any { it.photoUrl != null } -> rosterRows.map { it.photoUrl }
            officialProfileUrlTemplate != null -> {
                val gate = Semaphore(MAX_PARALLEL_PHOTO_REQUESTS)
                rosterRows.map { row ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            runCatching { fetchProfilePhoto(officialProfileUrlTemplate.replace("{slug}", slugify(row.name))) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            else -> List(rosterRows.size) { null }
        }

        // La posición se renumera 1..N según el orden en que ya vienen listadas las filas
        // (el orden real de clasificación de la tabla de autoridad) en vez de fiarse de la
        // columna "Pos"/"Rank" del HTML de origen — esa columna puede traer huecos o números
        // repetidos en caso de empate (ej. espn.com/racing/standings para NASCAR).
        val driverRows = rosterRows.mapIndexed { index, row ->
            val match = enrichment.entries.firstOrNull { (key, _) -> namesMatch(row.name, key) }?.value
            val officialPhoto = officialPhotos[index]
            StandingEntity(
                category = category,
                standingsClass = StandingsClass.OVERALL,
                type = StandingType.DRIVER,
                entrantKey = "${category.name}-DRIVER-${row.name}",
                position = index + 1,
                name = row.name,
                team = match?.team.orEmpty(),
                points = row.points,
                photoUrl = officialPhoto ?: match?.photoUrl,
                updatedAtUtc = nowUtc
            )
        }

        // Equipos: se agrupan los pilotos por el equipo emparejado desde driverdb y se suman
        // sus puntos — igual que en DriverDbStandingsSource, ninguna de las dos fuentes trae
        // una tabla de constructores separada en esta plantilla.
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
                    photoUrl = TEAM_LOGO_URLS[normalize(teamName)],
                    updatedAtUtc = nowUtc
                )
            }

        driverRows + teamRows
    }

    /** Cabeceras de una tabla: `<th>` si los hay (caso normal), y si no, la primera fila
     *  cuyas celdas `<td>` incluyan la palabra "Driver" (caso espn.com/racing/standings,
     *  28/08/2026 — su tabla no usa `<th>`, y la primera fila real es un título con una sola
     *  celda que solo dice "Standings", así que no vale con coger "la primera fila"). */
    private fun headerCells(table: Element): List<Element> {
        val ths = table.select("th")
        if (ths.isNotEmpty()) return ths
        return table.select("tr")
            .firstOrNull { row -> row.select("td").any { it.text().contains("Driver", ignoreCase = true) } }
            ?.select("td")
            .orEmpty()
    }

    private fun fetchRoster(): List<RosterRow> {
        val html = fetchHtml(rosterUrl)
        val doc = Jsoup.parse(html, rosterUrl)
        val table = doc.select("table").firstOrNull { t ->
            headerCells(t).any { it.text().contains("Driver", ignoreCase = true) }
        } ?: return emptyList()

        val headers = headerCells(table).map { it.text().trim() }
        val nameIndex = headers.indexOfFirst { it.contains("Driver", ignoreCase = true) }
            .let { if (it >= 0) it else 1 }
        val pointsIndex = headers.indexOfFirst { it.contains("Points", ignoreCase = true) || it.contains("Pts", ignoreCase = true) }
        if (pointsIndex < 0) return emptyList()

        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null

            val nameCell = cells.getOrNull(nameIndex) ?: return@mapNotNull null
            val rawName = nameCell.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: nameCell.text().trim()
            val name = cleanDriverName(rawName)
            if (name.isBlank()) return@mapNotNull null

            // Exigir que los puntos se puedan interpretar como número descarta de forma
            // natural la propia fila de cabecera cuando la tabla no tiene <th> (ej. espn.com:
            // esa fila también tiene celdas "DRIVER"/"POINTS"/etc. con la misma forma que una
            // fila real, pero "POINTS" no es un número).
            val points = cells.getOrNull(pointsIndex)?.text()?.trim()?.toDoubleOrNull()
                ?: return@mapNotNull null

            val photoUrl = rosterPhotoUrlExtractor?.invoke(nameCell)

            RosterRow(name, points, photoUrl)
        }
    }

    private fun fetchDriverDbEnrichment(slug: String): Map<String, Enrichment> {
        val url = "https://www.driverdb.com/championships/$slug/${Year.now().value}/standings"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html, url)
        val table = doc.select("table").firstOrNull { t ->
            t.select("th").any { it.text().contains("Driver", ignoreCase = true) }
        } ?: return emptyMap()

        val headers = table.select("th").map { it.text().trim() }
        val driverIndex = headers.indexOfFirst {
            it.contains("Driver", ignoreCase = true) &&
                !it.contains("No", ignoreCase = true) &&
                !it.contains("Rating", ignoreCase = true)
        }.let { if (it >= 0) it else 1 }
        val teamIndex = headers.indexOfFirst { it.contains("Team", ignoreCase = true) }

        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null
            val driverCell = cells.getOrNull(driverIndex) ?: return@mapNotNull null

            val rawName = driverCell.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: driverCell.text().trim()
            val name = cleanDriverName(rawName)
            if (name.isBlank()) return@mapNotNull null

            val photoUrl = driverCell.selectFirst("img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() && !it.contains("default/driver-profile", ignoreCase = true) }
            // driverdb muestra un guion largo ("—") en la celda de equipo cuando no tiene ese
            // dato para el piloto (ej. pilotos de una sola carrera) — es texto no vacío, así
            // que sin este filtro se agrupaba como si "—" fuera un nombre de equipo real
            // (comprobado el 30/08/2026 con Jimmie Johnson y B.J. McLeod en NASCAR Cup).
            val team = teamIndex.takeIf { it >= 0 }
                ?.let { cells.getOrNull(it)?.text()?.trim() }
                ?.takeUnless { it.matches(Regex("^[-–—]+$")) }
                .orEmpty()

            name to Enrichment(photoUrl, team)
        }.toMap()
    }

    /** Quita el prefijo de dorsal ("#4 ", "12 ") si lo hay y, sobre todo, el código de 3
     *  letras mayúsculas que algunas webs (formula1.com incluida) pegan sin espacio justo
     *  detrás del apellido (ej. "Kimi AntonelliANT" -> "Kimi Antonelli", "Max
     *  VerstappenVER" -> "Max Verstappen"). Solo se quita si va pegado a una letra minúscula
     *  justo antes, para no recortar apellidos que legítimamente terminen en mayúsculas. */
    private fun cleanDriverName(raw: String): String =
        raw.replace(Regex("^#?\\d+\\s+"), "")
            .replace(Regex("(?<=[a-zà-öø-ÿ])[A-Z]{3}$"), "")
            .trim()

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

    /** Lee la página de perfil oficial de un piloto y extrae la foto de la etiqueta
     *  meta[property=og:image] — presente en el HTML servido (no requiere JavaScript) en las
     *  páginas de perfil individuales, a diferencia del listado general de pilotos. Si la
     *  página no existe para ese slug o no trae la etiqueta, devuelve null y esa fila se queda
     *  con la foto de driverdb (o sin foto) como respaldo. */
    private fun fetchProfilePhoto(url: String): String? {
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html, url)
        return doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun slugify(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")

    /** Emparejamiento de nombres "mejor esfuerzo" entre dos webs distintas (ej. "Kimi
     *  Antonelli" en una vs "Andrea Kimi Antonelli" en otra): se normaliza a minúsculas sin
     *  signos y se acepta si uno contiene al otro. Puede fallar en casos raros — en ese caso
     *  la fila se queda sin foto/equipo, nunca desaparece. */
    private fun namesMatch(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb || na.contains(nb) || nb.contains(na)) return true
        // 30/08/2026: pilotos con iniciales (ej. NASCAR: "AJ Allmendinger" en espn.com vs
        // "A. J. Allmendinger" en driverdb.com) quitan los puntos en normalize() pero se
        // quedan con espacios en sitios distintos ("aj allmendinger" vs "a j allmendinger")
        // — ninguna de las dos contiene a la otra aunque sean la misma persona. Como último
        // recurso se comparan también sin ningún espacio, que sí coincide en ese caso.
        val ca = na.replace(" ", "")
        val cb = nb.replace(" ", "")
        return ca == cb || ca.contains(cb) || cb.contains(ca)
    }

    // 30/08/2026: antes esta función solo borraba cualquier carácter que no fuera a-z0-9 —
    // eso incluía las tildes, así que las quitaba en vez de convertir la letra a su base
    // (ej. "Hülkenberg" -> "hlkenberg" en vez de "hulkenberg"). Si una fuente escribe el
    // nombre con tilde y la otra sin ella (pasaba con Hülkenberg/Hulkenberg y Pérez/Perez
    // entre driverdb.com y formula1.com), las dos cadenas normalizadas ya no coincidían y
    // esa fila se quedaba sin equipo/foto — y, peor, sin sumar sus puntos al total del
    // equipo en la clasificación de constructores. Normalizer.Form.NFD separa cada letra
    // con tilde en la letra base + su marca diacrítica como carácter aparte (categoría
    // Unicode Mn), así que se puede quitar solo la marca y quedarse con la base.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        /** Peticiones de foto de perfil simultáneas como mucho (ver fetch()). 6 es un
         *  equilibrio: reduce 23 peticiones en serie a ~4 tandas sin parecer un scraper
         *  agresivo para formula1.com. */
        private const val MAX_PARALLEL_PHOTO_REQUESTS = 6

        /** Logos oficiales de equipo, por nombre normalizado — el nombre de equipo que
         *  usamos viene de driverdb.com (ver Enrichment), y coincide con estas claves. Un
         *  único mapa compartido por todas las categorías que usan esta fuente (F1, NASCAR
         *  Cup): las claves de una y otra categoría no chocan entre sí. Lo que no tenga
         *  coincidencia aquí simplemente se queda sin logo de equipo, como antes. */
        // 28/08/2026 (2): las URLs de Wikimedia se cambian por las oficiales de
        // formula1.com — comprobado que TODAS las de Wikimedia estaban rotas (pedían un
        // ancho de miniatura, "400px", que Wikimedia no permite en peticiones directas como
        // las que hace la app; solo deja unos anchos fijos: 250, 330, 500...) y que además
        // McLaren y Red Bull llevaban un fondo sólido incrustado en el propio archivo, no
        // eran transparentes de verdad. Las de formula1.com son la temporada 2026 oficial,
        // mismo formato para los 11 equipos (400x400) y confirmado fondo transparente real.
        // 29/08/2026: se añaden las 15 organizaciones a tiempo completo de NASCAR Cup 2026
        // (nascar.com/nascar-cup-series-teams — verificadas una a una: cargan y son de buena
        // resolución). No todas son transparentes (dos son .jpg, sin canal alfa), pero ya no
        // hace falta — el círculo de fondo ahora es blanco fijo para todos los equipos.
        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "mercedes" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/mercedes/2026mercedeslogo.webp",
            "ferrari" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/ferrari/2026ferrarilogo.webp",
            "mclaren" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/mclaren/2026mclarenlogo.webp",
            "red bull" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/redbullracing/2026redbullracinglogo.webp",
            "alpine" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/alpine/2026alpinelogo.webp",
            "racing bulls" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/racingbulls/2026racingbullslogo.webp",
            "haas" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/haasf1team/2026haasf1teamlogo.webp",
            "audi" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/audi/2026audilogo.webp",
            "williams" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/williams/2026williamslogo.webp",
            "aston martin" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/astonmartin/2026astonmartinlogo.webp",
            "cadillac" to "https://media.formula1.com/image/upload/c_fit,h_400/q_auto/v1740000001/common/f1/2026/cadillac/2026cadillaclogo.webp",
            "23xi racing" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/23XI-Solid-Racing-Red.png",
            "front row motorsports" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/04/01/FRM-logo-full-color.png",
            "haas factory team" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/Haas-Factory-Team-1.png",
            "hendrick" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Hendrick_Motorsports_Logo.svg.png",
            "hyak motorsports" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/HYAK_Final81.jpg",
            "joe gibbs" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/JGR-Block-Logo.png",
            "kaulig racing" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Black-Stacked.png",
            "legacy" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/27/LegacyMC_Global_OnWhite-RGB_2026.png",
            "richard childress" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/RCR_Updated-2.png",
            "rick ware" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/RWRwithText.png.png",
            "rfk racing" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/RFK_logo-443x189-1.jpg",
            "spire motorsports" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/Spire_HorizontalBadge.png",
            "trackhouse" to "https://www.nascar.com/wp-content/uploads/sites/7/2021/01/02/Trackhouse_light.png",
            "team penske" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/18/Penskelogo1.png",
            "wood brothers" to "https://www.nascar.com/wp-content/uploads/sites/7/2026/01/17/WoodBrothersPrimary-Logo.png"
        )
    }
}
