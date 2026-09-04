import AppIntents
import PitBoardKit

/// Configuración por instancia del widget de Clasificación — equivalente de
/// `StandingsWidgetPrefsRepository.kt` + `StandingsWidgetConfigActivity.kt`, pero por la vía
/// nativa de iOS (mismo patrón que `RaceWidgetConfigurationIntent`): el sistema genera solo
/// la UI de edición ("mantener pulsado → Editar widget"), sin pantalla propia.
///
/// `carClass` se enseña siempre, aunque solo se use quel realmente importa en las 4
/// categorías "por coche" (WEC/ELMS/IMSA/Le Mans Cup) — AppIntents no ofrece una forma
/// sencilla y fiable de mostrar un parámetro solo condicionalmente según el valor de otro
/// sin poder probarlo en un dispositivo real; se prefirió el diseño simple y robusto
/// (ver `StandingsWidgetProvider.loadEntry`, que ignora `carClass` fuera de esas 4
/// categorías y, si el valor elegido no es válido para la categoría, cae a su clase
/// principal — `StandingsCategory.primaryCarClass`).
struct StandingsWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Configurar Clasificación"
    static var description = IntentDescription("Elige la categoría y si quieres ver pilotos o equipos.")

    // Opcional (en vez de con `default:`) a propósito: evita depender de que
    // `StandingsCategoryEntity` cumpla los requisitos exactos que pida la sobrecarga de
    // `@Parameter` con valor por defecto para un `AppEntity` — sin poder compilar en este
    // entorno, el diseño más seguro es `nil` = "todavía no elegida" y resolverlo en
    // `StandingsWidgetProvider` cayendo a F1, mismo criterio que "vacío = todas" en
    // `RaceWidgetConfigurationIntent.effectiveSeries`.
    @Parameter(title: "Categoría")
    var category: StandingsCategoryEntity?

    @Parameter(title: "Pilotos o equipos", default: .drivers)
    var mode: StandingsModeOption

    @Parameter(title: "Clase (solo WEC/ELMS/IMSA/Le Mans Cup)", default: .lmp2)
    var carClass: StandingsClassOption

    @Parameter(title: "Filas a mostrar", default: .five)
    var rowCount: StandingsRowCountOption

    @Parameter(title: "Color de fondo (#RRGGBB)", default: "#131519")
    var backgroundColorHex: String

    @Parameter(title: "Apariencia", default: .dark)
    var appearance: AppearanceOption

    func perform() async throws -> some IntentResult { .result() }

    init() {}

    var rowLimit: Int { rowCount.limit }
}

/// Envuelve `StandingsCategory` como `AppEntity` para un selector nativo de una sola
/// categoría — equivalente de `RaceSeriesEntity`, pero de selección única (`var category:`
/// en vez de `var activeSeries: [...]`).
struct StandingsCategoryEntity: AppEntity, Identifiable {
    let id: String

    var category: StandingsCategory { StandingsCategory(rawValue: id) ?? .f1 }

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Categoría"
    static var defaultQuery = StandingsCategoryEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(category.displayName)")
    }

    init(id: String) { self.id = id }
    init(category: StandingsCategory) { self.id = category.rawValue }
}

struct StandingsCategoryEntityQuery: EntityQuery {
    func entities(for identifiers: [StandingsCategoryEntity.ID]) async throws -> [StandingsCategoryEntity] {
        identifiers.map(StandingsCategoryEntity.init(id:))
    }

    func suggestedEntities() async throws -> [StandingsCategoryEntity] {
        StandingsCategory.allCases.map(StandingsCategoryEntity.init(category:))
    }
}

/// Pilotos o equipos — ignorado en categorías "por coche" (siempre equipos ahí, ver
/// `StandingsWidgetProvider`).
enum StandingsModeOption: String, AppEnum {
    case drivers, teams

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Pilotos o equipos"
    static var caseDisplayRepresentations: [StandingsModeOption: DisplayRepresentation] = [
        .drivers: "Pilotos",
        .teams: "Equipos"
    ]

    var standingType: StandingType {
        switch self {
        case .drivers: .driver
        case .teams: .team
        }
    }
}

/// Las 10 clases reales (todo `StandingsClass` salvo `.overall`) en un único selector plano
/// — solo tiene efecto en WEC/ELMS/IMSA/Le Mans Cup, y solo si es una clase válida para la
/// categoría elegida (si no, se cae a la clase principal de esa categoría).
enum StandingsClassOption: String, AppEnum {
    case lmp2, lmp2ProAm, lmp3, lmp3ProAm, lmgt3, gt3, hypercar, gtp, gtdPro, gtd

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Clase"
    static var caseDisplayRepresentations: [StandingsClassOption: DisplayRepresentation] = [
        .lmp2: "LMP2",
        .lmp2ProAm: "LMP2 Pro/Am",
        .lmp3: "LMP3",
        .lmp3ProAm: "LMP3 Pro/Am",
        .lmgt3: "LMGT3 / GT3",
        .gt3: "GT3 (Le Mans Cup)",
        .hypercar: "Hypercar",
        .gtp: "GTP",
        .gtdPro: "GTD Pro",
        .gtd: "GTD"
    ]

    var standingsClass: StandingsClass {
        switch self {
        case .lmp2: .lmp2
        case .lmp2ProAm: .lmp2ProAm
        case .lmp3: .lmp3
        case .lmp3ProAm: .lmp3ProAm
        case .lmgt3: .lmgt3
        case .gt3: .gt3
        case .hypercar: .hypercar
        case .gtp: .gtp
        case .gtdPro: .gtdPro
        case .gtd: .gtd
        }
    }
}

/// Equivalente de las opciones "3 / 5 / 10 / Todos" de `RowCountRow` en
/// `StandingsWidgetConfigActivity.kt`.
enum StandingsRowCountOption: String, AppEnum {
    case three, five, ten, all

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Filas a mostrar"
    static var caseDisplayRepresentations: [StandingsRowCountOption: DisplayRepresentation] = [
        .three: "3",
        .five: "5",
        .ten: "10",
        .all: "Todos"
    ]

    var limit: Int {
        switch self {
        case .three: 3
        case .five: 5
        case .ten: 10
        case .all: 200
        }
    }
}
