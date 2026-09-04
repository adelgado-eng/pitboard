package com.pitboard.app.standings

/**
 * Clases de coche por categoría "por coche" (ELMS, IMSA, WEC, Le Mans Cup) — extraído de
 * CategoryStandingsScreen.kt (04/09/2026) para que StandingsWidgetConfigActivity pueda usar
 * la MISMA lista y el mismo orden en vez de duplicarla; una sola fuente de verdad para "qué
 * clases tiene esta categoría y en qué orden se enseñan".
 */
object CarBasedStandingsClasses {

    // Orden pedido explícitamente para ELMS: LMP2, LMP2 Pro/Am, LMP3 y GT3 (28/08/2026,
    // coincide con el orden real de las pestañas en europeanlemansseries.com) — nunca
    // pilotos/equipos como el resto, porque ElmsStandingsSource solo guarda equipos por clase.
    private val ELMS_CLASSES = listOf(
        StandingsClass.LMP2 to "LMP2",
        StandingsClass.LMP2_PRO_AM to "LMP2 Pro/Am",
        StandingsClass.LMP3 to "LMP3",
        StandingsClass.LMGT3 to "GT3"
    )

    // Mismo orden que las pestañas de imsa.com/weathertech/standings/ (GTP es la clase por
    // defecto de esa web) — igual que ELMS, siempre como filas de coche/equipo.
    private val IMSA_CLASSES = listOf(
        StandingsClass.GTP to "GTP",
        StandingsClass.LMP2 to "LMP2",
        StandingsClass.GTD_PRO to "GTD Pro",
        StandingsClass.GTD to "GTD"
    )

    // Orden real de las pestañas en fiawec.com (Hypercar es la clase "principal").
    private val WEC_CLASSES = listOf(
        StandingsClass.HYPERCAR to "Hypercar",
        StandingsClass.LMGT3 to "LMGT3"
    )

    // Mismo orden que las secciones de lemanscup.com/en/page/classification.
    private val LEMANS_CUP_CLASSES = listOf(
        StandingsClass.LMP3 to "LMP3",
        StandingsClass.LMP3_PRO_AM to "LMP3 Pro/Am",
        StandingsClass.GT3 to "GT3"
    )

    /** Categorías "por coche": sus filas de equipo representan un coche concreto — el resto
     *  de categorías son de un piloto por coche y no tienen esta noción. */
    val CAR_BASED_CLASSES: Map<StandingsCategory, List<Pair<StandingsClass, String>>> = mapOf(
        StandingsCategory.ELMS to ELMS_CLASSES,
        StandingsCategory.IMSA to IMSA_CLASSES,
        StandingsCategory.WEC to WEC_CLASSES,
        StandingsCategory.LEMANS_CUP to LEMANS_CUP_CLASSES
    )
}
