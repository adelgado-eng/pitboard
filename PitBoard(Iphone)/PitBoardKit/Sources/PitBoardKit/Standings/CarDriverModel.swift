import Foundation
import SwiftData

/// Un piloto de un coche de resistencia (ELMS, IMSA, WEC, Le Mans Cup), para el
/// desplegable que sale al tocar un coche en Clasificaciones — equivalente exacto de
/// `CarDriverEntity.kt`.
@Model
public final class CarDriverModel {
    public var category: StandingsCategory
    public var standingsClass: StandingsClass

    /// Sin "#", mismo formato que el número de coche en `StandingModel.name` para las
    /// fuentes "de coches" (Elms/Imsa/Wec/LeMansCup).
    public var carNumber: String

    /// Nombre normalizado del piloto — solo distingue filas dentro del mismo coche.
    public var entryKey: String

    public var name: String
    public var photoUrl: String?
    public var updatedAtUtc: Date

    public init(
        category: StandingsCategory,
        standingsClass: StandingsClass,
        carNumber: String,
        entryKey: String,
        name: String,
        photoUrl: String? = nil,
        updatedAtUtc: Date
    ) {
        self.category = category
        self.standingsClass = standingsClass
        self.carNumber = carNumber
        self.entryKey = entryKey
        self.name = name
        self.photoUrl = photoUrl
        self.updatedAtUtc = updatedAtUtc
    }

    public convenience init(draft: CarDriverDraft) {
        self.init(
            category: draft.category,
            standingsClass: draft.standingsClass,
            carNumber: draft.carNumber,
            entryKey: draft.entryKey,
            name: draft.name,
            photoUrl: draft.photoUrl,
            updatedAtUtc: draft.updatedAtUtc
        )
    }
}

public struct CarDriverDraft: Sendable, Hashable {
    public var category: StandingsCategory
    public var standingsClass: StandingsClass
    public var carNumber: String
    public var entryKey: String
    public var name: String
    public var photoUrl: String?
    public var updatedAtUtc: Date

    public init(
        category: StandingsCategory,
        standingsClass: StandingsClass,
        carNumber: String,
        entryKey: String,
        name: String,
        photoUrl: String? = nil,
        updatedAtUtc: Date
    ) {
        self.category = category
        self.standingsClass = standingsClass
        self.carNumber = carNumber
        self.entryKey = entryKey
        self.name = name
        self.photoUrl = photoUrl
        self.updatedAtUtc = updatedAtUtc
    }
}
