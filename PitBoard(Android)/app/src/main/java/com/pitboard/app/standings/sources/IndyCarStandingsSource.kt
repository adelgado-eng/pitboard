package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsSource
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * indycar.com/Standings: la web OFICIAL de IndyCar, con tabla real en el HTML — posición ya
 * desempatada correctamente, foto real del piloto y equipo en la misma fila. A diferencia de
 * F1/NASCAR, aquí no hace falta cruzar con ninguna otra fuente (28/08/2026, sustituye a
 * driverdb.com).
 */
class IndyCarStandingsSource : StandingsSource {

    override val category = StandingsCategory.INDYCAR

    private val standingsUrl = "https://www.indycar.com/Standings"

    private data class DriverParse(val name: String, val team: String, val points: Double, val photoUrl: String?)

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val request = Request.Builder()
            .url(standingsUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$standingsUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$standingsUrl: cuerpo vacío")
        }

        return parseHtml(html, nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // parsing (incluido el recorte del "?w=80" y el "Logo" pegado al alt del escudo) contra
    // un fixture HTML sin red — ver IndyCarStandingsSourceTest.
    internal fun parseHtml(html: String, nowUtc: Long): List<StandingEntity> {
        val doc = Jsoup.parse(html, standingsUrl)
        val table = doc.select("table").firstOrNull { t ->
            t.select("th").any { it.text().contains("Driver", ignoreCase = true) }
        } ?: return emptyList()

        val headers = table.select("th").map { it.text().trim() }
        val driverIndex = headers.indexOfFirst { it.contains("Driver", ignoreCase = true) }
            .let { if (it >= 0) it else 1 }
        val teamIndex = headers.indexOfFirst { it.contains("Team", ignoreCase = true) }
        val pointsIndex = headers.indexOfFirst { it.contains("Points", ignoreCase = true) || it.contains("Pts", ignoreCase = true) }
        if (pointsIndex < 0) return emptyList()

        // La posición se renumera 1..N según el orden en que ya vienen listadas las filas
        // (el orden real de clasificación de la tabla oficial) en vez de fiarse de la columna
        // "Rank"/"Pos" del HTML, o de un índice que se salte números al descartar filas sin
        // nombre — cualquiera de las dos formas anteriores podía dejar huecos.
        val driverRows = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null

            val driverCell = cells.getOrNull(driverIndex) ?: return@mapNotNull null
            val rawName = driverCell.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: driverCell.text().trim()
            val name = rawName.replace(Regex("^#?\\d+\\s+"), "").trim()
            if (name.isBlank()) return@mapNotNull null

            // La celda de piloto trae varias imágenes (endplate del coche + foto de cara) —
            // se busca la que apunte a la carpeta "Headshot" para no coger otra imagen.
            //
            // 04/09/2026: el HTML de la tabla trae siempre "?w=80" (miniatura de 80x64 —
            // se ve borrosa en el avatar grande de CategoryStandingsScreen y en la vista
            // previa a pantalla completa). Comprobado a mano que el mismo CMS de
            // indycar.com (Sitecore) sirve la foto real a resolución nativa (585x470) si
            // se quita el parámetro de ancho — mismo dominio y patrón que ya usan los
            // logos de equipo de más abajo (TEAM_LOGO_URLS, con ?w=400).
            val photoUrl = driverCell.select("img")
                .map { it.absUrl("src") }
                .firstOrNull { it.contains("Headshot", ignoreCase = true) }
                ?.takeIf { it.isNotBlank() }
                ?.replace(Regex("[?&]w=\\d+"), "")

            val teamCell = teamIndex.takeIf { it >= 0 }?.let { cells.getOrNull(it) }
            // El equipo a veces viene como logo (sin texto visible) en vez de texto — se cae
            // al atributo alt de esa imagen si la celda no tiene texto. Ese alt trae siempre
            // la palabra "Logo" al final (ej. "Andretti Global Logo ") — se quita para no
            // guardar/mostrar el nombre de equipo con "Logo" pegado (28/08/2026).
            val team = teamCell?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: teamCell?.selectFirst("img")?.attr("alt")?.trim()
                    ?.replace(Regex("\\s*Logo\\s*$", RegexOption.IGNORE_CASE), "")
                    ?.trim().orEmpty()

            val points = cells.getOrNull(pointsIndex)?.text()?.trim()?.toDoubleOrNull() ?: 0.0

            DriverParse(name, team, points, photoUrl)
        }.mapIndexed { index, r ->
            StandingEntity(
                category = category,
                standingsClass = StandingsClass.OVERALL,
                type = StandingType.DRIVER,
                entrantKey = "${category.name}-DRIVER-${r.name}",
                position = index + 1,
                name = r.name,
                team = r.team,
                points = r.points,
                photoUrl = r.photoUrl,
                updatedAtUtc = nowUtc
            )
        }

        // Equipos: se agrupan los pilotos por equipo y se suman sus puntos — esta tabla
        // oficial no trae una clasificación de equipos aparte en la misma página.
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

        return driverRows + teamRows
    }

