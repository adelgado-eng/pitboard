import Foundation

/// Clases de coche por categoría "por coche" (ELMS, IMSA, WEC, Le Mans Cup) — extraído de
/// `CategoryStandingsScreen.swift` (04/09/2026, equivalente exacto de
/// `CarBasedStandingsClasses.kt`) para que `StandingsWidgetConfigurationIntent` (extensión
/// de widget, target distinto de la app) pueda usar la MISMA lista en vez de duplicarla.
public enum CarBasedStandingsClasses {
    // Órdenes reales de cada web de origen — ver comentarios en CategoryStandingsScreen.kt.
    public static let elmsClasses: [(StandingsClass, String)] = [
        (.lmp2, "LMP2"), (.lmp2ProAm, "LMP2 Pro/Am"), (.lmp3, "LMP3"), (.lmgt3, "GT3")
    ]
    public static let imsaClasses: [(StandingsClass, String)] = [
        (.gtp, "GTP"), (.lmp2, "LMP2"), (.gtdPro, "GTD Pro"), (.gtd, "GTD")
    ]
    public static let wecClasses: [(StandingsClass, String)] = [
        (.hypercar, "Hypercar"), (.lmgt3, "LMGT3")
    ]
    public static let lemansCupClasses: [(StandingsClass, String)] = [
        (.lmp3, "LMP3"), (.lmp3ProAm, "LMP3 Pro/Am"), (.gt3, "GT3")
    ]

    /// Categorías "por coche": sus filas de equipo representan un coche concreto — el resto
    /// de categorías son de un piloto por coche y no tienen esta noción.
    public static let carBasedClasses: [StandingsCategory: [(StandingsClass, String)]] = [
        .elms: elmsClasses, .imsa: imsaClasses, .wec: wecClasses, .lemansCup: lemansCupClasses
    ]
}
