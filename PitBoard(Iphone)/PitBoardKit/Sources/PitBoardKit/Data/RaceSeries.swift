import Foundation

/// Las series de motorsport que cubre la app — lista FIJA (equivalente exacto de
/// `RaceSeries.kt`). Cada una tiene su propia fuente de calendario en
/// `Schedule/Sources`, igual que en Android. Se mantiene separado de `StandingsCategory`
/// a propósito, mismo motivo que en el original: aunque varias series coinciden entre
/// ambas listas, fusionarlas complicaría las queries sin necesidad real.
///
/// `rawValue` conserva el nombre exacto del enum de Kotlin (ej. "NASCAR_CUP") — no por
/// necesidad técnica (no hay interop directa con Android), sino porque es lo que aparece
/// en los comentarios/decisiones de diseño ya documentados en el proyecto Android y evita
/// tener que traducir mentalmente entre plataformas al leer ambos códigos a la vez.
public enum RaceSeries: String, CaseIterable, Codable, Sendable, Identifiable, Hashable {
    case f1 = "F1"
    case f2 = "F2"
    case f3 = "F3"
    case f1Academy = "F1_ACADEMY"
    case formulaE = "FORMULA_E"
    case wec = "WEC"
    case lemansCup = "LEMANS_CUP"
    case elms = "ELMS"
    case indycar = "INDYCAR"
    case nascarCup = "NASCAR_CUP"
    case nascarXfinity = "NASCAR_XFINITY"
    case nascarTruck = "NASCAR_TRUCK"
    case imsa = "IMSA"
    case porscheSupercup = "PORSCHE_SUPERCUP"
    case motoGp = "MOTOGP"
    case moto2 = "MOTO2"
    case moto3 = "MOTO3"
    case gtChallengeEurope = "GT_CHALLENGE_EUROPE"
    case gtChallengeAmerica = "GT_CHALLENGE_AMERICA"
    case gtChallengeAsia = "GT_CHALLENGE_ASIA"
    case gtChallengeAustralia = "GT_CHALLENGE_AUSTRALIA"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .f1: "Formula 1"
        case .f2: "Formula 2"
        case .f3: "Formula 3"
        case .f1Academy: "F1 Academy"
        case .formulaE: "Formula E"
        case .wec: "FIA World Endurance Championship"
        case .lemansCup: "Michelin Le Mans Cup"
        case .elms: "European Le Mans Series"
        case .indycar: "IndyCar"
        case .nascarCup: "NASCAR Cup Series"
        case .nascarXfinity: "NASCAR O'Reilly Auto Parts Series"
        case .nascarTruck: "NASCAR Craftsman Truck Series"
        case .imsa: "IMSA SportsCar Championship"
        case .porscheSupercup: "Porsche Supercup"
        case .motoGp: "MotoGP"
        case .moto2: "Moto2"
        case .moto3: "Moto3"
        case .gtChallengeEurope: "GT World Challenge Europe"
        case .gtChallengeAmerica: "GT World Challenge America"
        case .gtChallengeAsia: "GT World Challenge Asia"
        case .gtChallengeAustralia: "GT World Challenge Australia"
        }
    }

    /// Valor de fábrica del tag corto — el usuario puede cambiarlo desde el editor de
    /// series (equivalente del botón lápiz en Eventos); se guarda en SeriesConfigModel.
    public var defaultTag: String {
        switch self {
        case .f1: "F1"
        case .f2: "F2"
        case .f3: "F3"
        case .f1Academy: "F1A"
        case .formulaE: "FE"
        case .wec: "WEC"
        case .lemansCup: "MLMC"
        case .elms: "ELMS"
        case .indycar: "IND"
        case .nascarCup: "NCU"
        case .nascarXfinity: "NXS"
        case .nascarTruck: "NCTS"
        case .imsa: "IMSA"
        case .porscheSupercup: "PSC"
        case .motoGp: "MGP"
        case .moto2: "MT2"
        case .moto3: "MT3"
        case .gtChallengeEurope: "GTWE"
        case .gtChallengeAmerica: "GTWA"
        case .gtChallengeAsia: "GTAS"
        case .gtChallengeAustralia: "GTAU"
        }
    }

    public var defaultColorHex: String {
        switch self {
        case .f1: "#E10600"
        case .f2: "#0090FF"
        case .f3: "#00A19C"
        case .f1Academy: "#E80CB2"
        case .formulaE: "#01A6C7"
        case .wec: "#0B4F6C"
        case .lemansCup: "#5B7FBF"
        case .elms: "#2EC4B6"
        case .indycar: "#2E6DE8"
        case .nascarCup: "#F2A93B"
        case .nascarXfinity: "#7A9E2E"
        case .nascarTruck: "#C9A227"
        case .imsa: "#00B2A9"
        case .porscheSupercup: "#FF5DA2"
        case .motoGp: "#E4372F"
        case .moto2: "#EB6E1F"
        case .moto3: "#F7C948"
        case .gtChallengeEurope: "#7C4DFF"
        case .gtChallengeAmerica: "#9C6DFF"
        case .gtChallengeAsia: "#B98DFF"
        case .gtChallengeAustralia: "#6B3FD4"
        }
    }
}
