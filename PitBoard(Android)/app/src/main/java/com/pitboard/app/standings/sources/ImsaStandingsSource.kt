package com.pitboard.app.standings.sources

import com.pitboard.app.standings.CarDriverEntity
import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Year

/**
 * IMSA WeatherTech SportsCar Championship: igual que ELMS, se trata "por coche" dentro
 * de sus 4 clases (GTP, LMP2, GTD Pro, GTD — LMP3 salió del campeonato principal para
 * 2026). A diferencia de ELMS, no hay una única página con todo: comprobado a mano el
 * 02/09/2026 contra la web real, con curl (no es una suposición).
 *
 * NO implementa StandingsSource: esa interfaz devuelve solo `List<StandingEntity>`, pero
 * aquí el logo de equipo (que va EN esas filas) sale de la misma página que los pilotos
 * (ver más abajo), así que separar "clasificación" y "pilotos" en dos fuentes
 * independientes habría obligado a repetir la visita a las ~50 páginas de equipo dos
 * veces, o a que una fuente dependiera del resultado de la otra a medio terminar. Con
 * todo en una sola clase, cada equipo se visita una vez y las dos salidas
 * (`standings` + `carDrivers`) salen ya completas. StandingsRepository la trata como una
 * rama de sincronización aparte, con el mismo aislamiento de fallos que las demás.
 *
 * 1) CLASIFICACIÓN POR COCHE — imsa.com usa un widget AJAX de WordPress:
 *    `POST https://www.imsa.com/wp-admin/admin-ajax.php` con
 *    `action=getImsaStandings&standings=team&seriesId=1&currentYear={año}&classId={id}`.
 *    La respuesta es una tabla HTML con `#NN Team Name` por fila (número de coche +
 *    equipo) y el total de puntos en `td.totalpoints`. El `classId` de cada clase no
 *    está hardcodeado: se resuelve por su `shortcode` ("GTP", "LMP2"...) contra
 *    `https://dvw6yynr86g3k.cloudfront.net/galaxy/api/classes?series_id=1&season_id={año}`,
 *    una API JSON pública y estable — así que si IMSA renumerase los ids de clase de una
 *    temporada a otra, esto se sigue resolviendo solo. Solo si esa API fallara se cae a
 *    los ids comprobados hoy (FALLBACK_CLASS_IDS).
 *
 * 2) PILOTOS + LOGO POR COCHE — cada fila anterior enlaza a
 *    `imsa.com/racing-teams/{slug}/`, y esa página trae:
 *      - `.team-logos img`: 2-3 logos (serie/equipo/fabricante). CORREGIDO 02/09/2026:
 *        el primer intento asumía que el logo de equipo era el único cuya URL contiene
 *        "TeamLogo" — cierto para "13 Autosport" (2026_13Autosport_TeamLogo_282x188.png)
 *        pero NO para el resto: comprobado en 6 equipos reales (AF Corse, BMW Team RLL,
 *        Aston Martin THOR, Sun Energy 1, Meyer Shank, 13 Autosport) que el nombre de
 *        archivo del logo de equipo varía sin ningún patrón común. Lo que SÍ es
 *        constante en los 6: la 1ª imagen es siempre el logo fijo de la serie
 *        ("weathertech_championship.png") y la 2ª es siempre el logo del equipo (a
 *        veces "nologo_0.jpg" — el propio placeholder de IMSA para un equipo sin logo
 *        real, que aquí se trata como "sin logo" igual que el resto de la app). Ahora se
 *        descartan esas dos por nombre (serie fija / placeholder) y se coge la primera
 *        que quede, en vez de buscar "TeamLogo" en la URL.
 *      - Una tarjeta `div.imsa-card_item_widget` por piloto, con `img[data-src]` (la
 *        foto real — el `src` es un placeholder gris de carga perezosa) y
 *        `p.imsa-ciw-title` (nombre).
 *    Se descartó agrupar pilotos por el `man-id-NNNN` que trae la tabla de PILOTOS de
 *    imsa.com/weathertech/standings/: comprobado que ese id es del FABRICANTE (ej.
 *    Porsche), no del coche — un fabricante con 2 coches en la misma clase agruparía
 *    mal a sus pilotos. Por eso todo sale de la tabla de EQUIPOS + su página, nunca de
 *    esa tabla de pilotos.
 *
 * HONESTO sobre el coste: a diferencia de ELMS (una sola página, 140 pilotos), aquí hace
 * falta una petición por coche — unos 45-50 en total. Para no golpear el servidor de
 * golpe se lanzan en bloques de 8 en paralelo, nunca las ~50 a la vez. Un fallo al
 * visitar la página de un coche concreto solo deja a ESE coche sin logo/pilotos (sigue
 * apareciendo en la clasificación, con el icono por defecto) — nunca tumba la
 * sincronización completa.
 */
