package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * driverdb.com en vez de autosport.com (28/08/2026) — autosport no traía fotos para F1
 * Academy. driverdb trae 22 filas, pero la parrilla confirmada de 2026 son 17 pilotas — el
 * resto son entradas sin confirmar/reserva a 0 puntos. Se filtran contra el directorio de
 * pilotas de motorsport.com (ver RosterNameFilter).
 *
 * 02/09/2026: dos correcciones importantes.
 * 1. El logo de Campos Racing venía de una foto del lateral de su camión de transporte
 *    (así lo tenía también la propia Wikipedia como "logo"), no de un logo de verdad —
 *    cambiado, junto con los otros 5 equipos, a los logos oficiales de f1academy.com
 *    (f1academy.com/Racing-Series/Teams), su propio CDN, más limpios y consistentes entre sí
 *    que la mezcla de Wikimedia/Wikipedia de antes.
 * 2. driverdb.com solo tenía foto real para una minoría de las pilotas de F1 Academy (la
 *    mayoría salían con el icono genérico) — se añade DRIVER_PHOTO_URLS como respaldo, con
 *    las fotos oficiales de f1academy.com/Racing-Series/Drivers (mismo CDN que los equipos).
 *    18 de las ~21 filas de la tabla quedan cubiertas; las 3 restantes (Bättig, Wei, Fisher)
 *    no tienen foto en esa página tampoco, así que se quedan con el icono por defecto, como
 *    antes — nunca desaparecen de la clasificación.
 */
class F1AcademyStandingsSource : DriverDbStandingsSource(
    category = StandingsCategory.F1_ACADEMY,
    slug = "f1-academy",
    knownRosterUrl = "https://www.motorsport.com/f1-academy/drivers/",
    teamLogoUrls = TEAM_LOGO_URLS,
    driverPhotoUrls = DRIVER_PHOTO_URLS
) {
    companion object {
        private const val DRIVER_PHOTO_HOST = "https://res.cloudinary.com/prod-f2f3/image/upload/"

        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "art grand prix" to DRIVER_PHOTO_HOST + "v1736943411/FA/Global/teams/logos/F1A_Teams_ART.png",
            "campos racing" to DRIVER_PHOTO_HOST + "v1736943446/FA/Global/teams/logos/F1A_Teams_Campos.png",
            "hitech grand prix" to DRIVER_PHOTO_HOST + "v1768219948/FA/Global/teams/logos/Hitech_Logo_2026.png",
            "mp motorsport" to DRIVER_PHOTO_HOST + "v1736943449/FA/Global/teams/logos/F1A_Teams_MP.png",
            "prema racing" to DRIVER_PHOTO_HOST + "v1736943450/FA/Global/teams/logos/F1A_Teams_Prema.png",
            "rodin motorsport" to DRIVER_PHOTO_HOST + "v1736943452/FA/Global/teams/logos/F1A_Teams_Rodin.png"
        )

        /** Claves = nombre de la pilota normalizado tal como lo trae driverdb.com (ver
         *  DriverDbStandingsSource.normalize: minúsculas, sin tildes ni signos). Comprobadas
         *  una a una (HTTP 200, foto real) el 02/09/2026. */
        private val DRIVER_PHOTO_URLS: Map<String, String> = mapOf(
            "alisha palmowski" to DRIVER_PHOTO_HOST + "v1772642010/FA/Global/drivers/2026/Alisha_Palmowski_Profile.jpg",
            "emma felbermayr" to DRIVER_PHOTO_HOST + "v1772642018/FA/Global/drivers/2026/Emma_Felbermayr_Profile.jpg",
            "nina gademan" to DRIVER_PHOTO_HOST + "v1772642029/FA/Global/drivers/2026/Nina_Gademan_Profile.jpg",
            "alba larsen" to DRIVER_PHOTO_HOST + "v1772642008/FA/Global/drivers/2026/Alba_Larsen_Profile.jpg",
            "megan bruce" to DRIVER_PHOTO_HOST + "v1772642027/FA/Global/drivers/2026/Megan_Bruce_Profile.jpg",
            "payton westcott" to DRIVER_PHOTO_HOST + "v1773172129/FA/Global/drivers/2026/Payton_Profile.jpg",
            "ella lloyd" to DRIVER_PHOTO_HOST + "v1772642013/FA/Global/drivers/2026/Ella_Lloyd_Profile.jpg",
            "mathilda paatz" to DRIVER_PHOTO_HOST + "v1773172140/FA/Global/drivers/2026/Mathilda_Profile.jpg",
            "natalia granada ferrero" to DRIVER_PHOTO_HOST + "v1773263612/FA/Global/drivers/2026/Natalia_Granada_Profile.jpg",
            "rafaela ferreira" to DRIVER_PHOTO_HOST + "v1772642034/FA/Global/drivers/2026/Rafaela_Ferreira_Profile.jpg",
            "rachel robertson" to DRIVER_PHOTO_HOST + "v1772642031/FA/Global/drivers/2026/Rachel_Robertson_Profile.jpg",
            "lisa billard" to DRIVER_PHOTO_HOST + "v1772642025/FA/Global/drivers/2026/Lisa_Billard_Profile.jpg",
            "kaylee countryman" to DRIVER_PHOTO_HOST + "v1772642023/FA/Global/drivers/2026/Kaylee_Countryman_Profile.jpg",
            "ava dobson" to DRIVER_PHOTO_HOST + "v1772642012/FA/Global/drivers/2026/Ava_Dobson_Profile.jpg",
            "esmee kosterman" to DRIVER_PHOTO_HOST + "v1772642019/FA/Global/drivers/2026/Esmee_Kosterman_Profile.jpg",
            "ella stevens" to DRIVER_PHOTO_HOST + "v1772642015/FA/Global/drivers/2026/Ella_Stevens_Profile.jpg",
            "jade jacquet" to DRIVER_PHOTO_HOST + "v1772642021/FA/Global/drivers/2026/Jade_Jacquet_Profile.jpg",
            // "Zoe Florescu-Potolea" -> el guion desaparece al normalizar (no es letra/número
            // ni espacio), así que "Florescu-Potolea" se queda pegado en "florescupotolea".
            "zoe florescupotolea" to DRIVER_PHOTO_HOST + "f_auto/q_auto/v1786029316/FA/Global/drivers/2026/Zoe_Florescu_Profile.jpg"
        )
    }
}
