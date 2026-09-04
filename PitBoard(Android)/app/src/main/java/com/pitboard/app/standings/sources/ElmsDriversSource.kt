package com.pitboard.app.standings.sources

import com.pitboard.app.standings.CarDriverEntity
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Los 3 pilotos de cada coche de ELMS (a veces 2 o 4, si hay un cambio a mitad de
 * temporada), para el desplegable que sale al tocar un coche en Clasificaciones — dato
 * que ElmsStandingsSource.kt NUNCA trae, porque su tabla de origen es de EQUIPOS, no de
 * pilotos.
 *
 * Fuente: europeanlemansseries.com/en/page/drivers — página propia de la web oficial,
 * comprobada a mano el 02/09/2026 (HTML real, no suposición): una única página con las 4
 * clases (LMP2, LMP2 PRO/AM, LMP3, LMGT3), cada una con un `<h2 class="h3 text-center">`
 * seguido de sus tarjetas `<div class="card-driver">`. Cada tarjeta trae:
 *   - `div.driver-name`: nombre completo del piloto.
 *   - `img` dentro de `div.driver-thumb`: su foto oficial (siempre presente en las 140
 *     tarjetas comprobadas).
 *   - `div.driver-team`: equipo y número de coche juntos, ej. "PROTON COMPETITION #9" —
 *     el número de coche es lo único que hace falta de aquí (el nombre de equipo ya sale,
 *     con su grafía real, de ElmsStandingsSource).
 *
 * Como la tarjeta siempre aparece DESPUÉS de su `<h2>` de clase en el árbol del documento
 * (a diferencia de las tablas de clasificación, que sí venían desordenadas — ver el aviso
 * del 28/08/2026 en ElmsStandingsSource), aquí basta con recorrer el documento en orden y
 * recordar la última clase vista.
 *
 * HONESTO: si la web cambiara de estructura y no se encontrara ninguna tarjeta, se
 * devuelve lista vacía en vez de arriesgarse a asociar mal pilotos a coches — mismo
 * criterio que el resto de fuentes ELMS.
 */
class ElmsDriversSource {

    private val pageUrl = "https://www.europeanlemansseries.com/en/page/drivers"

    private val classMatchers: List<Pair<StandingsClass, (String) -> Boolean>> = listOf(
        StandingsClass.LMP2 to { t: String -> t.contains("LMP2") && !t.contains("PRO") },
        StandingsClass.LMP2_PRO_AM to { t: String -> t.contains("LMP2") && t.contains("PRO") },
        StandingsClass.LMP3 to { t: String -> t.contains("LMP3") },
        StandingsClass.LMGT3 to { t: String -> t.contains("LMGT3") || t.contains("GT3") }
    )

    suspend fun fetch(nowUtc: Long): List<CarDriverEntity> {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()

        val html = StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$pageUrl: HTTP ${response.code}")
            response.body?.string() ?: error("$pageUrl: cuerpo vacío")
        }

        return parseHtml(html, nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // agrupado por clase (recordando el último <h2> visto) contra un fixture HTML sin red —
    // ver ElmsDriversSourceTest.
    internal fun parseHtml(html: String, nowUtc: Long): List<CarDriverEntity> {
        val doc = Jsoup.parse(html, pageUrl)

        var currentClass: StandingsClass? = null
        val rows = mutableListOf<CarDriverEntity>()

        doc.select("h2.h3.text-center, div.card-driver").forEach { el ->
            if (el.tagName() == "h2") {
                val text = el.text().uppercase()
                currentClass = classMatchers.firstOrNull { (_, matches) -> matches(text) }?.first
                return@forEach
            }

            val standingsClass = currentClass ?: return@forEach
            parseDriverCard(el, standingsClass, nowUtc)?.let { rows += it }
        }

        return rows
    }

    private fun parseDriverCard(card: Element, standingsClass: StandingsClass, nowUtc: Long): CarDriverEntity? {
        val name = card.selectFirst("div.driver-name")?.text()?.trim().orEmpty()
        if (name.isBlank()) return null

        // "PROTON COMPETITION #9" -> el número de coche es lo único que se necesita de
        // aquí (ver KDoc de la clase); sin él no hay forma de asociar el piloto a un
        // coche, así que la tarjeta se descarta.
        val teamText = card.selectFirst("div.driver-team")?.text()?.trim().orEmpty()
        val carNumber = CAR_NUMBER_SUFFIX.find(teamText)?.groupValues?.get(1) ?: return null

        val photoUrl = card.selectFirst("div.driver-thumb img")?.absUrl("src")?.takeIf { it.isNotBlank() }

        return CarDriverEntity(
            category = StandingsCategory.ELMS,
            standingsClass = standingsClass,
            carNumber = carNumber,
            entryKey = normalize(name),
            name = name,
            photoUrl = photoUrl,
            updatedAtUtc = nowUtc
        )
    }

    // 02/09/2026: mismo normalize() que ElmsStandingsSource/DriverDbStandingsSource, por
    // consistencia — aquí solo se usa como parte de la clave primaria de la fila.
    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private val CAR_NUMBER_SUFFIX = Regex("#\\s*(\\d+)\\s*$")
    }
}
