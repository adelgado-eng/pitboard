import Foundation

/// Contrato que implementan las fuentes de calendario, una por serie — equivalente
/// exacto de `RaceScheduleSource.kt`. Mismo patrón que `StandingsSource` para las
/// clasificaciones: el repositorio no sabe ni le importa si detrás hay JSON o HTML, solo
/// pide la lista de sesiones ya normalizadas.
public protocol RaceScheduleSource: Sendable {
    var series: RaceSeries { get }

    func fetch() async throws -> [EventDraft]
}
