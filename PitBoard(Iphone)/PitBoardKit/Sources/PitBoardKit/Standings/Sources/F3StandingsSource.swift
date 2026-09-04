import Foundation

/// Mismo tratamiento que `F2StandingsSource` (ver allí el detalle) — driverdb.com para
/// pilotos/puntos. Logos de la web oficial (fiaformula3.com/en/standings/2026/teams),
/// mismo CDN "prod-f2f3" que F2/F1 Academy, variante sin sufijo "white" por el mismo
/// motivo. F3 tiene 10 equipos en vez de los 11 de F2 — no corre Invicta Racing.
///
/// Fotos de piloto: igual que F2, driverdb.com casi no tiene fotos reales para F3, así
/// que se completan en vivo desde fiaformula3.com/en/drivers/{slug}.
public final class F3StandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: DriverDbStandingsSource

    public init() {
        inner = DriverDbStandingsSource(
            category: .f3,
            slug: "fia-formula-3",
            teamLogoUrls: Self.teamLogoUrls,
            officialProfileUrlTemplate: "https://www.fiaformula3.com/en/drivers/{slug}"
        )
    }

    public var category: StandingsCategory { inner.category }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        try await inner.fetch(nowUtc: nowUtc)
    }

    private static let logoHost = "https://res.cloudinary.com/prod-f2f3/image/upload/common/f3/2026/"

    /// Claves = nombre de equipo tal como lo trae la columna "Team" de driverdb.com.
    private static let teamLogoUrls: [String: String] = [
        "aix racing": logoHost + "aixracing/2026aixracinglogo.webp",
        "art grand prix": logoHost + "artgrandprix/2026artgrandprixlogo.webp",
        "campos racing": logoHost + "camposracing/2026camposracinglogo.webp",
        "dams": logoHost + "damslucasoil/2026damslucasoillogo.webp",
        "hitech grand prix": logoHost + "hitech/2026hitechlogo.webp",
        "mp motorsport": logoHost + "mpmotorsport/2026mpmotorsportlogo.webp",
        "prema racing": logoHost + "premaracing/2026premaracinglogo.webp",
        "rodin motorsport": logoHost + "rodinmotorsport/2026rodinmotorsportlogo.webp",
        "trident racing": logoHost + "trident/2026tridentlogo.webp",
        "van amersfoort racing": logoHost + "vanamersfoortracing/2026vanamersfoortracinglogo.webp"
    ]
}
