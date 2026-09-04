import Foundation

/// formula1.com en vez de driverdb.com — driverdb incluía pilotos reserva/de test sin
/// carreras disputadas y no siempre desempataba bien las posiciones en caso de empate a
/// puntos. La tabla oficial de F1 trae exactamente los pilotos que han corrido esta
/// temporada, ya con las posiciones bien desempatadas — pero sin fotos, así que se
/// completan (mejor esfuerzo) buscando el nombre en driverdb, con la foto oficial de la
/// página de perfil de cada piloto en formula1.com con prioridad sobre la de driverdb.
public final class F1StandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: OfficialRosterStandingsSource

    public init() {
        inner = OfficialRosterStandingsSource(
            category: .f1,
            rosterUrl: "https://www.formula1.com/en/results/2026/drivers",
            driverDbSlug: "formula-1",
            officialProfileUrlTemplate: "https://www.formula1.com/en/drivers/{slug}"
        )
    }

    public var category: StandingsCategory { inner.category }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        try await inner.fetch(nowUtc: nowUtc)
    }
}
