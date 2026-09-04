package com.pitboard.app.standings.sources

import com.pitboard.app.standings.CarDriverEntity
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Pilotos por coche para WEC y Le Mans Cup — fuente COMPARTIDA porque ambas webs
 * (fiawec.com y lemanscup.com) usan exactamente la misma plantilla (mismo organizador,
 * ACO): comprobado a mano el 03/09/2026, byte a byte iguales en la parte que interesa
 * aquí — tarjetas `div.card-team` con la clase del coche (`span.fs-11`), su número (del
 * propio enlace `/en/car/{año}/{N}`, no hace falta leerlo de ningún texto) y el enlace a
 * su ficha; y en la ficha de cada coche, tarjetas `a.card-driver` con foto real (`img`
 * con `src` normal, sin el truco de carga perezosa que sí tiene imsa.com) y nombre
 * (`div.py-4`).
 *
 * HONESTO sobre el coste: como con ImsaStandingsSource, hace falta una petición por
 * coche además de la página de listado — WEC tiene ~35 coches, Le Mans Cup ~45. Se
 * visitan con concurrencia acotada (8 a la vez) para no golpear el servidor de golpe.
 * Un fallo al visitar la ficha de un coche concreto solo deja a ESE coche sin pilotos,
 * nunca tumba la sincronización completa.
 */
class AcoCarDriversSource(
    private val category: StandingsCategory,
    /** Página con todos los coches de la temporada — fiawec.com/en/page/grid o
     *  lemanscup.com/en/car/{año} (con el año ya sustituido). */
    private val listingUrl: String,
    /** Texto del badge de clase (`span.fs-11`, ej. "Hypercar", "LMP3 Pro/Am") a
     *  StandingsClass — se prueban en orden, así que las coincidencias más específicas
     *  ("LMP3 Pro/Am") deben ir antes que las genéricas ("LMP3"). */
    private val classMatchers: List<Pair<StandingsClass, (String) -> Boolean>>
) {

    suspend fun fetch(nowUtc: Long): List<CarDriverEntity> {
        val listingHtml = fetchHtml(listingUrl)
        val listingDoc = Jsoup.parse(listingHtml, listingUrl)

        data class CarRef(val standingsClass: StandingsClass, val carUrl: String)

        val carRefs = listingDoc.select("div.card-team").mapNotNull { card ->
            val classText = card.selectFirst("span.fs-11")?.text()?.trim().orEmpty()
            val standingsClass = classMatchers.firstOrNull { (_, matches) -> matches(classText) }?.first
                ?: return@mapNotNull null
            val carUrl = card.selectFirst("a.stretched-link")?.absUrl("href")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            CarRef(standingsClass, carUrl)
        }

        return coroutineScope {
            carRefs.chunked(CONCURRENCY).flatMap { chunk ->
                chunk.map { ref -> async(Dispatchers.IO) { fetchCarDrivers(ref.carUrl, ref.standingsClass, nowUtc) } }
                    .awaitAll()
            }
        }.flatten()
    }

    private fun fetchCarDrivers(carUrl: String, standingsClass: StandingsClass, nowUtc: Long): List<CarDriverEntity> =
        runCatching {
            // El número de coche sale del propio tramo final de la URL de la ficha
            // (".../car/2026/35") — más fiable que leerlo de un texto suelto, que en
            // alguna de las dos webs va en un span aparte y en la otra pegado al
            // nombre del equipo.
            val carNumber = carUrl.trimEnd('/').substringAfterLast('/')
            if (carNumber.isBlank() || carNumber.toIntOrNull() == null) return@runCatching emptyList()

            val html = fetchHtml(carUrl)
            val doc = Jsoup.parse(html, carUrl)

            doc.select("a.card-driver").mapNotNull { card ->
                val name = card.selectFirst("div.py-4")?.text()?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val photoUrl = card.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }

                CarDriverEntity(
                    category = category,
                    standingsClass = standingsClass,
                    carNumber = carNumber,
                    entryKey = normalize(name),
                    name = name,
                    photoUrl = photoUrl,
                    updatedAtUtc = nowUtc
                )
            }
        }.getOrElse { emptyList() }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
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

    companion object {
        private const val CONCURRENCY = 8
    }
}
