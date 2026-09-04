import WidgetKit
import SwiftUI
import PitBoardKit

@main
struct PitBoardWidgetBundle: WidgetBundle {
    var body: some Widget {
        PitBoardWidget()
        StandingsWidget()
    }
}

/// Widget de próximos eventos — equivalente conjunto de `RaceWidget.kt` +
/// `RaceWidgetReceiverSmall/Medium/Large.kt`. Android necesita 3 receivers (uno por
/// tamaño, para que aparezcan como 3 opciones separadas en el selector de widgets de
/// Samsung); en iOS un único `Widget` con `supportedFamilies` basta — el sistema ya
/// ofrece los 3 tamaños como variantes del mismo widget en la galería.
struct PitBoardWidget: Widget {
    let kind: String = "PitBoardWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: RaceWidgetConfigurationIntent.self,
            provider: RaceWidgetProvider()
        ) { entry in
            RaceWidgetView(entry: entry)
        }
        .configurationDisplayName("PitBoard")
        .description("Próximos eventos de tus series de motorsport favoritas.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

/// Widget de Clasificación — un único tipo, configurable por instancia (categoría +
/// Pilotos/Equipos, o clase de coche en WEC/ELMS/IMSA/Le Mans Cup): varias copias del mismo
/// widget, cada una apuntando a una categoría distinta, en vez de un tipo de widget por cada
/// una de las 15 categorías.
struct StandingsWidget: Widget {
    let kind: String = "StandingsWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: StandingsWidgetConfigurationIntent.self,
            provider: StandingsWidgetProvider()
        ) { entry in
            StandingsWidgetView(entry: entry)
        }
        .configurationDisplayName("PitBoard — Clasificación")
        .description("Clasificación de pilotos o equipos de tu categoría favorita.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}
