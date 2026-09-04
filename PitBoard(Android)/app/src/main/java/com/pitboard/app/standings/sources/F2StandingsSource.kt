package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * driverdb.com (mismo patrón que F1/NASCAR/IndyCar/F1 Academy/Porsche Supercup) — un
 * piloto por coche, así que no hace falta el tratamiento especial "por coche" de
 * ELMS/IMSA. Comprobado con curl el 02/09/2026: driverdb.com/championships/fia-formula-2/
 * 2026/standings responde con la tabla "2026 driver Standings" en el mismo formato que
 * ya parsea DriverDbStandingsSource.
 *
 * Logos de equipo: de la propia web oficial (fiaformula2.com/en/standings/2026/teams),
 * mismo CDN de Cloudinary que ya usa F1 Academy ("prod-f2f3" — F1 Academy, F2 y F3 son
 * las tres categorías "feeder" del mismo grupo F1, comparten infraestructura web). La
 * URL de esa página trae la variante "logowhite.webp" (blanca sobre transparente, se
 * perdería contra el círculo blanco de fondo de la UI) — se usa en su lugar la variante
 * sin sufijo de color ("...logo.webp"), comprobada uno a uno el 02/09/2026 (HTTP 200,
 * las 11 son logos reales a color, no fotos ni placeholders).
 *
 * Fotos de piloto (03/09/2026): driverdb.com casi no tiene fotos reales para F2 (la
 * mayoría de filas traían el icono genérico), así que se completan en vivo desde la
 * ficha oficial de cada piloto (fiaformula2.com/en/drivers/{slug}) — comprobado a mano
 * con Rafael Câmara (Invicta Racing): la ficha trae su foto de estudio real, no un
 * placeholder. Ver DriverDbStandingsSource.officialProfileUrlTemplate.
 */
class F2StandingsSource : DriverDbStandingsSource(
    category = StandingsCategory.F2,
    slug = "fia-formula-2",
    teamLogoUrls = TEAM_LOGO_URLS,
    officialProfileUrlTemplate = "https://www.fiaformula2.com/en/drivers/{slug}"
) {
    companion object {
        private const val LOGO_HOST = "https://res.cloudinary.com/prod-f2f3/image/upload/common/f2/2026/"

        /** Claves = nombre de equipo tal como lo trae la columna "Team" de driverdb.com,
         *  comprobado a mano el 02/09/2026 (campo `team_name` de las 11 filas de la
         *  tabla). */
        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "aix racing" to LOGO_HOST + "aixracing/2026aixracinglogo.webp",
            "art grand prix" to LOGO_HOST + "artgrandprix/2026artgrandprixlogo.webp",
            "campos racing" to LOGO_HOST + "camposracing/2026camposracinglogo.webp",
            "dams" to LOGO_HOST + "damslucasoil/2026damslucasoillogo.webp",
            "hitech grand prix" to LOGO_HOST + "hitech/2026hitechlogo.webp",
            "invicta racing" to LOGO_HOST + "invictaracing/2026invictaracinglogo.webp",
            "mp motorsport" to LOGO_HOST + "mpmotorsport/2026mpmotorsportlogo.webp",
            "prema racing" to LOGO_HOST + "premaracing/2026premaracinglogo.webp",
            "rodin motorsport" to LOGO_HOST + "rodinmotorsport/2026rodinmotorsportlogo.webp",
            "trident racing" to LOGO_HOST + "trident/2026tridentlogo.webp",
            "van amersfoort racing" to LOGO_HOST + "vanamersfoortracing/2026vanamersfoortracinglogo.webp"
        )
    }
}