    /** Normaliza un nombre de equipo para usarlo como clave del mapa de logos: minusculas,
     *  sin signos de puntuacion (quita puntos, "&", "/") y con los espacios colapsados — asi
     *  "A.J. Foyt Enterprises" y "Dreyer & Reinbold Racing" coinciden con sus claves en
     *  TEAM_LOGO_URLS sin tener que escribir el nombre exacto caracter a caracter. */
    // 30/08/2026: normaliza también las tildes a su letra base (ver el mismo fix en
    // OfficialRosterStandingsSource) — aquí solo se usa para el nombre de equipo, pero se
    // deja consistente con el resto de fuentes por si algún equipo trae acento (ej. "Chevrolet").
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        /** Logos oficiales de los 13 equipos de la IndyCar 2026, por nombre normalizado —
         *  extraidos del propio indycar.com/Standings (atributo alt de la imagen de equipo,
         *  ya sin el sufijo "Logo"), mismo dominio y patron de URL que ya usa el logo de la
         *  categoria (indycar.com/-/media/IndyCar/...) — verificados uno a uno, todos
         *  HTTP 200 image/png (28/08/2026). */
        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "chip ganassi racing" to "https://www.indycar.com/-/media/IndyCar/Team/ChipGanassiRacing.png?w=400",
            "andretti global" to "https://www.indycar.com/-/media/IndyCar/Team/AndrettiGlobal.png?w=400",
            "arrow mclaren" to "https://www.indycar.com/-/media/IndyCar/Team/ArrowMcLaren.png?w=400",
            "team penske" to "https://www.indycar.com/-/media/IndyCar/Team/TeamPenske.png?w=400",
            "meyer shank racing" to "https://www.indycar.com/-/media/IndyCar/Team/MeyerShankRacing.png?w=400",
            "juncos hollinger racing" to "https://www.indycar.com/-/media/IndyCar/Team/JuncosHollinger.png?w=400",
            "rahal letterman lanigan racing" to "https://www.indycar.com/-/media/IndyCar/Team/RahalLettermanLanigan.png?w=400",
            "ecr" to "https://www.indycar.com/-/media/IndyCar/Team/EdCarpenterRacing.png?w=400",
            "aj foyt enterprises" to "https://www.indycar.com/-/media/IndyCar/Team/AJFoytRacing.png?w=400",
            "dale coyne racing" to "https://www.indycar.com/-/media/IndyCar/Team/DaleCoyneRacing.png?w=400",
            "dreyer reinbold racing" to "https://www.indycar.com/-/media/IndyCar/Team/DreyerReinboldRacing.png?w=400",
            "abel motorsports" to "https://www.indycar.com/-/media/IndyCar/Team/AbelMotorsports.png?w=400",
            "hmd motorsports w aj foyt racing" to "https://www.indycar.com/-/media/IndyCar/Team/HMD-Foyt.png?w=400"
        )
    }
}
