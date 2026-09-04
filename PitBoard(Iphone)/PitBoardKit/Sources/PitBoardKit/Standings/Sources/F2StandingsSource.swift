import Foundation

/// driverdb.com (mismo patrón que F1/NASCAR/IndyCar/F1 Academy/Porsche Supercup) — un
/// piloto por coche, así que no hace falta el tratamiento "por coche" de ELMS/IMSA.
///
/// Logos de equipo: de la web oficial (fiaformula2.com/en/standings/2026/teams), CDN de
/// Cloudinary "prod-f2f3" (F1 Academy, F2 y F3 son las tres categorías "feeder" del mismo
/// grupo F1, comparten infraestructura web) — variante sin sufijo de color ("...logo.webp"),
/// no la blanca ("logowhite.webp"), que se perdería contra el círculo blanco de fondo de
/// la UI.
///
/// Fotos de piloto: driverdb.com casi no tiene fotos reales para F2, así que se
/// completan en vivo desde la ficha oficial de cada piloto
/// (fiaformula2.com/en/drivers/{slug}).
public final class F2StandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: DriverDbStandingsSource

    public init() {
        inner = DriverDbStandingsSource(
            category: .f2,
            slug: "fia-formula-2",
            teamLogoUrls: Self.teamLogoUrls,
            officialProfileUrlTemplate: "https://www.fiaformula2.com/en/drivers/{slug}"
        )
    }

    public var category: StandingsCategory { inner.category }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        try await inner.fetch(nowUtc: nowUtc)
    }

    private static let logoHost = "https://res.cloudinary.com/prod-f2f3/image/upload/common/f2/2026/"

    /// Claves = nombre de equipo tal como lo trae la columna "Team" de driverdb.com.
    private static let teamLogoUrls: [String: String] = [
        "aix racing": logoHost + "aixracing/2026aixracinglogo.webp",
        "art grand prix": logoHost + "artgrandprix/2026artgrandprixlogo.webp",
        "campos racing": logoHost + "camposracing/2026camposracinglogo.webp",
        "dams": logoHost + "damslucasoil/2026damslucasoillogo.webp",
        "hitech grand prix": logoHost + "hitech/2026hitechlogo.webp",
        "invicta racing": logoHost + "invictaracing/2026invictaracinglogo.webp",
        "mp motorsport": logoHost + "mpmotorsport/2026mpmotorsportlogo.webp",
        "prema racing": logoHost + "premaracing/2026premaracinglogo.webp",
        "rodin motorsport": logoHost + "rodinmotorsport/2026rodinmotorsportlogo.webp",
        "trident racing": logoHost + "trident/2026tridentlogo.webp",
        "van amersfoort racing": logoHost + "vanamersfoortracing/2026vanamersfoortracinglogo.webp"
    ]
}
