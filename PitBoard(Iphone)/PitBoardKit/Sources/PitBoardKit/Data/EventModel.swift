import Foundation
import SwiftData

/// Una sesión concreta de un fin de semana de carreras (ej. "Formula 1 - GP de Italia -
/// Monza - Carrera"), obtenida automáticamente por una fuente en `Schedule/Sources` —
/// equivalente exacto de `EventEntity.kt` (Room).
///
/// A diferencia de Android no se declara un índice único (series, uid) a nivel de
/// esquema: `RaceScheduleRepository.replaceSeries` borra e inserta TODA la serie en cada
/// sincronización (igual que `EventDao.replaceSeries` en Kotlin), así que la unicidad ya
/// la garantiza ese flujo, no hace falta que SwiftData la imponga también.
@Model
public final class EventModel {
    public var series: RaceSeries

    /// Id estable dentro de la serie (ej. "F1-2026-R05-RACE"), lo construye cada fuente.
    public var uid: String

    /// "Serie - Nombre de la ronda - Circuito - Sesión" — mismo formato que Android para
    /// que el resto de la UI (agrupador de fin de semana, widget...) no necesite ningún
    /// tratamiento especial.
    public var fullTitle: String

    public var startTimeUtc: Date

    /// Zona horaria del circuito (ej. "Europe/Rome"). nil si la fuente no la da (ver
    /// WikipediaSeasonCalendarSource, que solo tiene fecha sin hora).
    public var timeZoneId: String?

    /// Rótulo bruto (rawValue de SessionBadgeType: "C"/"Q"/"S"/"L"/"") — cada fuente lo
    /// asigna directamente a partir del tipo de sesión que scrapea.
    public var inferredBadge: String

    public init(
        series: RaceSeries,
        uid: String,
        fullTitle: String,
        startTimeUtc: Date,
        timeZoneId: String? = nil,
        inferredBadge: String
    ) {
        self.series = series
        self.uid = uid
        self.fullTitle = fullTitle
        self.startTimeUtc = startTimeUtc
        self.timeZoneId = timeZoneId
        self.inferredBadge = inferredBadge
    }

    public convenience init(draft: EventDraft) {
        self.init(
            series: draft.series,
            uid: draft.uid,
            fullTitle: draft.fullTitle,
            startTimeUtc: draft.startTimeUtc,
            timeZoneId: draft.timeZoneId,
            inferredBadge: draft.inferredBadge
        )
    }

    public var badge: SessionBadgeType? { SessionBadgeType(rawValue: inferredBadge) }
}

/// Valor plano y `Sendable` que devuelven las fuentes de `Schedule/Sources`. Las fuentes
/// corren en tareas de fondo concurrentes (`TaskGroup`); un `@Model` de SwiftData está
/// atado a un `ModelContext`/actor concreto y no es seguro pasarlo entre tareas, así que
/// las fuentes construyen este DTO y es `RaceScheduleRepository` quien lo convierte a
/// `EventModel` ya dentro del `ModelContext` correcto al guardar.
public struct EventDraft: Sendable, Hashable {
    public var series: RaceSeries
    public var uid: String
    public var fullTitle: String
    public var startTimeUtc: Date
    public var timeZoneId: String?
    public var inferredBadge: String

    public init(
        series: RaceSeries,
        uid: String,
        fullTitle: String,
        startTimeUtc: Date,
        timeZoneId: String? = nil,
        inferredBadge: String
    ) {
        self.series = series
        self.uid = uid
        self.fullTitle = fullTitle
        self.startTimeUtc = startTimeUtc
        self.timeZoneId = timeZoneId
        self.inferredBadge = inferredBadge
    }
}
