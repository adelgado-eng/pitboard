import AppIntents
import PitBoardKit

/// Configuración por instancia del widget — equivalente de `WidgetPrefsRepository.kt` +
/// `RaceWidgetConfigActivity.kt`, pero adaptado a la vía NATIVA de iOS 17: un
/// `WidgetConfigurationIntent` con `@Parameter`s genera solo la UI de edición del sistema
/// (mantener pulsado el widget → "Editar widget") — no hace falta programar ninguna
/// pantalla propia (Android sí la necesitaba: Glance no tiene configuración nativa por
/// widget). El sistema persiste los valores por instancia automáticamente.
struct RaceWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Configurar PitBoard"
    static var description = IntentDescription("Elige qué series y cómo se muestran en el widget.")

    /// Vacío = todas las series — mismo convenio que `effectiveSeries()` en Android.
    @Parameter(title: "Series (vacío = todas)")
    var activeSeries: [RaceSeriesEntity]

    @Parameter(title: "Eventos a mostrar", default: .ten)
    var eventCount: EventCountOption

    @Parameter(title: "Palabras del título", default: 4)
    var wordCount: Int

    @Parameter(title: "Color de fondo (#RRGGBB)", default: "#131519")
    var backgroundColorHex: String

    @Parameter(title: "Mostrar hora del circuito", default: true)
    var showTrackTime: Bool

    @Parameter(title: "Apariencia", default: .dark)
    var appearance: AppearanceOption

    /// `AppIntent` exige `perform()` incluso para un intent que solo existe para
    /// configurar el widget (nunca se "ejecuta" como una Shortcut/Siri de verdad) — el
    /// propio `AppIntentConfiguration` es quien lee los `@Parameter` directamente para
    /// construir la timeline, así que aquí no hay nada que hacer.
    func perform() async throws -> some IntentResult {
        .result()
    }

    init() {}

    /// Series realmente activas — vacío (widget recién colocado) significa "todas".
    var effectiveSeries: Set<RaceSeries> {
        activeSeries.isEmpty ? Set(RaceSeries.allCases) : Set(activeSeries.map(\.series))
    }

    var eventLimit: Int { eventCount.limit }

    var clampedWordCount: Int { min(max(wordCount, 1), 8) }
}

/// Envuelve `RaceSeries` como `AppEntity` para que `@Parameter var activeSeries:
/// [RaceSeriesEntity]` dé un selector múltiple nativo en la UI de configuración del sistema.
struct RaceSeriesEntity: AppEntity, Identifiable {
    let id: String

    var series: RaceSeries { RaceSeries(rawValue: id) ?? .f1 }

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Serie"
    static var defaultQuery = RaceSeriesEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(series.displayName)")
    }

    init(id: String) { self.id = id }
    init(series: RaceSeries) { self.id = series.rawValue }
}

struct RaceSeriesEntityQuery: EntityQuery {
    func entities(for identifiers: [RaceSeriesEntity.ID]) async throws -> [RaceSeriesEntity] {
        identifiers.map(RaceSeriesEntity.init(id:))
    }

    func suggestedEntities() async throws -> [RaceSeriesEntity] {
        RaceSeries.allCases.map(RaceSeriesEntity.init(series:))
    }
}

/// Equivalente de las opciones "5 / 10 / 20 / 50 / Todos" de `EventCountRow` en Android.
enum EventCountOption: String, AppEnum {
    case five, ten, twenty, fifty, all

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Cantidad de eventos"
    static var caseDisplayRepresentations: [EventCountOption: DisplayRepresentation] = [
        .five: "5",
        .ten: "10",
        .twenty: "20",
        .fifty: "50",
        .all: "Todos"
    ]

    var limit: Int {
        switch self {
        case .five: 5
        case .ten: 10
        case .twenty: 20
        case .fifty: 50
        case .all: WidgetPrefsConstants.noLimit
        }
    }
}

/// Equivalente de `AppTheme` aplicado al widget (independiente del tema de la app —
/// mismo criterio que `WidgetConfig.appearance` en Android).
enum AppearanceOption: String, AppEnum {
    case light, dark, system

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Apariencia"
    static var caseDisplayRepresentations: [AppearanceOption: DisplayRepresentation] = [
        .light: "☀️ Claro",
        .dark: "🌙 Oscuro",
        .system: "📱 Auto"
    ]

    var appTheme: AppTheme {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: .system
        }
    }
}
