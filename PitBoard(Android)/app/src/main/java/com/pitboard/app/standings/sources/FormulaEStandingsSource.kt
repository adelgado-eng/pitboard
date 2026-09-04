package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsHttpClient
import com.pitboard.app.standings.StandingsMoshi
import com.pitboard.app.standings.StandingsSource
import com.squareup.moshi.Types
import okhttp3.Request

/**
 * Fórmula E: se corrige el 03/09/2026 la conclusión anterior de "sin fuente" — la temporada
 * 2025-26 (Season 12) ya está en marcha desde diciembre de 2025 (el comentario viejo de
 * StandingsCategory.FORMULA_E decía "no empieza hasta diciembre de 2026", una equivocación:
 * esa es la temporada SIGUIENTE, la 2026-27). fiaformulae.com/en/standings es una SPA sin
 * datos en el HTML servido (la sección de la tabla es un `<section data-widget=
 * "standings/standings">` vacío que se rellena por JS), pero el widget carga sus datos desde
 * una API JSON pública del mismo proveedor que usa MotoGP (Pulselive) — comprobado a mano con
 * curl el 03/09/2026 contra el host real `api.formula-e.pulselive.com`:
 *
 * 1. GET `/formula-e/v1/championships` — lista de todas las temporadas, cada una con
 *    `status` ("Present" para la actual, "Past" para las anteriores). Se busca la de
 *    `status == "Present"`.
 * 2. GET `/formula-e/v1/standings/drivers?championshipId={id}` y
 *    `/formula-e/v1/standings/teams?championshipId={id}` — JSON plano ya con posición,
 *    nombre y puntos, sin necesidad de tocar HTML.
 *
 * Fotos de piloto y logos de equipo (añadido después de comprobar que la API de arriba no
 * trae ningún campo de imagen): ninguna de las tres respuestas anteriores tiene foto/logo,
 * y fiaformulae.com/en/standings tampoco los sirve en el HTML — pero las páginas de perfil
 * SÍ (`/en/drivers/{id}/{slug}`, `/en/teams/{id}/{slug}`), a través de un componente propio
 * ("staticfile-image") que NO pone la URL completa en el HTML, solo una ruta relativa
 * (`data-image-path`) que su JS resuelve contra un host fijo. Se sacó ese host leyendo el
 * bundle de la propia web (`/resources/v4.38.0/scripts/bundle-es.min.js`, config
 * `staticFiles:"//static-files.formula-e.pulselive.com"`) y comprobado con curl (HTTP 200,
 * imagen real) el 03/09/2026:
 * - Piloto: `https://static-files.formula-e.pulselive.com/drivers/{championshipId}/right/large/{driverId}.png`
 *   (mismo `driverId` que ya trae `/standings/drivers` — no hace falta ninguna petición más).
 * - Equipo: `https://static-files.formula-e.pulselive.com/badges/{teamId}.svg` (mismo
 *   `teamId` que ya trae `/standings/teams`, y sin depender de la temporada). Es SVG, no
 *   PNG/JPG como el resto de la app — hace falta el decoder de Coil (ver
 *   PitBoardApplication.newImageLoader() y la dependencia coil-svg en build.gradle.kts),
 *   si no la imagen no se pintaría pese a la URL ser correcta.
 *
 * No se comprobó CADA piloto/equipo uno a uno (sería 30+ peticiones de más) — se confirmó el
 * patrón contra un piloto y un equipo reales y se aplica igual al resto, mismo criterio que
 * ya se usa con la API de motogp.com en MotorsportStandingsHtmlSource.
 *
 * La posición de cada fila NO se copia del campo `driverPosition`/`teamPosition` de la API
 * tal cual (mismo criterio que WecStandingsSource tras el bug de las posiciones de WEC): se
 * reordena por puntos descendentes y se renumera con `mapIndexed`, así una fila con posición
 * nula o duplicada en el JSON de origen no puede colar un hueco o un choque de posiciones.
 */
class FormulaEStandingsSource : StandingsSource {
    override val category: StandingsCategory = StandingsCategory.FORMULA_E

