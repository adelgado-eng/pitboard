import Foundation

/// Mismo tratamiento que `Moto2StandingsSource` — autosport.com, misma plantilla de
/// tabla. Equivalente exacto de `Moto3StandingsSource.kt`. Fotos de piloto y logos de
/// equipo en vivo desde la API interna de motogp.com, con el UUID de categoría de Moto3.
public final class Moto3StandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: MotorsportStandingsHTMLSource

    public init() {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        inner = MotorsportStandingsHTMLSource(
            category: .moto3,
            driverUrl: "https://www.autosport.com/moto3/standings/\(year)/?type=Driver",
            teamUrl: "https://www.autosport.com/moto3/standings/\(year)/?type=Team",
            pulseliveCategoryUuid: "1ab203aa-e292-4842-8bed-971911357af1"
        )
    }

    public var category: StandingsCategory { inner.category }
    public func fetch(nowUtc: Date) async throws -> [StandingDraft] { try await inner.fetch(nowUtc: nowUtc) }
}
