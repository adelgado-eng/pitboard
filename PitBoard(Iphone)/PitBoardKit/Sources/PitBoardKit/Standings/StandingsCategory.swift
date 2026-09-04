import Foundation

/// Identificador estable de cada una de las 15 categorías con clasificación — equivalente
/// exacto de `StandingsCategory.kt`, `logoUrl` incluido tal cual (apunta a una imagen
/// alojada fuera de la app, no a un asset local — mismo criterio que en Android: son URLs
/// de mejor esfuerzo, si alguna deja de cargar se cambia por otra).
public enum StandingsCategory: String, CaseIterable, Codable, Sendable, Identifiable, Hashable {
    case f1 = "F1"
    case f2 = "F2"
    case f3 = "F3"
    case f1Academy = "F1_ACADEMY"
    case formulaE = "FORMULA_E"
    case motoGp = "MOTOGP"
    case moto2 = "MOTO2"
    case moto3 = "MOTO3"
    case nascarCup = "NASCAR_CUP"
    case indycar = "INDYCAR"
    case porscheSupercup = "PORSCHE_SUPERCUP"
    case wec = "WEC"
    case lemansCup = "LEMANS_CUP"
    case elms = "ELMS"
    case imsa = "IMSA"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .f1: "Formula 1"
        case .f2: "Formula 2"
        case .f3: "Formula 3"
        case .f1Academy: "F1 Academy"
        case .formulaE: "Formula E"
        case .motoGp: "MotoGP"
        case .moto2: "Moto2"
        case .moto3: "Moto3"
        case .nascarCup: "NASCAR Cup"
        case .indycar: "IndyCar"
        case .porscheSupercup: "Porsche Supercup"
        case .wec: "FIA World Endurance Championship"
        case .lemansCup: "Michelin Le Mans Cup"
        case .elms: "European Le Mans Series"
        case .imsa: "IMSA WeatherTech SportsCar Championship"
        }
    }

    public var hasTeamStandings: Bool { true }

    public var logoUrl: String {
        switch self {
        case .f1: "https://logos-world.net/wp-content/uploads/2023/12/F1-Logo.png"
        case .f2: "https://upload.wikimedia.org/wikipedia/commons/8/87/FIA_Formula_2_Championship_logo.jpg"
        case .f3: "https://upload.wikimedia.org/wikipedia/commons/5/5b/FIA_F3_Championship_logo.png"
        case .f1Academy: "https://cdn.brandfetch.io/id0rFpPDnI/w/400/h/400/theme/light/icon.jpeg?c=1bxid64Mup7aczewSAYMX&t=1772610871630"
        case .formulaE: "https://upload.wikimedia.org/wikipedia/commons/9/9b/Formula_E_Logo.png"
        case .motoGp: "https://logos-world.net/wp-content/uploads/2025/07/MotoGP-Logo.png"
        case .moto2: "https://upload.wikimedia.org/wikipedia/commons/a/ab/Moto2_logo_%282024%29.png"
        case .moto3: "https://upload.wikimedia.org/wikipedia/commons/5/51/Moto3_Logo_2026.png"
        case .nascarCup: "https://logos-world.net/wp-content/uploads/2021/09/NASCAR-Logo.png"
        case .indycar: "https://www.indycar.com/-/media/IndyCar/Logos/INDYCAR-Dark.png"
        case .porscheSupercup: "https://cdn.brandfetch.io/racing.porsche.com/w/400/h/400/theme/light/icon.jpeg?c=1bxid64Mup7aczewSAYMX"
        case .wec: "https://upload.wikimedia.org/wikipedia/commons/4/4c/FIA_WEC_Logo_2024.png"
        case .lemansCup: "https://upload.wikimedia.org/wikipedia/commons/f/f5/LeMansCup_logo.png"
        case .elms: "https://www.freelogovectors.net/wp-content/uploads/2020/03/european-lemans-series-logo.png"
        case .imsa: "https://www.imsa.com/wp-content/uploads/sites/32/2023/01/03/2023_IMSA_Logo_639x240.png"
        }
    }

    /// Para las 4 categorías "por coche" (ELMS/IMSA/WEC/Le Mans Cup): estas nunca guardan
    /// filas `.overall`/`.driver` (solo `.team`, por clase), así que el "líder" que se
    /// enseña en `StandingsScreen` tiene que ser el equipo en cabeza de su clase
    /// principal en vez de un piloto en cabeza que nunca va a existir. Misma clase
    /// "por defecto"/primera pestaña que usa `CategoryStandingsScreen.carBasedClasses` —
    /// `nil` para el resto, que sí tienen clasificación de piloto normal.
    public var primaryCarClass: StandingsClass? {
        switch self {
        case .elms: .lmp2
        case .imsa: .gtp
        case .wec: .hypercar
        case .lemansCup: .lmp3
        default: nil
        }
    }
}

/// ELMS, IMSA, WEC y Le Mans Cup corren varias clases en paralelo; el resto de categorías
/// solo usan `.overall`. Equivalente exacto de `StandingsClass.kt`.
public enum StandingsClass: String, CaseIterable, Codable, Sendable {
    case overall = "OVERALL"
    case lmp2 = "LMP2"
    case lmp2ProAm = "LMP2_PRO_AM"
    case lmp3 = "LMP3"
    case lmp3ProAm = "LMP3_PRO_AM"
    case lmgt3 = "LMGT3"
    case gt3 = "GT3"
    case hypercar = "HYPERCAR"
    case gtp = "GTP"
    case gtdPro = "GTD_PRO"
    case gtd = "GTD"
}

public enum StandingType: String, CaseIterable, Codable, Sendable {
    case driver = "DRIVER"
    case team = "TEAM"
}
