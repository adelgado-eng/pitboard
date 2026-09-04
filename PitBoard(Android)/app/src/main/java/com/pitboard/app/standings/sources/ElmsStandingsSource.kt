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
 * ELMS es la única categoría que se trata "por clase": en vez de pilotos/equipos como el
 * resto, la página oficial (europeanlemansseries.com) publica una tabla de EQUIPOS por
 * cada una de las 4 clases que corren a la vez (LMP2, LMP2 Pro/Am, LMP3, LMGT3). Por
 * petición explícita, aquí solo se guardan filas de tipo TEAM (nunca pilotos).
 *
 * 30/08/2026: el número de coche es el identificador principal de cada fila (puede haber
 * más de un coche del mismo equipo en la misma clase) y el nombre de equipo aparece debajo,
 * con su logo si está en TEAM_LOGO_URLS — igual que piloto/equipo en el resto de categorías.
 *
 * 28/08/2026 — reescrito tras comprobar a mano (navegador real) que:
 * 1) El orden de las tablas en el HTML de la página NO coincide con el orden visual de sus
 *    pestañas (comprobado: título "LMP2", luego "LMP3", luego "LMP2 Pro/Am", luego "LMGT3",
 *    mientras que las pestañas visuales dicen LMP2 | LMP2 Pro/Am | LMP3 | LMGT3). El enfoque
 *    anterior — subir desde el título por sus ancestros y mirar hermanos — a veces terminaba
 *    en la tabla de OTRA clase por este desorden. Lo verificado como fiable: el título de
 *    cada clase ("<Clase> Teams Classification") y su tabla aparecen siempre en el MISMO
 *    orden relativo entre sí, así que ahora se emparejan por posición — el título de equipos
 *    Nº1 con la tabla de equipos Nº1, el Nº2 con la Nº2, etc. — en vez de por cercanía en el
 *    árbol del documento.
 * 2) La columna de puntos total ("Total points") va detrás de una columna "Race pts" que en
 *    realidad ocupa varias columnas de datos (puntos de cada carrera por separado) bajo un
 *    único encabezado — así que el índice del encabezado "Total points" NO corresponde a la
 *    misma posición en las celdas de datos de la fila. Antes esto hacía que se leyera el
 *    valor de una sola carrera en vez del total (por eso podían verse huecos como "65
 *    puntos" en un puesto y "4" en el siguiente). Comprobado con la tabla real: el total
 *    siempre es la ÚLTIMA celda de la fila, así que ahora se usa directamente esa por
 *    posición en vez de buscar la columna "Total points" por índice de encabezado.
 *
 * 30/08/2026 (4) — POR QUÉ SE VEÍA EL DORSAL COMO NOMBRE DE EQUIPO. Comprobado en el HTML
 * real de la página (navegador, no suposición): la celda del número de coche es un `<td>`
 * dentro del propio `<thead>`, no un `<th>`:
 *
 *     <thead><tr><th>Pos.</th><td>N°</td><th>Team</th> ... <th>Total points</th></tr></thead>
 *
 * Como la lectura de cabeceras solo miraba `thead th`, se saltaba esa celda: 9 cabeceras
 * frente a 10 celdas de datos, o sea todos los índices corridos en uno. Consecuencias en
 * cadena: no se encontraba la columna "N°" (así que no había número de coche, ni búsqueda
 * del equipo oficial, ni logo) y "Team" caía en el índice 1, que en las filas de datos es
 * el dorsal — de ahí que la app enseñara "#29" arriba y "#29" otra vez debajo. Ahora la
 * cabecera se lee con `th` Y `td`, y además hay una red de seguridad que localiza la
 * columna del dorsal por el contenido de las celdas si la cabecera fallara (ver
 * resolveCarIndex).
 *
 * HONESTO sobre los límites de esto: sigue siendo una heurística sin acceso permanente al
 * HTML real — si la web cambia de estructura de forma que el número de títulos de clase ya
 * no coincida con el número de tablas de equipo, se prefiere no devolver nada antes que
 * devolver clases mal etiquetadas (ver comprobación de tamaños en `fetch`).
 */
class ElmsStandingsSource : StandingsSource {

    override val category = StandingsCategory.ELMS

    private val pageUrl = "https://www.europeanlemansseries.com/en/page/classification-2"

    // A qué StandingsClass corresponde el texto de cada título "<Clase> Teams Classification".
    private val classMatchers: List<Pair<StandingsClass, (String) -> Boolean>> = listOf(
        StandingsClass.LMP2 to { t: String -> t.contains("LMP2") && !t.contains("PRO") },
        StandingsClass.LMP2_PRO_AM to { t: String -> t.contains("LMP2") && t.contains("PRO") },
        StandingsClass.LMP3 to { t: String -> t.contains("LMP3") },
        StandingsClass.LMGT3 to { t: String -> t.contains("LMGT3") || t.contains("GT3") }
    )

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

        // Títulos "<Clase> Teams Classification": se queda solo con los elementos "hoja" que
        // cumplen el criterio (ninguno de sus hijos directos lo cumple también) para no coger
        // por error un contenedor ancestro que envuelve varios títulos a la vez.
        val headings = doc.select("*")
            .filter { el ->
                val text = el.text().uppercase()
                text.contains("TEAM") && text.contains("CLASSIFICATION") && el.select("table").isEmpty()
            }
            .filter { el ->
                el.children().none { child ->
                    val childText = child.text().uppercase()
                    childText.contains("TEAM") && childText.contains("CLASSIFICATION")
                }
            }

        val teamTables = doc.select("table").filter { isTeamTable(it) }

        if (teamTables.isEmpty()) return emptyList()

        // Camino normal: un título de clase por tabla de equipos, emparejados por posición
        // (ver la explicación del 28/08/2026 arriba). Si esa correspondencia no se sostiene,
        // en vez de devolver nada se identifica cada tabla por los números de coche que
        // contiene, que es un dato mucho más estable que la maquetación (ver pairByCarNumbers).
        val paired: List<Pair<StandingsClass, Element>> =
            if (headings.isNotEmpty() && headings.size == teamTables.size) {
                headings.zip(teamTables).mapNotNull { (heading, table) ->
                    val headingText = heading.text().uppercase()
                    classMatchers.firstOrNull { (_, matches) -> matches(headingText) }
                        ?.first
                        ?.let { it to table }
                }
            } else {
                pairByCarNumbers(teamTables)
            }

        return paired.flatMap { (standingsClass, table) -> parseTeamTable(table, standingsClass, nowUtc) }
    }

    /**
     * Plan B para saber a qué clase pertenece cada tabla cuando los títulos no se pueden
     * emparejar (cambio de maquetación, un título de más o de menos): se mira qué números de
     * coche trae la tabla y se elige la clase de OFFICIAL_TEAM_BY_CAR que más comparte con
     * ella. Los dorsales de una clase no se repiten en otra, así que la coincidencia es
     * inequívoca en la práctica.
     *
     * Sigue prefiriendo no decir nada antes que etiquetar mal: una tabla se descarta si menos
     * de la mitad de sus coches están en la clase candidata, y cada clase se asigna una sola
     * vez.
     */
    private fun pairByCarNumbers(tables: List<Element>): List<Pair<StandingsClass, Element>> {
        val used = mutableSetOf<StandingsClass>()
        return tables.mapNotNull { table ->
            val numbers = carNumbersIn(table)
            if (numbers.isEmpty()) return@mapNotNull null

            val best = OFFICIAL_TEAM_BY_CAR
                .filterKeys { it !in used }
                .maxByOrNull { (_, cars) -> numbers.count { number -> cars.containsKey(number) } }
                ?: return@mapNotNull null

            val hits = numbers.count { number -> best.value.containsKey(number) }
            if (hits * 2 <= numbers.size) return@mapNotNull null

            used += best.key
            best.key to table
        }
    }

    /** Números de coche (sin "#") de una tabla, en el orden en que aparecen. */
    private fun carNumbersIn(table: Element): List<String> {
        val index = resolveCarIndex(table)
        if (index < 0) return emptyList()
        return dataRows(table).mapNotNull { row -> carNumberAt(row.select("td"), index) }
    }

    /** Filas de datos de una tabla — las de `<tbody>`, o todas menos la cabecera. */
    private fun dataRows(table: Element): List<Element> =
        table.select("tbody tr").ifEmpty { table.select("tr").drop(1) }

    /**
     * Cabecera de una tabla: TODAS las celdas de la fila de cabecera, tanto `<th>` como
     * `<td>` — la web mezcla las dos en la misma fila y leer solo los `<th>` descolocaba
     * todos los índices (ver la nota del 30/08/2026 (4) arriba).
     */
    private fun headerTexts(table: Element): List<String> {
        val headerRow = table.select("thead tr").firstOrNull()
            ?: table.select("tr").firstOrNull()
            ?: return emptyList()
        return headerRow.select("th, td").map { it.text().trim() }
    }

    /** Índice de la columna del número de coche en una cabecera ya leída (-1 si no está).
     *  Se compara sin acentos ni signos, así valen "N°", "Nº", "No.", "N" o "Num". */
    private fun carIndexOf(headers: List<String>): Int = headers.indexOfFirst { header ->
        val key = header.uppercase().replace(Regex("[^A-Z0-9]"), "")
        key == "N" || key == "NO" || key == "NUM" || key == "CAR" ||
            header.contains("Car", ignoreCase = true)
    }

    /**
     * Columna del dorsal, con red de seguridad: si la cabecera no la nombra (o no hay
     * cabecera legible), se busca en las propias filas la columna cuyas celdas tienen
     * forma de dorsal ("#29"). La almohadilla es obligatoria en esa búsqueda para no
     * confundirla con las columnas de puntos, que son números pelados.
     */
    private fun resolveCarIndex(table: Element): Int {
        val fromHeader = carIndexOf(headerTexts(table))
        if (fromHeader >= 0) return fromHeader

        val hits = mutableMapOf<Int, Int>()
        dataRows(table).take(5).forEach { row ->
            row.select("td").forEachIndexed { index, cell ->
                if (index > 0 && CAR_NUMBER_CELL.matches(cell.text().trim())) {
                    hits[index] = (hits[index] ?: 0) + 1
                }
            }
        }
        return hits.maxByOrNull { it.value }?.key ?: -1
    }

    /** Número de coche de una fila, sin el "#" y sin espacios — null si no se puede leer. */
    private fun carNumberAt(cells: Elements, carIndex: Int): String? =
        carIndex.takeIf { it >= 0 }
            ?.let { cells.getOrNull(it)?.text()?.trim() }
            ?.trimStart('#')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /** Distingue una tabla de EQUIPOS ("Pos. N° Team Race pts Total points") de una de
     *  PILOTOS ("Pos. N° Drivers Race pts Total points") por su propia cabecera. */
    private fun isTeamTable(table: Element): Boolean =
        headerTexts(table).any { it.equals("Team", ignoreCase = true) || it.contains("Team", ignoreCase = true) }

    private data class TeamRow(val name: String, val carNumber: String?, val points: Double, val carLabel: String)

    private fun parseTeamTable(table: Element, standingsClass: StandingsClass, nowUtc: Long): List<StandingEntity> {
        val headers = headerTexts(table)

        val carIndex = resolveCarIndex(table)
        // Si la cabecera no dijera dónde está "Team", se asume la celda justo detrás del
        // dorsal — que es como está montada la tabla: Pos. | N° | Team | ... | Total points.
        val teamIndex = headers.indexOfFirst { it.contains("Team", ignoreCase = true) }
            .let { if (it >= 0) it else if (carIndex >= 0) carIndex + 1 else 2 }

        val rows = dataRows(table)

        // La posición se renumera 1..N (por clase, ya que esta función procesa una tabla de
        // una única clase) según el orden en que ya vienen listadas las filas, en vez de
        // fiarse de la columna "Pos" del HTML — que puede traer huecos o números repetidos en
        // caso de empate — o de un índice que se salte números al descartar filas sin equipo.
        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapNotNull null

            // El "#" que a veces trae la celda se quita aquí (ver carNumberAt): el número
            // pelado es la clave de OFFICIAL_TEAM_BY_CAR y la etiqueta "#N" se vuelve a
            // construir más abajo, así que se ve igual venga como venga de la web.
            val carNumber = carNumberAt(cells, carIndex)

            // 30/08/2026 (3): el nombre de equipo que se enseña debajo del número de coche sale
            // de OFFICIAL_TEAM_BY_CAR — la lista oficial de la propia ELMS
            // (europeanlemansseries.com/en/teams), contrastada coche a coche con las cuatro
            // tablas "<Clase> Teams Classification" de la web oficial: 47 coches y 30 equipos,
            // con los mismos números y las mismas clases en ambas páginas. Se prefiere a lo
            // que traiga la celda "Team" por dos motivos: (1) la tabla lo escribe TODO EN
            // MAYÚSCULAS y aquí se ve con su grafía real ("IDEC Sport", no "IDEC SPORT"), y
            // (2) si esa celda llegara vacía o con otro formato, la fila ya no se quedaría sin
            // equipo. Un coche que no esté en la lista (inscripción nueva a mitad de
            // temporada) sigue usando lo que diga la web, que es la fuente en vivo.
            // Salvaguarda: si el índice de "Team" apuntara a una celda que en realidad es
            // un dorsal ("#29") o un número suelto, se descarta en vez de enseñarlo como
            // nombre de equipo — era exactamente el síntoma del fallo del 30/08/2026 (4).
            val scrapedTeam = cells.getOrNull(teamIndex)?.text()?.trim().orEmpty()
                .takeUnless { it.isBlank() || CAR_NUMBER_CELL.matches(it) || it.toDoubleOrNull() != null }
                .orEmpty()
            val teamText = carNumber?.let { OFFICIAL_TEAM_BY_CAR[standingsClass]?.get(it) } ?: scrapedTeam
            if (teamText.isBlank()) return@mapNotNull null

            // La columna "Total points" va detrás de "Race pts", que en realidad ocupa varias
            // celdas de datos (una por carrera) bajo un único encabezado — así que su índice de
            // encabezado no corresponde a la misma posición en las celdas reales de la fila.
            // Comprobado con la tabla real: el total es siempre la ÚLTIMA celda.
            val points = cells.lastOrNull()?.text()?.trim()?.toDoubleOrNull() ?: 0.0

            // El número de coche entra en la clave de la fila (ver entrantKey) para no perder
            // ninguno de los dos coches cuando un mismo equipo compite con varios en la misma
            // clase.
            val carLabel = carNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()

            TeamRow(teamText, carNumber, points, carLabel)
        }.mapIndexed { index, r ->
            StandingEntity(
                category = category,
                standingsClass = standingsClass,
                type = StandingType.TEAM,
                entrantKey = "${category.name}-${standingsClass.name}-TEAM-${r.name}-${r.carNumber}",
                position = index + 1,
                // 30/08/2026: se muestra el número de coche como identificador principal y el
                // equipo debajo (igual que piloto/equipo en el resto de categorías) — puede
                // haber más de un coche del mismo equipo en la misma clase (ej. IDEC Sport con
                // el #18 y el #28), así que el número es lo que distingue cada fila de un
                // vistazo. Si por lo que sea no se pudo leer el número de coche, se usa el
                // nombre de equipo como fallback para que el título nunca quede vacío.
                name = r.carLabel.ifBlank { r.name },
                team = r.name,
                points = r.points,
                photoUrl = TEAM_LOGO_URLS[normalize(r.name)],
                updatedAtUtc = nowUtc
            )
        }
    }

    // 30/08/2026: normaliza también las tildes a su letra base (ver el mismo fix en
    // OfficialRosterStandingsSource) — por consistencia con el resto de fuentes; la tabla de
    // clasificación de ELMS ya trae "LEMAN" sin tilde, pero por si acaso cambia.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        /** Una celda que es un dorsal y nada más: "#29", "# 7". La almohadilla es
         *  obligatoria — sin ella cualquier celda de puntos pasaría por número de coche. */
        private val CAR_NUMBER_CELL = Regex("^#\\s*\\d{1,3}$")

        /**
         * Equipo oficial de cada coche, por clase y número — la respuesta a "de quién es el
         * #29" sin depender de cómo venga escrita la celda "Team" de la tabla.
         *
         * 30/08/2026: copiado de la web oficial de ELMS (europeanlemansseries.com/en/teams,
         * que lista cada equipo con su clase y su numero de coche) y comprobado uno a uno
         * contra las cuatro tablas "<Clase> Teams Classification" de
         * europeanlemansseries.com/en/page/classification-2: coinciden los 47 coches
         * (11 LMP2, 12 LMP2 Pro/Am, 10 LMP3 y 14 LMGT3) y las 30 escuderías. Los nombres van
         * con su grafía real, no en mayúsculas como los imprime la tabla; todos ellos caen
         * en una clave de TEAM_LOGO_URLS al pasar por normalize(), así que cada fila
         * mantiene su logo.
         *
         * Si cambia la parrilla a mitad de temporada, esto se actualiza desde esa misma web;
         * mientras tanto un coche desconocido no rompe nada — se muestra con el nombre que
         * traiga la tabla (ver parseTeamTable).
         */
        private val OFFICIAL_TEAM_BY_CAR: Map<StandingsClass, Map<String, String>> = mapOf(
            StandingsClass.LMP2 to mapOf(
                "9" to "Proton Competition",
                "10" to "Vector Sport",
                "18" to "IDEC Sport",
                "22" to "United Autosports",
                "24" to "Nielsen Racing",
                "25" to "Algarve Pro Racing",
                "28" to "IDEC Sport",
                "29" to "Forestier Racing by Panis",
                "34" to "Inter Europol Competition",
                "37" to "CLX Motorsport",
                "43" to "Inter Europol Competition"
            ),
            StandingsClass.LMP2_PRO_AM to mapOf(
                "3" to "DKR Engineering",
                "7" to "Vector Sport",
                "14" to "TDS Racing",
                "19" to "Rossa Racing by Virage",
                "20" to "Algarve Pro Racing",
                "21" to "United Autosports",
                "27" to "Nielsen Racing",
                "30" to "Duqueine Team",
                "47" to "CLX Motorsport",
                "83" to "AF Corse",
                "88" to "Proton Competition",
                "99" to "AO by TF"
            ),
            StandingsClass.LMP3 to mapOf(
                "4" to "DKR Engineering",
                "5" to "Rinaldi Racing",
                "8" to "Team Virage",
                "11" to "Eurointernational",
                "13" to "Inter Europol Competition",
                "17" to "CLX Motorsport",
                "31" to "Racing Spirit of Leman",
                "35" to "Ultimate",
                "68" to "M Racing",
                "85" to "R-ace GP"
            ),
            StandingsClass.LMGT3 to mapOf(
                "23" to "United Autosports",
                "33" to "TF Sport",
                "50" to "Richard Mille AF Corse",
                "51" to "AF Corse",
                "54" to "High Class Racing",
                "55" to "Spirit of Race",
                "57" to "Kessel Racing",
                "59" to "Racing Spirit of Leman",
                "62" to "Team Qatar by Iron Lynx",
                "63" to "Iron Lynx",
                "74" to "Kessel Racing",
                "75" to "Proton Competition",
                "77" to "Proton Competition",
                "86" to "GR Racing"
            )
        )

        /** Logos de equipo por nombre normalizado — las claves son el nombre EXACTO tal como
         *  lo trae la columna "Team" de la tabla oficial de europeanlemansseries.com (ver
         *  parseTeamTable), comprobado a mano el 30/08/2026 contra las 4 clases (LMP2, LMP2
         *  Pro-Am, LMP3, LMGT3: 47 coches, 30 equipos distintos).
         *
         *  30/08/2026 (2): las 30 URLs vienen de la propia web oficial de ELMS
         *  (europeanlemansseries.com/en/teams, atributo "src" del div "brand-logo" de cada
         *  ficha de equipo) — cobertura completa de los 30 equipos, verificadas una a una
         *  (HTTP 200, imagen real). Sustituye al intento anterior con Wikipedia, que solo
         *  cubría 19 de los 30 (varios equipos franceses/europeos pequeños no tienen artículo
         *  ni logo en Wikipedia, pero sí en la propia web de la categoría).
         *
         *  30/08/2026 (5): revisadas otra vez una a una contra el servidor — las 30
         *  responden HTTP 200 con una imagen real. Hasta hoy no se veía ninguna, pero no
         *  era culpa de las URLs: el nombre de equipo llegaba aquí como "#29" por el fallo
         *  de cabeceras del punto (4), así que normalize() nunca acertaba una clave. Con
         *  ese fallo arreglado, los 47 coches caen en uno de estos 30 logos. */
        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "af corse" to "https://www.europeanlemansseries.com/uploads/af-corse-logo-rwf-2026-6a1050db75a59882854473.jpg",
            "algarve pro racing" to "https://www.europeanlemansseries.com/uploads/logo-apr-grey-4-69fb32bf5abca703789835.jpg",
            "ao by tf" to "https://www.europeanlemansseries.com/uploads/ao-69fb372fbce42693281829.png",
            "clx motorsport" to "https://www.europeanlemansseries.com/uploads/clx-02-fondblanc-600x-8-6a104d436f2c8016040958.png",
            "dkr engineering" to "https://www.europeanlemansseries.com/uploads/logo-dkr-6a104ddcf2be2522615970.jpg",
            "duqueine team" to "https://www.europeanlemansseries.com/uploads/duqueine-fond-fonce-rvb-principal-vert-1387d8-69fb32424287d736277658.png",
            "eurointernational" to "https://www.europeanlemansseries.com/uploads/eurointernational-69b4358ae3cbc269898583.png",
            "forestier racing by panis" to "https://www.europeanlemansseries.com/uploads/panis-racing-logo-6970a70365497655741936.png",
            "gr racing" to "https://www.europeanlemansseries.com/uploads/grracing-69b43709303d3982114829.png",
            "high class racing" to "https://www.europeanlemansseries.com/uploads/high-class-racing-team-full-logo-6a10511ebcaca030176156.png",
            "idec sport" to "https://www.europeanlemansseries.com/uploads/logo-idec-sport-signature-69fb35e5476e9923138721.png",
            "inter europol competition" to "https://www.europeanlemansseries.com/uploads/bild1-69fb36bcd4173387502825.png",
            "iron lynx" to "https://www.europeanlemansseries.com/uploads/ironlynx-69b4379bf12e4781333205.png",
            "kessel racing" to "https://www.europeanlemansseries.com/uploads/kessel-ra-69fa2133a8675957048954.jpg",
            "m racing" to "https://www.europeanlemansseries.com/uploads/capture-d-ecran-2019-02-11-a-18-14-19-6a104f0b2a460059499819.png",
            "nielsen racing" to "https://www.europeanlemansseries.com/uploads/nielsenracing-69b43001251fe977012084.png",
            "proton competition" to "https://www.europeanlemansseries.com/uploads/protoncompetition-left-69fb3661a27b9065802455.jpg",
            "race gp" to "https://www.europeanlemansseries.com/uploads/racegp-69b43d5a7bb12568008372.png",
            "racing spirit of leman" to "https://www.europeanlemansseries.com/uploads/racing-of-leman-fond-transp-03-69fb30a68842b083442787.png",
            "richard mille af corse" to "https://www.europeanlemansseries.com/uploads/richard-mille-af-corse-logo-69faf0be76a4e012429055.jpg",
            "rinaldi racing" to "https://www.europeanlemansseries.com/uploads/logo-rinaldi-racing-6a104da903a40406466216.png",
            "rossa racing by virage" to "https://www.europeanlemansseries.com/uploads/rossaracingvirage-69b439ef72f3f176279175.png",
            "spirit of race" to "https://www.europeanlemansseries.com/uploads/spiritofrace-69b43a472fd20008829558.png",
            "tds racing" to "https://www.europeanlemansseries.com/uploads/logo-tds-69fb334d3e327775241613.jpg",
            "team qatar by iron lynx" to "https://www.europeanlemansseries.com/uploads/qmmfiron-69b43b3c8e00b215906208.png",
            "team virage" to "https://www.europeanlemansseries.com/uploads/escudo-virage-2020-6a104e3c9fe59272031071.png",
            "tf sport" to "https://www.europeanlemansseries.com/uploads/logo-tf-sport-full-black-69fae8addc867999538358.png",
            "ultimate" to "https://www.europeanlemansseries.com/uploads/ultimate-logo-02-6a104df5da4fd412204634.png",
            "united autosports" to "https://www.europeanlemansseries.com/uploads/united-autosports-black-6a10503c2b10b938159166.png",
            "vector sport" to "https://www.europeanlemansseries.com/uploads/vector-sport-logo-69fb316db07cd851127223.jpg"
        )
    }
}
