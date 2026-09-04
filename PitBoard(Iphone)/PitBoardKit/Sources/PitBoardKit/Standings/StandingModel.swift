import Foundation
import SwiftData

/// Una fila de una clasificación: piloto o equipo, dentro de una categoría (y, para las
/// de resistencia, dentro de una clase concreta) — equivalente exacto de
/// `StandingEntity.kt`. Sin índice único a nivel de esquema por el mismo motivo que
/// `EventModel`: `StandingsRepository.replaceCategory` sustituye toda la categoría en
/// cada sincronización.
@Model
public final class StandingModel {
    public var category: StandingsCategory
    public var standingsClass: StandingsClass
    public var type: StandingType

    /// Identifica la fila de forma estable entre actualizaciones.
    public var entrantKey: String

    public var position: Int

    /// Nombre del piloto, o de los pilotos separados por " / " si es un equipo de resistencia.
    public var name: String

    /// Equipo/escudería (vacío si `type` es `.team`).
    public var team: String

    public var points: Double
    public var photoUrl: String?
    public var updatedAtUtc: Date

    public init(
        category: StandingsCategory,
        standingsClass: StandingsClass,
        type: StandingType,
        entrantKey: String,
        position: Int,
        name: String,
        team: String,
        points: Double,
        photoUrl: String? = nil,
        updatedAtUtc: Date
    ) {
        self.category = category
        self.standingsClass = standingsClass
        self.type = type
        self.entrantKey = entrantKey
        self.position = position
        self.name = name
        self.team = team
        self.points = points
        self.photoUrl = photoUrl
        self.updatedAtUtc = updatedAtUtc
    }

    public convenience init(draft: StandingDraft) {
        self.init(
            category: draft.category,
            standingsClass: draft.standingsClass,
            type: draft.type,
            entrantKey: draft.entrantKey,
            position: draft.position,
            name: draft.name,
            team: draft.team,
            points: draft.points,
            photoUrl: draft.photoUrl,
            updatedAtUtc: draft.updatedAtUtc
        )
    }
}

/// DTO `Sendable` que devuelven las fuentes de `Standings/Sources` — mismo motivo que
/// `EventDraft`: un `@Model` no cruza fronteras de concurrencia de forma segura.
public struct StandingDraft: Sendable, Hashable {
    public var category: StandingsCategory
    public var standingsClass: StandingsClass
    public var type: StandingType
    public var entrantKey: String
    public var position: Int
    public var name: String
    public var team: String
    public var points: Double
    public var photoUrl: String?
    public var updatedAtUtc: Date

    public init(
        category: StandingsCategory,
        standingsClass: StandingsClass = .overall,
        type: StandingType,
        entrantKey: String,
        position: Int,
        name: String,
        team: String,
        points: Double,
        photoUrl: String? = nil,
        updatedAtUtc: Date
    ) {
        self.category = category
        self.standingsClass = standingsClass
        self.type = type
        self.entrantKey = entrantKey
        self.position = position
        self.name = name
        self.team = team
        self.points = points
        self.photoUrl = photoUrl
        self.updatedAtUtc = updatedAtUtc
    }
}
