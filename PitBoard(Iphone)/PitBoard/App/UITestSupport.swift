import Foundation
import SwiftData
import PitBoardKit

/// Puente entre `PitBoardUITests` (proceso separado, sin acceso directo al estado de la
/// app) y esta — se activa solo con el argumento de lanzamiento "-uiTesting" que los
/// tests añaden vía `XCUIApplication().launchArguments`. En ese modo, `RootTabView` se
/// salta la sincronización de arranque por red (nada de eso es determinista ni rápido
/// para un test) y usa datos fijos en su lugar.
enum UITestSupport {
    static let launchArgument = "-uiTesting"

    /// `arguments:` es inyectable (por defecto los reales del proceso) para poder
    /// testear las dos ramas desde `PitBoardTests` sin depender de cómo se lanzó el test
    /// runner en sí.
    static func isUITesting(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        arguments.contains(launchArgument)
    }
}

/// Datos deterministas para que los tests tengan contenido real con el que interactuar
/// (una lista de Eventos no vacía, una clasificación con un líder) sin depender de red.
enum UITestFixtures {
    /// `container:` es inyectable (por defecto el store real, compartido vía App Group)
    /// para poder testear el sembrado contra un `ModelContainer` en memoria — devuelve el
    /// `ModelContext` usado para que el test pueda seguir consultando sobre él.
    @discardableResult
    static func seedIfNeeded(in container: ModelContainer = AppDatabase.container) async -> ModelContext {
        let context = ModelContext(container)

        // Limpia cualquier resto de una ejecución de test anterior en el mismo simulador
        // — cada test debe partir del mismo estado, no acumular filas de sesiones previas.
        for model in (try? context.fetch(FetchDescriptor<EventModel>())) ?? [] { context.delete(model) }
        for model in (try? context.fetch(FetchDescriptor<StandingModel>())) ?? [] { context.delete(model) }
        for model in (try? context.fetch(FetchDescriptor<CarDriverModel>())) ?? [] { context.delete(model) }
        for model in (try? context.fetch(FetchDescriptor<SeriesConfigModel>())) ?? [] { context.delete(model) }

        for config in makeDefaultSeriesConfigs() { context.insert(config) }

        let now = Date()
        context.insert(EventModel(
            series: .f1,
            uid: "UITEST-F1-RACE",
            fullTitle: "Formula 1 - GP de Prueba - Circuito de Test - Carrera",
            startTimeUtc: now.addingTimeInterval(3600),
            timeZoneId: "Europe/Madrid",
            inferredBadge: SessionBadgeType.race.rawValue
        ))
        context.insert(EventModel(
            series: .motoGp,
            uid: "UITEST-MGP-QUALY",
            fullTitle: "MotoGP - GP de Prueba - Circuito de Test - Clasificación",
            startTimeUtc: now.addingTimeInterval(7 * 24 * 3600),
            timeZoneId: nil,
            inferredBadge: SessionBadgeType.qualy.rawValue
        ))

        context.insert(StandingModel(
            category: .f1,
            type: .driver,
            entrantKey: "uitest-driver-1",
            position: 1,
            name: "Piloto de Prueba",
            team: "Equipo de Prueba",
            points: 250,
            updatedAtUtc: now
        ))
        context.insert(StandingModel(
            category: .f1,
            type: .driver,
            entrantKey: "uitest-driver-2",
            position: 2,
            name: "Segundo Piloto",
            team: "Otro Equipo",
            points: 210,
            updatedAtUtc: now
        ))

        try? context.save()
        return context
    }
}
