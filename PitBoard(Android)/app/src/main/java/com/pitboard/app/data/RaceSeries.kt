package com.pitboard.app.data

/**
 * Las series de motorsport que cubre la app. Es una lista FIJA (ya no depende de lo que el
 * usuario importe) — cada una tiene una fuente de calendario propia en
 * com.pitboard.app.schedule.sources, igual que StandingsCategory tiene sus fuentes en
 * com.pitboard.app.standings.sources. Se mantiene como enum separado de StandingsCategory
 * a propósito: aunque 7 series coinciden entre ambas listas, StandingsCategory pertenece a
 * un módulo ya estable y tocarlo para fusionarlo aquí habría sido un riesgo innecesario para
 * este cambio.
 *
 * `defaultTag`/`defaultColorHex` son el valor de fábrica — el usuario puede cambiarlos desde
 * el editor de series (botón lápiz en Eventos), que los guarda en SeriesConfigEntity. Si no
 * hay fila en SeriesConfigEntity todavía (primer arranque), se usan estos.
 */
enum class RaceSeries(val displayName: String, val defaultTag: String, val defaultColorHex: String) {
    F1("Formula 1", "F1", "#E10600"),
    F2("Formula 2", "F2", "#0090FF"),
    F3("Formula 3", "F3", "#00A19C"),
    F1_ACADEMY("F1 Academy", "F1A", "#E80CB2"),
    FORMULA_E("Formula E", "FE", "#01A6C7"),
    WEC("FIA World Endurance Championship", "WEC", "#0B4F6C"),
    LEMANS_CUP("Michelin Le Mans Cup", "MLMC", "#5B7FBF"),
    ELMS("European Le Mans Series", "ELMS", "#2EC4B6"),
    INDYCAR("IndyCar", "IND", "#2E6DE8"),
    NASCAR_CUP("NASCAR Cup Series", "NCU", "#F2A93B"),
    NASCAR_XFINITY("NASCAR O'Reilly Auto Parts Series", "NXS", "#7A9E2E"),
    NASCAR_TRUCK("NASCAR Craftsman Truck Series", "NCTS", "#C9A227"),
    IMSA("IMSA SportsCar Championship", "IMSA", "#00B2A9"),
    PORSCHE_SUPERCUP("Porsche Supercup", "PSC", "#FF5DA2"),
    MOTOGP("MotoGP", "MGP", "#E4372F"),
    MOTO2("Moto2", "MT2", "#EB6E1F"),
    MOTO3("Moto3", "MT3", "#F7C948"),
    GT_CHALLENGE_EUROPE("GT World Challenge Europe", "GTWE", "#7C4DFF"),
    GT_CHALLENGE_AMERICA("GT World Challenge America", "GTWA", "#9C6DFF"),
    GT_CHALLENGE_ASIA("GT World Challenge Asia", "GTAS", "#B98DFF"),
    GT_CHALLENGE_AUSTRALIA("GT World Challenge Australia", "GTAU", "#6B3FD4")
}
