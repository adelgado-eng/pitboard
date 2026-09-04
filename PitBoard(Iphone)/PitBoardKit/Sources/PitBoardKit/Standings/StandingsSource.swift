import Foundation

/// Contrato que implementan 14 de las 15 fuentes de clasificación (queda fuera solo
/// IMSA, ver `ImsaStandingsSource`) — equivalente exacto de `StandingsSource.kt`. El
/// repositorio no sabe ni le importa si detrás hay JSON o HTML, solo pide una lista de
/// filas ya normalizadas.
public protocol StandingsSource: Sendable {
    var category: StandingsCategory { get }

    /// `nowUtc` se pasa desde fuera (en vez de leerlo aquí) para que todas las filas de
    /// una misma sincronización queden marcadas con el mismo instante.
    func fetch(nowUtc: Date) async throws -> [StandingDraft]
}
