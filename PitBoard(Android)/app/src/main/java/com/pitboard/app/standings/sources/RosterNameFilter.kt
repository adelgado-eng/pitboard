package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Utilidad compartida para excluir pilotos reserva/wildcard/test que no están en la
 * parrilla oficial de esta temporada, pero que sí aparecen en la tabla de puntos con 0 (o
 * pocos) puntos — algo que pasaba en MotoGP (29 filas en vez de 22) y F1 Academy (22 en vez
 * de 17), ver conversación del 28/08/2026.
 *
 * HONESTO: `fetchKnownNames` es una heurística — busca texto que "parece un nombre propio"
 * (dos o más palabras que empiezan en mayúscula) dentro de títulos, enlaces, listas y
 * negritas de la página de referencia (un directorio de pilotos o un artículo de la
 * alineación confirmada, no siempre una fuente 100% oficial — ver comentario en cada
 * fuente que la usa). Si no encuentra nada reconocible, `fetchKnownNames` devuelve un
 * conjunto vacío y quien la llama debe usarlo como "no filtrar nada" en vez de vaciar la
 * categoría entera — mejor un piloto reserva de más que perder la clasificación completa.
 */
object RosterNameFilter {

    private val NAME_REGEX = Regex("^\\p{Lu}[\\p{L}'-]+(\\s+\\p{Lu}[\\p{L}'-]+){1,3}$")

    fun fetchKnownNames(url: String): Set<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }

        val doc = Jsoup.parse(html, url)
        return doc.select("h1, h2, h3, h4, li, a, strong, b, td")
            .map { it.ownText().trim() }
            .filter { it.length in 4..40 && NAME_REGEX.matches(it) }
            .toSet()
    }

    /** true si `name` coincide (mejor esfuerzo, por subcadena normalizada) con algún nombre
     *  de `knownNames`, o si `knownNames` está vacío (no se pudo obtener la parrilla de
     *  referencia — en ese caso no se filtra nada). */
    fun isInRoster(name: String, knownNames: Set<String>): Boolean {
        if (knownNames.isEmpty()) return true
        val normalizedName = normalize(name)
        if (normalizedName.isBlank()) return false
        return knownNames.any { known ->
            val normalizedKnown = normalize(known)
            if (normalizedKnown.isBlank()) return@any false
            if (normalizedName == normalizedKnown || normalizedName.contains(normalizedKnown) || normalizedKnown.contains(normalizedName)) {
                return@any true
            }
            // Mismo caso que namesMatch en OfficialRosterStandingsSource: iniciales con
            // puntuación distinta entre las dos páginas ("AJ" vs "A. J.") dejan un espacio
            // en un sitio distinto tras quitar los signos — se compara también sin espacios.
            val ca = normalizedName.replace(" ", "")
            val cb = normalizedKnown.replace(" ", "")
            if (ca == cb || ca.contains(cb) || cb.contains(ca)) return@any true
            // 30/08/2026: autosport.com abrevia el nombre de pila a una inicial en su tabla
            // de puntos de MotoGP ("J. Martin" en vez de "Jorge Martin") mientras que la
            // página de referencia trae el nombre completo — ninguna subcadena contiene a la
            // otra aunque sea la misma persona, así que ANTES esto vaciaba el filtro entero
            // (ver filterKeepingReal) y MotoGP nunca llegaba a excluir a ningún piloto
            // reserva. Como último recurso se comparan solo los apellidos (última palabra de
            // cada nombre) — y solo si tienen 3 letras o más, para no dar positivos por
            // casualidad con apellidos cortos que coincidan por azar.
            val lastA = normalizedName.substringAfterLast(' ')
            val lastB = normalizedKnown.substringAfterLast(' ')
            lastA.length >= 3 && lastA == lastB
        }
    }

    // 30/08/2026: antes esta función solo borraba cualquier carácter que no fuera a-z0-9 —
    // eso incluía las tildes, así que las quitaba en vez de convertir la letra a su base (ej.
    // "Jorge Martín" -> "jorge martn" en vez de "jorge martin"). Si la tabla de puntos escribe
    // el nombre con tilde y la página de referencia (o al revés) no, las dos cadenas
    // normalizadas dejaban de coincidir y ese piloto real podía acabar excluido por error como
    // si fuera reserva — justo lo que esta utilidad existe para evitar. Normalizer.Form.NFD
    // separa cada letra con tilde en la letra base + su marca diacrítica (categoría Unicode
    // Mn) como carácter aparte, así que se puede quitar solo la marca y quedarse con la base.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Aplica [isInRoster] a toda la lista, pero si el resultado se queda vacío mientras la
     *  lista original no lo estaba, no se fía del filtro y devuelve la lista sin filtrar.
     *  28/08/2026: esto pasaba en MotoGP (la fuente de referencia usa nombres completos y la
     *  tabla de puntos los trae abreviados, ej. "J. Martin" vs "Jorge Martín" — antes nunca
     *  coincidían por subcadena; 30/08/2026: [isInRoster] ya compara también por apellido
     *  como último recurso, así que este caso concreto queda resuelto) y en F1 Academy (la
     *  página de referencia resultó ser JavaScript puro, así que lo que se descarga no trae
     *  ningún nombre real, solo texto de menú que por casualidad "parece" un nombre) — este
     *  resguardo se queda para cualquier otro caso en que el filtro falle por completo, para
     *  dejar pasar, como mucho, algún piloto reserva de más antes que perder la clasificación
     *  entera. */
    fun <T> filterKeepingReal(rows: List<T>, knownNames: Set<String>, nameOf: (T) -> String): List<T> {
        if (rows.isEmpty()) return rows
        val filtered = rows.filter { isInRoster(nameOf(it), knownNames) }
        return if (filtered.isEmpty()) rows else filtered
    }
}
