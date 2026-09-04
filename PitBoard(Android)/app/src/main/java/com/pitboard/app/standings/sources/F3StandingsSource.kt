package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * Mismo tratamiento que F2StandingsSource.kt (ver allí el detalle) — driverdb.com para
 * pilotos/puntos, comprobado con curl el 02/09/2026
 * (driverdb.com/championships/fia-formula-3/2026/standings, tabla real). Logos de la web
 * oficial (fiaformula3.com/en/standings/2026/teams), mismo CDN "prod-f2f3" que F2/F1
 * Academy, variante sin sufijo "white" por el mismo motivo (se perdería sobre fondo
 * blanco). F3 tiene 10 equipos en vez de los 11 de F2 — no corre Invicta Racing.
 *
 * Fotos de piloto (03/09/2026): igual que F2, driverdb.com casi no tiene fotos reales
 * para F3, así que se completan en vivo desde fiaformula3.com/en/drivers/{slug} —
 * comprobada visualmente con Brando Badoer (Rodin Motorsport): foto real, no un
 * placeholder. Ver DriverDbStandingsSource.officialProfileUrlTemplate.
 */
class F3StandingsSource : DriverDbStandingsSource(
    category = StandingsCategory.F3,
    slug = "fia-formula-3",
    teamLogoUrls = TEAM_LOGO_URLS,
    officialProfileUrlTemplate = "https://www.fiaformula3.com/en/drivers/{slug}"
) {
    companion object {
        private const val LOGO_HOST = "https://res.cloudinary.com/prod-f2f3/image/upload/common/f3/2026/"

        /** Claves = nombre de equipo tal como lo trae la columna "Team" de driverdb.com,
         *  comprobado a mano el 02/09/2026 (campo `team_name` de las 10 filas de la
         *  tabla). */
        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "aix racing" to LOGO_HOST + "aixracing/2026aixracinglogo.webp",
            "art grand prix" to LOGO_HOST + "artgrandprix/2026artgrandprixlogo.webp",
            "campos racing" to LOGO_HOST + "camposracing/2026camposracinglogo.webp",
            "dams" to LOGO_HOST + "damslucasoil/2026damslucasoillogo.webp",
            "hitech grand prix" to LOGO_HOST + "hitech/2026hitechlogo.webp",
            "mp motorsport" to LOGO_HOST + "mpmotorsport/2026mpmotorsportlogo.webp",
            "prema racing" to LOGO_HOST + "premaracing/2026premaracinglogo.webp",
            "rodin motorsport" to LOGO_HOST + "rodinmotorsport/2026rodinmotorsportlogo.webp",
            "trident racing" to LOGO_HOST + "trident/2026tridentlogo.webp",
            "van amersfoort racing" to LOGO_HOST + "vanamersfoortracing/2026vanamersfoortracinglogo.webp"
        )
    }
}