    override suspend fun fetch(nowUtc: Long): List<StandingEntity> {
        val championshipId = parseChampionshipsJson(fetchJson("$API_BASE/championships")) ?: return emptyList()

        val driverRows = runCatching {
            parseDriverRowsJson(fetchJson("$API_BASE/standings/drivers?championshipId=$championshipId"))
        }.getOrElse { emptyList() }
        val teamRows = runCatching {
            parseTeamRowsJson(fetchJson("$API_BASE/standings/teams?championshipId=$championshipId"))
        }.getOrElse { emptyList() }

        return buildDriverEntities(driverRows, championshipId, nowUtc) + buildTeamEntities(teamRows, championshipId, nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): fetchCurrentChampionshipId/fetchDriverRows/
    // fetchTeamRows separadas en "red" (fetchJson, sin cambios) + "parsing puro" (las
    // funciones de abajo) para poder testear contra fixtures JSON reales sin red — ver
    // FormulaEStandingsSourceTest.
    internal fun parseChampionshipsJson(json: String): String? {
        val adapter = StandingsMoshi.instance.adapter(FormulaEChampionshipsResponse::class.java)
        val championships = adapter.fromJson(json)?.championships.orEmpty()
        return championships.firstOrNull { it.status.equals("Present", ignoreCase = true) }?.id
            ?: championships.lastOrNull()?.id
    }

    internal fun parseDriverRowsJson(json: String): List<Row> {
        val listType = Types.newParameterizedType(List::class.java, FormulaEDriverStanding::class.java)
        val adapter = StandingsMoshi.instance.adapter<List<FormulaEDriverStanding>>(listType)
        val rows = adapter.fromJson(json).orEmpty()
        return rows.mapNotNull { row ->
            val name = listOfNotNull(row.driverFirstName?.trim(), row.driverLastName?.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (name.isBlank()) return@mapNotNull null
            val driverId = row.driverId?.trim()?.takeIf { it.isNotBlank() }
            Row(
                key = driverId ?: "${category.name}-DRIVER-$name",
                name = name,
                team = row.driverTeamName?.trim().orEmpty(),
                points = row.driverPoints ?: 0.0,
                imageId = driverId
            )
        }
    }

    internal fun parseTeamRowsJson(json: String): List<Row> {
        val listType = Types.newParameterizedType(List::class.java, FormulaETeamStanding::class.java)
        val adapter = StandingsMoshi.instance.adapter<List<FormulaETeamStanding>>(listType)
        val rows = adapter.fromJson(json).orEmpty()
        return rows.mapNotNull { row ->
            val name = row.teamName?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val teamId = row.teamId?.trim()?.takeIf { it.isNotBlank() }
            Row(
                key = teamId ?: "${category.name}-TEAM-$name",
                name = name,
                team = "",
                points = row.teamPoints ?: 0.0,
                imageId = teamId
            )
        }
    }

    internal fun buildDriverEntities(rows: List<Row>, championshipId: String, nowUtc: Long): List<StandingEntity> =
        rows.sortedByDescending { it.points }
            .mapIndexed { index, row ->
                StandingEntity(
                    category = category,
                    standingsClass = StandingsClass.OVERALL,
                    type = StandingType.DRIVER,
                    entrantKey = row.key,
                    position = index + 1,
                    name = row.name,
                    team = row.team,
                    points = row.points,
                    photoUrl = row.imageId?.let { "$STATIC_FILES_BASE/drivers/$championshipId/right/large/$it.png" },
                    updatedAtUtc = nowUtc
                )
            }

    internal fun buildTeamEntities(rows: List<Row>, championshipId: String, nowUtc: Long): List<StandingEntity> =
        rows.sortedByDescending { it.points }
            .mapIndexed { index, row ->
                StandingEntity(
                    category = category,
                    standingsClass = StandingsClass.OVERALL,
                    type = StandingType.TEAM,
                    entrantKey = row.key,
                    position = index + 1,
                    name = row.name,
                    team = "",
                    points = row.points,
                    photoUrl = row.imageId?.let { "$STATIC_FILES_BASE/badges/$it.svg" },
                    updatedAtUtc = nowUtc
                )
            }

    private fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
            .build()
        return StandingsHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$url: HTTP ${response.code}")
            response.body?.string() ?: error("$url: cuerpo vacío")
        }
    }

    // internal (no private): expuesta a test — ver parseDriverRowsJson/parseTeamRowsJson.
    internal data class Row(
        val key: String,
        val name: String,
        val team: String,
        val points: Double,
        /** driverId (piloto) o teamId (equipo) — null solo si la API no lo trajo, y en ese
         *  caso no se puede construir la URL de foto/logo (ver STATIC_FILES_BASE). */
        val imageId: String? = null
    )

    private companion object {
        const val API_BASE = "https://api.formula-e.pulselive.com/formula-e/v1"
        const val STATIC_FILES_BASE = "https://static-files.formula-e.pulselive.com"
    }
}

private data class FormulaEChampionshipsResponse(val championships: List<FormulaEChampionship>?)
private data class FormulaEChampionship(val id: String?, val status: String?)

private data class FormulaEDriverStanding(
    val driverId: String?,
    val driverTeamName: String?,
    val driverFirstName: String?,
    val driverLastName: String?,
    val driverPoints: Double?
)

private data class FormulaETeamStanding(
    val teamId: String?,
    val teamName: String?,
    val teamPoints: Double?
)