class ImsaStandingsSource {

    val category = StandingsCategory.IMSA

    suspend fun fetch(nowUtc: Long): ImsaFetchResult {
        val year = Year.now().value
        val classIds = resolveClassIds(year)

        val teamRows = CLASS_SHORTCODES.entries.flatMap { (standingsClass, shortcode) ->
            val classId = classIds[shortcode] ?: FALLBACK_CLASS_IDS.getValue(standingsClass)
            runCatching { fetchClassTeamRows(standingsClass, classId, year) }.getOrElse { emptyList() }
        }

        val teamPages = coroutineScope {
            teamRows.chunked(TEAM_PAGE_CONCURRENCY).flatMap { chunk ->
                chunk.map { row -> async(Dispatchers.IO) { row to row.teamUrl?.let { fetchTeamPage(it, row, nowUtc) } } }
                    .awaitAll()
            }
        }.toMap()

        val standings = teamRows.map { row ->
            val logoUrl = teamPages[row]?.logoUrl
            StandingEntity(
                category = category,
                standingsClass = row.standingsClass,
                type = StandingType.TEAM,
                entrantKey = "${category.name}-${row.standingsClass.name}-TEAM-${row.teamName}-${row.carNumber}",
                position = row.position,
                name = "#${row.carNumber}",
                team = row.teamName,
                points = row.points,
                photoUrl = logoUrl,
                updatedAtUtc = nowUtc
            )
        }

        val carDrivers = teamPages.values.filterNotNull().flatMap { it.drivers }

        return ImsaFetchResult(standings, carDrivers)
    }

    /** Resuelve el classId de cada shortcode ("GTP", "LMP2"...) contra la API pública de
     *  clases — mapa vacío (nunca excepción) si esa API fallara, para caer al fallback. */
    private fun resolveClassIds(year: Int): Map<String, String> = runCatching {
        val url = "https://dvw6yynr86g3k.cloudfront.net/galaxy/api/classes?series_id=$SERIES_ID&season_id=$year"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val json = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        val listType = Types.newParameterizedType(List::class.java, ImsaGalaxyClass::class.java)
        val adapter = StandingsMoshi.instance.adapter<List<ImsaGalaxyClass>>(listType)
        adapter.fromJson(json).orEmpty()
            .filter { it.shortcode != null }
            .associate { it.shortcode!!.uppercase() to it.id.toString() }
    }.getOrElse { emptyMap() }

    private fun fetchClassTeamRows(standingsClass: StandingsClass, classId: String, year: Int): List<TeamRow> {
        val body = FormBody.Builder()
            .add("action", "getImsaStandings")
            .add("standings", "team")
            .add("seriesId", SERIES_ID)
            .add("currentYear", year.toString())
            .add("classId", classId)
            .build()

        val request = Request.Builder()
            .url(AJAX_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.imsa.com/weathertech/standings/")
            .post(body)
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("imsa standings ($standingsClass): HTTP ${response.code}")
            response.body?.string() ?: error("imsa standings ($standingsClass): cuerpo vacío")
        }

        val doc = Jsoup.parse(html)
        // Solo la tabla "desktop" trae puntos (td.totalpoints) — la "mobile" repite las
        // mismas filas sin esa columna, así que acotar por esa celda evita procesar cada
        // coche dos veces.
        return doc.select("tr:has(td.totalpoints)").mapIndexedNotNull { index, row ->
            parseTeamRow(row, standingsClass, index + 1)
        }
    }

