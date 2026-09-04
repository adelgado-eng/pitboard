package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * driverdb.com en vez de speedsport-magazine.com (28/08/2026) — esa web tenía foto
 * genérica gris para el 80% de los pilotos. driverdb trae más fotos reales, pero sigue sin
 * cubrir a la mayoría de los ~35 pilotos de la parrilla (revisado a fondo el 02/09/2026: 15
 * de 35 tienen foto real en driverdb, el resto sale con el icono genérico).
 *
 * 02/09/2026 (2): LOS 10 LOGOS DE EQUIPO — la propia driverdb.com tiene una página de
 * equipos que no se había mirado hasta ahora (driverdb.com/championships/porsche-supercup/
 * 2026/teams), con un logo real por equipo en su CDN (storage.googleapis.com/driverdb-media/
 * teams/...) — los 10 comprobados uno a uno visualmente, todos logos limpios de verdad (nada
 * de fotos de coche o camión). Dos nombres no coinciden exactamente entre la tabla de
 * clasificación (de donde sale el nombre de equipo que se usa para buscar en este mapa) y la
 * página de equipos: la tabla dice "Lechner Racing" y "CarTech Motorsport", la página de
 * equipos los tiene como "BWT Lechner Racing" y "Looping by CarTech" (mismo equipo, nombre de
 * patrocinador incluido) — las claves de abajo usan el nombre de la TABLA, que es el que
 * llega aquí.
 *
 * HONESTO — pilotos: este sigue siendo el peor caso de todas las categorías con foto de la
 * app, y merece decirlo con claridad en vez de dejarlo caer. Se comprobaron a mano los 6
 * pilotos pedidos explícitamente (Kieffer, Cauhaupé, Boerekamps, Young, McNeilly, Sumich):
 * solo Kieffer, Young y McNeilly tienen página de Wikipedia, y de esos tres solo Kieffer trae
 * imagen — pero es una foto de SU COCHE en pista, no de él, así que se descartó (mismo
 * criterio que el logo de Campos Racing en F1 Academy: mejor sin foto que con la equivocada).
 * Los otros 5 no tienen ninguna imagen verificable en las fuentes consultadas.
 */
class PorscheSupercupStandingsSource : DriverDbStandingsSource(
    category = StandingsCategory.PORSCHE_SUPERCUP,
    slug = "porsche-supercup",
    teamLogoUrls = TEAM_LOGO_URLS
) {
    companion object {
        private const val LOGO_HOST = "https://storage.googleapis.com/driverdb-media/teams/"

        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "schumacher clrt" to LOGO_HOST + "344/seasons/55385/logo_1784539957.png",
            "lechner racing" to LOGO_HOST + "489/seasons/55385/logo_1784539785.png", // "BWT Lechner Racing" en la página de equipos
            "martinet by almeras" to LOGO_HOST + "1216/seasons/55385/logo_1784551581.png",
            "gp elite" to LOGO_HOST + "491/seasons/55385/logo_1784540264.png",
            "proton competition" to LOGO_HOST + "228/seasons/55385/logo_1784540047.png",
            "cartech motorsport" to LOGO_HOST + "3676/seasons/55385/logo_1784542591.png", // "Looping by CarTech" en la página de equipos
            "dinamic motorsport" to LOGO_HOST + "173/seasons/55385/logo_1784540514.png",
            "target competition" to LOGO_HOST + "481/seasons/55385/logo_1784550976.png",
            "ombra racing" to LOGO_HOST + "492/seasons/55385/logo_1784550354.png",
            "rgb racing team" to LOGO_HOST + "3761/seasons/55385/logo_1784550729.png"
        )
    }
}
