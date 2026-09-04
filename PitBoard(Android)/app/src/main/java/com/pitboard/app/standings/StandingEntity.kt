package com.pitboard.app.standings

import androidx.room.Entity
import androidx.room.Index

/**
 * Identificador estable de cada una de las 15 categorías con clasificación.
 *
 * `logoUrl` apunta a una imagen alojada fuera de la app (igual que las fotos de piloto),
 * no a un recurso local — este entorno de desarrollo no tiene forma de descargar ni
 * verificar bytes de imagen para empaquetarlos como drawable. Son URLs de mejor esfuerzo
 * (28/08/2026); si alguna no carga bien al probar la app, se cambia por otra.
 */
enum class StandingsCategory(val displayName: String, val hasTeamStandings: Boolean, val logoUrl: String) {
    F1(
        "Formula 1", hasTeamStandings = true,
        logoUrl = "https://logos-world.net/wp-content/uploads/2023/12/F1-Logo.png"
    ),
    F2(
        "Formula 2", hasTeamStandings = true,
        // 02/09/2026: comprobado visualmente (HTTP 200 + logo real de verdad, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/8/87/FIA_Formula_2_Championship_logo.jpg"
    ),
    F3(
        "Formula 3", hasTeamStandings = true,
        // 02/09/2026: comprobado visualmente (HTTP 200 + logo real de verdad, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/5/5b/FIA_F3_Championship_logo.png"
    ),
    F1_ACADEMY(
        "F1 Academy", hasTeamStandings = true,
        // "theme/dark" es la variante pensada para fondo oscuro (texto claro) — sobre el
        // círculo blanco de la app casi no se vería, por eso es "theme/light".
        logoUrl = "https://cdn.brandfetch.io/id0rFpPDnI/w/400/h/400/theme/light/icon.jpeg?c=1bxid64Mup7aczewSAYMX&t=1772610871630"
    ),
    FORMULA_E(
        "Formula E", hasTeamStandings = true,
        // 03/09/2026: comprobado visualmente (HTTP 200 + logo real, no un 404). Corregido
        // el mismo día el error de la nota anterior ("sin fuente, la temporada no empieza
        // hasta diciembre de 2026") — esa fecha es la de la temporada SIGUIENTE (2026-27);
        // la 2025-26 (Season 12) ya está en marcha desde diciembre de 2025 y tiene
        // clasificación real. Ver FormulaEStandingsSource.
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9b/Formula_E_Logo.png"
    ),
    MOTOGP(
        "MotoGP", hasTeamStandings = true,
        logoUrl = "https://logos-world.net/wp-content/uploads/2025/07/MotoGP-Logo.png"
    ),
    MOTO2(
        "Moto2", hasTeamStandings = true,
        // 03/09/2026: comprobado visualmente (HTTP 200 + logo real, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/ab/Moto2_logo_%282024%29.png"
    ),
    MOTO3(
        "Moto3", hasTeamStandings = true,
        // 03/09/2026: comprobado visualmente (HTTP 200 + logo real, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/5/51/Moto3_Logo_2026.png"
    ),
    NASCAR_CUP(
        "NASCAR Cup", hasTeamStandings = true,
        logoUrl = "https://logos-world.net/wp-content/uploads/2021/09/NASCAR-Logo.png"
    ),
    INDYCAR(
        "IndyCar", hasTeamStandings = true,
        // 28/08/2026: logo oficial de Wikipedia, mejor calidad que el anterior.
        logoUrl = "https://www.indycar.com/-/media/IndyCar/Logos/INDYCAR-Dark.png"
    ),
    PORSCHE_SUPERCUP(
        "Porsche Supercup", hasTeamStandings = true,
        // 28/08/2026: logo oficial de Wikipedia, mejor calidad que el anterior.
        logoUrl = "https://cdn.brandfetch.io/racing.porsche.com/w/400/h/400/theme/light/icon.jpeg?c=1bxid64Mup7aczewSAYMX"
    ),
    WEC(
        "FIA World Endurance Championship", hasTeamStandings = true,
        // 03/09/2026: comprobado visualmente (HTTP 200 + logo real, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/4/4c/FIA_WEC_Logo_2024.png"
    ),
    LEMANS_CUP(
        "Michelin Le Mans Cup", hasTeamStandings = true,
        // 03/09/2026: comprobado visualmente (HTTP 200 + logo real, no un 404).
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/f/f5/LeMansCup_logo.png"
    ),
    ELMS(
        "European Le Mans Series", hasTeamStandings = true,
        logoUrl = "https://www.freelogovectors.net/wp-content/uploads/2020/03/european-lemans-series-logo.png"
    ),
    IMSA(
        "IMSA WeatherTech SportsCar Championship", hasTeamStandings = true,
        logoUrl = "https://www.imsa.com/wp-content/uploads/sites/32/2023/01/03/2023_IMSA_Logo_639x240.png"
    );

