package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import com.pitboard.app.standings.StandingsSource
import com.squareup.moshi.Json
import com.squareup.moshi.Types
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Year

/**
 * Analizador HTML genérico para páginas de clasificación con una tabla <table> normal
 * (posición, piloto/equipo, puntos). 30/08/2026: hoy su única subclase es MotoGP
 * (autosport.com) — IndyCar, F1 Academy, NASCAR Cup y Porsche Supercup, que antes también
 * lo usaban, han ido pasando a fuentes propias.
 *
 * HONESTO: esto es "leer una página web", no una API — si alguno de estos sitios cambia
 * el diseño de su tabla, esa categoría concreta dejará de funcionar y habrá que ajustar
 * los selectores. No he podido verificar los nombres exactos de clases CSS byte a byte
 * (solo el contenido de las tablas), así que el reparto piloto/equipo dentro de la celda
 * de nombre es una heurística — si esto viene mal en la práctica, es lo primero a revisar.
 */
open class MotorsportStandingsHtmlSource(
    override val category: StandingsCategory,
    private val driverUrl: String,
    private val teamUrl: String?,
    private val standingsClass: StandingsClass = StandingsClass.OVERALL,
    // Si se indica, se descarta a cualquier piloto que no aparezca en esta página de
    // referencia (parrilla confirmada de la temporada) — evita pilotos reserva/wildcard/
    // test que sí aparecen en la tabla de puntos con 0. Ver RosterNameFilter.
    private val knownRosterUrl: String? = null,
    // Fotos de piloto por clave "inicial + apellido" (ver photoKey) — un mapa fijo, igual
    // que teamLogoUrls, porque la tabla de puntos de autosport.com no trae ninguna imagen.
    // Lo que no tenga entrada aquí se queda sin foto, nunca desaparece de la clasificación.
    private val driverPhotoUrls: Map<String, String> = emptyMap(),
    // Logos de equipo por nombre normalizado, aplicados a las filas TEAM (ver
    // MotoGpStandingsSource) — igual que TEAM_LOGO_URLS en las demás fuentes.
    private val teamLogoUrls: Map<String, String> = emptyMap(),
    /** 03/09/2026: Moto2/Moto3 — a diferencia de MotoGP (mapa fijo hallado a mano), aquí se
     *  resuelven fotos de piloto Y logos de equipo EN VIVO en cada sincronización desde la
     *  API interna de motogp.com (api.pulselive.motogp.com/motogp/v1/riders), con el UUID
     *  de categoría correspondiente — comprobado a mano el 03/09/2026 (Moto2:
     *  "ea854a67-73a4-4a28-ac77-d67b3b2a530a", Moto3: "1ab203aa-e292-4842-8bed-971911357af1",
     *  sacados de .../v1/categories?seasonYear=2026). Un único piloto sin equipo/foto no
     *  tumba nada — si la API fallara entera, se cae a [driverPhotoUrls]/[teamLogoUrls]
     *  (vacíos aquí, así que las filas se quedan con el icono por defecto). Tiene prioridad
     *  sobre esos dos mapas fijos cuando ambos traen algo para el mismo piloto/equipo. */
    private val pulseliveCategoryUuid: String? = null
) : StandingsSource {

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val (livePhotos, liveLogos) = pulseliveCategoryUuid
            ?.let { runCatching { fetchPulseliveMaps(it) }.getOrNull() }
            ?: (emptyMap<String, String>() to emptyMap<String, String>())
        val effectiveDriverPhotoUrls = driverPhotoUrls + livePhotos
        val effectiveTeamLogoUrls = teamLogoUrls + liveLogos
        // El equipo es "best effort": si esta categoría no separa pilotos/equipos, o la
        // URL/plantilla de la página de equipos falla, simplemente no hay filas de equipo
        // — nunca debe tirar abajo la lista de pilotos, que es lo más importante. Se pide
        // una sola vez y se reutiliza tanto para las filas TEAM como para separar nombre
        // de piloto y equipo en las filas DRIVER (ver parseRow).
        val teamParsedRows = teamUrl?.let { url ->
            runCatching { parseTable(url) }.getOrElse { emptyList() }
        }.orEmpty()

        // autosport.com no separa "J. Martin Aprilia Racing Team" en piloto y equipo con
        // ninguna marca HTML — es un único bloque de texto. En vez de adivinar dónde
        // cortar, usamos los nombres de equipo reales (ya descargados arriba) y miramos
        // si el texto del piloto termina en uno de ellos. Se ordenan del más largo al más
        // corto para que un nombre de equipo que sea prefijo de otro más largo no corte
        // en el sitio equivocado.
        val knownTeamNames = teamParsedRows.map { it.name }.filter { it.isNotBlank() }.sortedByDescending { it.length }

        // Filtro de reservas: si hay página de referencia, solo se quedan los pilotos que
        // aparecen en ella (mejor esfuerzo — si no se pudo obtener, no se filtra nada) — las
        // posiciones se vuelven a numerar tras filtrar para no dejar huecos.
        val knownRiderNames = knownRosterUrl?.let { runCatching { RosterNameFilter.fetchKnownNames(it) }.getOrElse { emptySet() } }.orEmpty()
        val driverParsedRows = RosterNameFilter.filterKeepingReal(parseTable(driverUrl, knownTeamNames), knownRiderNames) { it.name }

        val driverRows = driverParsedRows.mapIndexed { index, row ->
            val photoUrl = effectiveDriverPhotoUrls[photoKey(row.name)]
            toEntity(StandingType.DRIVER, index, row.copy(photoUrl = photoUrl), nowUtc)
        }

        val teamRows = teamParsedRows.mapIndexed { index, row ->
            val logoUrl = effectiveTeamLogoUrls[normalize(row.name)]
            toEntity(StandingType.TEAM, index, row.copy(team = "", photoUrl = logoUrl), nowUtc)
        }

        return driverRows + teamRows
    }

    private fun toEntity(type: StandingType, index: Int, row: ParsedRow, nowUtc: Long) = StandingEntity(
        category = category,
        standingsClass = standingsClass,
        type = type,
        entrantKey = "${category.name}-$type-${row.name}",
        position = index + 1,
        name = row.name,
        team = row.team,
        points = row.points,
        photoUrl = row.photoUrl,
        updatedAtUtc = nowUtc
    )

    /** Clave del mapa [driverPhotoUrls]: inicial del nombre de pila + apellido completo, sin
     *  tildes ni signos. Existe porque las dos formas en que se puede escribir el mismo
     *  piloto tienen que dar la misma clave: autosport.com abrevia el nombre de pila en su
     *  tabla de puntos ("J. Martin", "F. Di Giannantonio", "R. Fernández") mientras que el
     *  mapa se escribe con el nombre completo tal como lo publica motogp.com ("Jorge
     *  Martin", "Fabio Di Giannantonio", "Raul Fernandez") — las tres pasan a "j martin",
     *  "f di giannantonio" y "r fernandez".
     *
     *  Se conserva el apellido ENTERO (todo menos la primera palabra), no solo la última
     *  palabra, para no romper los apellidos compuestos ("Di Giannantonio"). Y se conserva
     *  la inicial del nombre de pila, no solo el apellido, porque en la parrilla hay
     *  hermanos y homónimos: Marc y Alex Marquez ("m marquez" vs "a marquez") y Raul y
     *  Augusto Fernandez ("r fernandez" vs "a fernandez") — sin la inicial, uno se llevaría
     *  la foto del otro. Comprobado que las 22 claves de la parrilla 2026 son únicas. */
    private fun photoKey(name: String): String {
        val parts = normalize(name).split(" ").filter { it.isNotBlank() }
        if (parts.size < 2) return parts.joinToString(" ")
        return parts.first().take(1) + " " + parts.drop(1).joinToString(" ")
    }

    // 30/08/2026: normaliza también las tildes a su letra base (ver el mismo fix en
    // OfficialRosterStandingsSource). Aquí es imprescindible, no solo por consistencia: la
    // usa photoKey(), y autosport.com escribe "R. Fernández" y "M. Viñales" con tilde
    // mientras que las claves del mapa de fotos van sin ella.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    // internal (no protected): solo se usa dentro de esta misma clase, y protected no puede
    // devolver un tipo internal (ParsedRow) — el compilador lo rechaza porque una subclase
    // fuera del módulo podría ver la firma sin poder ver el tipo que devuelve.
    private fun parseTable(url: String, knownTeamNames: List<String> = emptyList()): List<ParsedRow> {
        val html = fetchHtml(url)
        return parseTableHtml(html, knownTeamNames)
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

    // 04/09/2026 (Fase 1 del diagnóstico): separado de parseTable()/fetchHtml() para poder
    // testear el parsing contra un fixture HTML real sin red — ver
    // MotorsportStandingsHtmlSourceTest. internal (no protected) para que el test, en el mismo
    // módulo, pueda instanciar la clase directamente sin necesitar una subclase.
    internal fun parseTableHtml(html: String, knownTeamNames: List<String> = emptyList()): List<ParsedRow> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { table ->
            table.select("th").any {
                val text = it.text()
                text.contains("Driver", ignoreCase = true) ||
                    text.contains("Team", ignoreCase = true) ||
                    // MotoGP usa "Rider" en vez de "Driver" — sin esto, esta tabla nunca
                    // se encontraba y la categoría se quedaba sin datos para siempre.
                    text.contains("Rider", ignoreCase = true)
            }
        } ?: return emptyList()

        // Localizamos la columna de puntos por su encabezado en vez de adivinar "el
        // primer número que aparezca tras el nombre" — eso fallaba en Porsche Supercup
        // (cogía el número de coche del piloto, que también es un td numérico y va antes
        // que los puntos reales de campeonato).
        val pointsColumnIndex = table.select("th").indexOfFirst {
            val text = it.text()
            text.contains("Points", ignoreCase = true) || text.contains("Pts", ignoreCase = true)
        }.let { if (it >= 0) it else null }

        return table.select("tbody tr").mapNotNull { parseRow(it, pointsColumnIndex, knownTeamNames) }
    }

    private fun parseRow(row: Element, pointsColumnIndex: Int?, knownTeamNames: List<String>): ParsedRow? {
        val cells = row.select("td")
        if (cells.isEmpty()) return null

        val nameCellIndex = cells.indexOfFirst { it.selectFirst("a") != null }
            .let { if (it >= 0) it else 1 }
        val nameCell = cells.getOrNull(nameCellIndex) ?: return null

        // Heurística: si la celda de nombre tiene varios elementos hijos (ej. un <span>
        // para el piloto y otro para el equipo), se cogen por separado; si no, se intenta
        // cortar por el nombre de equipo conocido (ver fetch()), y si ninguno de los dos
        // aplica, todo el texto se queda como "name" y el equipo se deja vacío.
        val children = nameCell.children()
        val (name, team) = if (children.size >= 2) {
            children[0].text().trim() to children[1].text().trim()
        } else {
            val rawText = nameCell.text().trim()
            val matchedTeam = knownTeamNames.firstOrNull { rawText.endsWith(it) && rawText.length > it.length }
            if (matchedTeam != null) {
                rawText.removeSuffix(matchedTeam).trim() to matchedTeam
            } else {
                rawText to ""
            }
        }
        if (name.isBlank()) return null

        // Con la columna de puntos localizada por encabezado, una celda vacía (categoría
        // todavía sin resultados esta temporada, ej. ELMS entre carreras) cuenta como 0
        // en vez de descartar al piloto entero de la clasificación. Si no encontramos la
        // columna por nombre (formato desconocido), caemos al comportamiento anterior.
        val points = if (pointsColumnIndex != null) {
            cells.getOrNull(pointsColumnIndex)?.text()?.trim()?.toDoubleOrNull() ?: 0.0
        } else {
            cells.drop(nameCellIndex + 1)
                .firstNotNullOfOrNull { it.text().trim().toDoubleOrNull() }
                ?: return null
        }

        return ParsedRow(name, team, points)
    }

    /** Fotos de piloto (clave = photoKey, ver arriba) y logos de equipo (clave =
     *  normalize(nombre)) sacados de la API interna de motogp.com — ver
     *  [pulseliveCategoryUuid]. Solo se cuentan los pilotos "en parrilla"
     *  (`in_grid == true`) — esa misma API lista también bajas/reservas fuera de la
     *  temporada actual. */
    private fun fetchPulseliveMaps(categoryUuid: String): Pair<Map<String, String>, Map<String, String>> {
        val url = "https://api.pulselive.motogp.com/motogp/v1/riders?category=$categoryUuid&season=${Year.now().value}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val json = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        val listType = Types.newParameterizedType(List::class.java, PulseliveRider::class.java)
        val adapter = StandingsMoshi.instance.adapter<List<PulseliveRider>>(listType)
        val riders = adapter.fromJson(json).orEmpty()

        val photos = mutableMapOf<String, String>()
        val logos = mutableMapOf<String, String>()
        riders.forEach { rider ->
            val step = rider.currentCareerStep ?: return@forEach
            if (step.inGrid != true) return@forEach
            val name = rider.name?.trim().orEmpty()
            val surname = rider.surname?.trim().orEmpty()
            if (name.isNotBlank() && surname.isNotBlank()) {
                step.pictures?.profile?.main?.trim()?.takeIf { it.isNotBlank() }?.let { photo ->
                    photos[photoKey("$name $surname")] = photo
                }
            }
            val teamName = step.team?.name?.trim()
            val teamLogo = step.team?.picture?.trim()
            if (!teamName.isNullOrBlank() && !teamLogo.isNullOrBlank()) {
                logos[normalize(teamName)] = teamLogo
            }
        }
        return photos to logos
    }

    // internal (no protected) por el mismo motivo que parseTableHtml() arriba.
    internal data class ParsedRow(val name: String, val team: String, val points: Double, val photoUrl: String? = null)
}

private data class PulseliveRider(
    val name: String?,
    val surname: String?,
    @Json(name = "current_career_step") val currentCareerStep: PulseliveCareerStep?
)

private data class PulseliveCareerStep(
    @Json(name = "in_grid") val inGrid: Boolean?,
    val team: PulseliveTeamRef?,
    val pictures: PulselivePictures?
)

private data class PulseliveTeamRef(val name: String?, val picture: String?)
private data class PulselivePictures(val profile: PulseliveProfilePic?)
private data class PulseliveProfilePic(val main: String?)