import WidgetKit
import SwiftData
import UIKit
import PitBoardKit

/// Datos ya resueltos para pintar una entry del widget de Clasificación — equivalente de
/// `RaceWidgetEntry`, pero para clasificaciones. Usa `StandingDraft` (struct `Sendable`, ya
/// existente para las fuentes de scraping) en vez de `StandingModel` (`@Model`) por el mismo
/// motivo que `RaceWidgetEntry` usa `EventDraft`: una `TimelineEntry` puede sobrevivir más
/// allá del `ModelContext` que la construyó.
struct StandingsWidgetEntry: TimelineEntry {
    let date: Date
    let category: StandingsCategory
    let rows: [StandingDraft]
    let useDark: Bool
    let backgroundColorHex: String
    /// Mismo idioma que el resto de la app — ver `RaceWidgetEntry.appLanguage`.
    let appLanguage: AppLanguage
}

struct StandingsWidgetProvider: AppIntentTimelineProvider {

    func placeholder(in context: Context) -> StandingsWidgetEntry {
        StandingsWidgetEntry(
            date: Date(),
            category: .f1,
            rows: [
                StandingDraft(category: .f1, type: .driver, entrantKey: "placeholder", position: 1, name: "Piloto de Ejemplo", team: "Equipo Ejemplo", points: 250, updatedAtUtc: Date())
            ],
            useDark: true,
            backgroundColorHex: "#131519",
            appLanguage: .spanish
        )
    }

    func snapshot(for configuration: StandingsWidgetConfigurationIntent, in context: Context) async -> StandingsWidgetEntry {
        await loadEntry(configuration: configuration)
    }

    func timeline(for configuration: StandingsWidgetConfigurationIntent, in context: Context) async -> Timeline<StandingsWidgetEntry> {
        let entry = await loadEntry(configuration: configuration)
        // Las clasificaciones se sincronizan una vez por semana (lunes 12:00) — un tope de
        // 6 h es de sobra para que el widget recoja esa actualización sin esperar a que
        // WidgetKit decida refrescar por su cuenta.
        let nextRefresh = Calendar.current.date(byAdding: .hour, value: 6, to: Date()) ?? Date().addingTimeInterval(21600)
        return Timeline(entries: [entry], policy: .after(nextRefresh))
    }

    private func loadEntry(configuration: StandingsWidgetConfigurationIntent) async -> StandingsWidgetEntry {
        let context = AppDatabase.newContext()
        // nil = todavía no se ha tocado el selector — cae a F1 (mismo criterio que
        // "vacío = todas" en RaceWidgetConfigurationIntent.effectiveSeries).
        let category = configuration.category?.category ?? .f1

        // Categoría "por coche" (WEC/ELMS/IMSA/Le Mans Cup): siempre equipos, en la clase
        // elegida SI es válida para esta categoría — si no (o si el usuario no la ha
        // tocado), cae a la clase principal de la categoría.
        let effectiveClass: StandingsClass
        let effectiveType: StandingType
        if let primaryClass = category.primaryCarClass {
            let validClasses = Set((CarBasedStandingsClasses.carBasedClasses[category] ?? []).map(\.0))
            let chosen = configuration.carClass.standingsClass
            effectiveClass = validClasses.contains(chosen) ? chosen : primaryClass
            effectiveType = .team
        } else {
            effectiveClass = .overall
            effectiveType = configuration.mode.standingType
        }

        let descriptor = FetchDescriptor<StandingModel>(
            predicate: #Predicate<StandingModel> {
                $0.category == category && $0.standingsClass == effectiveClass && $0.type == effectiveType
            },
            sortBy: [SortDescriptor(\.position)]
        )
        let fetched = (try? context.fetch(descriptor)) ?? []
        let rows = fetched.prefix(configuration.rowLimit).map {
            StandingDraft(
                category: $0.category,
                standingsClass: $0.standingsClass,
                type: $0.type,
                entrantKey: $0.entrantKey,
                position: $0.position,
                name: $0.name,
                team: $0.team,
                points: $0.points,
                updatedAtUtc: $0.updatedAtUtc
            )
        }

        let useDark: Bool
        switch configuration.appearance.appTheme {
        case .light: useDark = false
        case .dark: useDark = true
        case .system: useDark = UITraitCollection.current.userInterfaceStyle == .dark
        }

        let appLanguage = AppSettingsRepository().appLanguage ?? .spanish

        return StandingsWidgetEntry(
            date: Date(),
            category: category,
            rows: Array(rows),
            useDark: useDark,
            backgroundColorHex: configuration.backgroundColorHex,
            appLanguage: appLanguage
        )
    }
}
