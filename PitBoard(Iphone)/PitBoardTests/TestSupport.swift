import Foundation
import SwiftData
import PitBoardKit

/// `ModelContainer` en memoria — igual que el homónimo de `PitBoardKitTests`, pero
/// duplicado aquí a propósito: son targets distintos (`PitBoardTests` no puede importar
/// los símbolos `internal` de test de `PitBoardKitTests`) y esta utilidad es lo bastante
/// pequeña para no merecer un tercer módulo compartido solo para esto.
func makeInMemoryContainer() -> ModelContainer {
    let schema = Schema([EventModel.self, SeriesConfigModel.self, StandingModel.self, CarDriverModel.self])
    let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
    return try! ModelContainer(for: schema, configurations: [configuration])
}