    /** Para las 4 categorías "por coche" (ELMS/IMSA/WEC/Le Mans Cup): estas nunca guardan
     *  filas OVERALL/DRIVER (solo TEAM, por clase — ver ElmsStandingsSource,
     *  ImsaStandingsSource, WecStandingsSource, LeMansCupStandingsSource), así que el
     *  "líder" que se enseña en la lista de Clasificaciones (ver StandingsViewModel.
     *  leaderByCategory) tiene que ser el equipo en cabeza de su clase principal en vez de
     *  un piloto en cabeza que nunca va a existir. La clase elegida es la misma "por
     *  defecto"/primera pestaña que usa CategoryStandingsScreen (CAR_BASED_CLASSES) —
     *  null para el resto de categorías, que sí tienen clasificación de piloto normal. */
    val primaryCarClass: StandingsClass?
        get() = when (this) {
            ELMS -> StandingsClass.LMP2
            IMSA -> StandingsClass.GTP
            WEC -> StandingsClass.HYPERCAR
            LEMANS_CUP -> StandingsClass.LMP3
            else -> null
        }
}

/** ELMS, IMSA, WEC y Le Mans Cup corren varias clases en paralelo; el resto de
 *  categorías solo usan [OVERALL]. GTP/GTD_PRO/GTD son de IMSA, HYPERCAR es solo de WEC,
 *  LMP3_PRO_AM y GT3 son solo de Le Mans Cup — LMP2 y LMP3 se comparten con ELMS, y
 *  LMGT3 con WEC (mismo concepto de clase entre esas categorías, cada fila ya va
 *  etiquetada con su [StandingsCategory] así que no hay ambigüedad). */
enum class StandingsClass {
    OVERALL,
    LMP2,
    LMP2_PRO_AM,
    LMP3,
    LMP3_PRO_AM,
    LMGT3,
    GT3,
    HYPERCAR,
    GTP,
    GTD_PRO,
    GTD
}

enum class StandingType {
    DRIVER,
    TEAM
}

/**
 * Una fila de una clasificación: puede ser un piloto o un equipo, dentro de una
 * categoría (y, para ELMS, dentro de una clase concreta).
 *
 * `entrantKey` identifica la fila de forma estable entre actualizaciones (ej: el
 * "driverId" de la API de turno, o el nombre normalizado si la fuente es HTML) —
 * se usa como parte de la clave primaria para que un REPLACE en cada sync no deje
 * filas huérfanas de pilotos que ya no puntúan.
 */
@Entity(
    tableName = "standings",
    primaryKeys = ["category", "standingsClass", "type", "entrantKey"],
    indices = [Index(value = ["category", "standingsClass", "type"])]
)
data class StandingEntity(
    val category: StandingsCategory,
    val standingsClass: StandingsClass,
    val type: StandingType,
    val entrantKey: String,
    val position: Int,
    /** Nombre del piloto, o de los pilotos separados por " / " si es un equipo de resistencia. */
    val name: String,
    /** Equipo/escudería (vacío si `type` es TEAM). */
    val team: String,
    val points: Double,
    /** URL de la foto del piloto/equipo, cuando la fuente la trae (ver fuentes que usan
     *  speedsport-magazine.com) — null si no hay disponible para esta categoría. */
    val photoUrl: String? = null,
    val updatedAtUtc: Long
)