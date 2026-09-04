import Foundation

/// Mismo sitio y plantilla que `MotoGpStandingsSource` (autosport.com, tabla
/// "Rider"/"Points") — equivalente exacto de `Moto2StandingsSource.kt`. Fotos de piloto y
/// logos de equipo en vivo desde la API interna de motogp.com, con el UUID de categoría
/// de Moto2.
public final class Moto2StandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: MotorsportStandingsHTMLSource

    public init() {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        inner = MotorsportStandingsHTMLSource(
            category: .moto2,
            driverUrl: "https://www.autosport.com/moto2/standings/\(year)/?type=Driver",
            teamUrl: "https://www.autosport.com/moto2/standings/\(year)/?type=Team",
            pulseliveCategoryUuid: "ea854a67-73a4-4a28-ac77-d67b3b2a530a"
        )
    }

    public var category: StandingsCategory { inner.category }
    public func fetch(nowUtc: Date) async throws -> [StandingDraft] { try await inner.fetch(nowUtc: nowUtc) }
}