    /** "#04 Crowdstrike Racing by APR" -> número de coche + nombre de equipo. Algunas
     *  filas no traen el `<a>` de enlace (equipo sin ficha propia todavía) — se lee igual
     *  el texto de la celda, solo que esa fila se queda sin logo ni pilotos. */
    private fun parseTeamRow(row: Element, standingsClass: StandingsClass, position: Int): TeamRow? {
        val cell = row.selectFirst("td.team-col") ?: return null
        val text = cell.text().trim()
        val match = CAR_NUMBER_TEAM.find(text) ?: return null
        val carNumber = match.groupValues[1]
        val teamName = match.groupValues[2].trim()
        if (teamName.isBlank()) return null

        val points = row.selectFirst("td.totalpoints")?.text()?.trim()?.toDoubleOrNull() ?: 0.0
        val teamUrl = cell.selectFirst("a.team-name")?.absUrl("href")?.takeIf { it.isNotBlank() }

        return TeamRow(standingsClass, position, carNumber, teamName, points, teamUrl)
    }

    private fun fetchTeamPage(teamUrl: String, row: TeamRow, nowUtc: Long): TeamPage? = runCatching {
        // El Referer evita algún 403 puntual comprobado en pruebas manuales (parece
        // bastarle con que la petición diga venir de la propia imsa.com).
        val request = Request.Builder()
            .url(teamUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.imsa.com/weathertech/teams/")
            .build()
        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$teamUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$teamUrl: cuerpo vacío")
        }
        val doc = Jsoup.parse(html, teamUrl)

        val logoUrl = doc.select(".team-logos img").map { it.absUrl("src") }
            .firstOrNull {
                !it.contains("weathertech_championship", ignoreCase = true) &&
                    !it.contains("nologo", ignoreCase = true)
            }

        val drivers = doc.select("div.imsa-card_item_widget").mapNotNull { card ->
            val name = card.selectFirst("p.imsa-ciw-title")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val photoUrl = card.selectFirst("img.imsa-ciw-image")?.absUrl("data-src")?.takeIf { it.isNotBlank() }

            CarDriverEntity(
                category = StandingsCategory.IMSA,
                standingsClass = row.standingsClass,
                carNumber = row.carNumber,
                entryKey = normalize(name),
                name = name,
                photoUrl = photoUrl,
                updatedAtUtc = nowUtc
            )
        }

        TeamPage(logoUrl, drivers)
    }.getOrNull()

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class TeamRow(
        val standingsClass: StandingsClass,
        val position: Int,
        val carNumber: String,
        val teamName: String,
        val points: Double,
        val teamUrl: String?
    )

    private data class TeamPage(val logoUrl: String?, val drivers: List<CarDriverEntity>)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0"
        private const val AJAX_URL = "https://www.imsa.com/wp-admin/admin-ajax.php"
        /** WeatherTech SportsCar Championship — comprobado en galaxy/api/series (id 1;
         *  el 2 y el 3 son las otras dos series de IMSA, IMPC y VPRC). */
        private const val SERIES_ID = "1"
        /** No más de 8 páginas de equipo a la vez (de las ~45-50 en total). */
        private const val TEAM_PAGE_CONCURRENCY = 8

        private val CAR_NUMBER_TEAM = Regex("^#(\\d+)\\s+(.+)$")

        private val CLASS_SHORTCODES: Map<StandingsClass, String> = mapOf(
            StandingsClass.GTP to "GTP",
            StandingsClass.LMP2 to "LMP2",
            StandingsClass.GTD_PRO to "GTD PRO",
            StandingsClass.GTD to "GTD"
        )

        /** Ids comprobados a mano el 02/09/2026 para la temporada 2026 — solo se usan si
         *  la API de clases (ver resolveClassIds) no responde. */
        private val FALLBACK_CLASS_IDS: Map<StandingsClass, String> = mapOf(
            StandingsClass.GTP to "194",
            StandingsClass.LMP2 to "196",
            StandingsClass.GTD_PRO to "192",
            StandingsClass.GTD to "191"
        )
    }
}

data class ImsaFetchResult(
    val standings: List<StandingEntity>,
    val carDrivers: List<CarDriverEntity>
)

data class ImsaGalaxyClass(val id: Int, val shortcode: String?)
