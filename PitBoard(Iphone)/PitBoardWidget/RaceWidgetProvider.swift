import WidgetKit
import SwiftData
import UIKit
import PitBoardKit

/// Datos ya resueltos para pintar una entry del widget — equivalente del `WidgetReadyState`
/// privado de `RaceWidget.kt`. Usa `EventDraft`/`SeriesTagColor` (structs `Sendable`) en vez
/// de `EventModel`/`SeriesConfigModel` (`@Model`, atados a un `ModelContext`) porque una
/// `TimelineEntry` puede sobrevivir más allá del `ModelContext` que la construyó.
struct RaceWidgetEntry: TimelineEntry {
    let date: Date
    let weekendLabel: String
    let weekendEvents: [EventDraft]
    let laterEvents: [EventDraft]
    let seriesTagColors: [RaceSeries: SeriesTagColor]
    let configuration: RaceWidgetConfigurationIntent
    let useDark: Bool

    var allEvents: [EventDraft] { weekendEvents + laterEvents }
}

struct RaceWidgetProvider: AppIntentTimelineProvider {

    func placeholder(in context: Context) -> RaceWidgetEntry {
        RaceWidgetEntry(
            date: Date(),
            weekendLabel: "ESTE FIN DE SEMANA",
            weekendEvents: [
                EventDraft(series: .f1, uid: "placeholder", fullTitle: "Formula 1 - GP de Ejemplo - Carrera", startTimeUtc: Date().addingTimeInterval(3600), inferredBadge: SessionBadgeType.race.rawValue)
            ],
            laterEvents: [],
            seriesTagColors: [.f1: SeriesTagColor(tag: "F1", colorHex: RaceSeries.f1.defaultColorHex)],
            configuration: RaceWidgetConfigurationIntent(),
            useDark: true
        )
    }

    func snapshot(for configuration: RaceWidgetConfigurationIntent, in context: Context) async -> RaceWidgetEntry {
        await loadEntry(configuration: configuration)
    }

    func timeline(for configuration: RaceWidgetConfigurationIntent, in context: Context) async -> Timeline<RaceWidgetEntry> {
        let entry = await loadEntry(configuration: configuration)
        // No hay equivalente exacto al ciclo de 30 min de SyncWorker (WidgetCenter se
        // recarga sola desde BackgroundSyncManager tras cada sync) — este "after" es solo
        // el tope máximo antes de que WidgetKit vuelva a pedir una entry por su cuenta,
        // para que un widget que llevara mucho sin recargarse no se quede con "D-3" fijo
        // para siempre si, por lo que sea, ninguna sincronización en segundo plano llegó
        // a correr.
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        return Timeline(entries: [entry], policy: .after(nextRefresh))
    }

    private func loadEntry(configuration: RaceWidgetConfigurationIntent) async -> RaceWidgetEntry {
        let context = AppDatabase.newContext()

        let seriesTagColors: [RaceSeries: SeriesTagColor] = (try? context.fetch(FetchDescriptor<SeriesConfigModel>()))
            .map { configs in
                configs.reduce(into: [RaceSeries: SeriesTagColor]()) { result, config in
                    result[config.series] = SeriesTagColor(tag: config.tag, colorHex: config.colorHex)
                }
            } ?? [:]

        let activeSeries = configuration.effectiveSeries
        let now = Date()
        let endOfYear = SeasonWindow.endOfCurrentYearUtc(nowUtc: now)

        let descriptor = FetchDescriptor<EventModel>(
            predicate: #Predicate<EventModel> { $0.startTimeUtc >= now && $0.startTimeUtc <= endOfYear },
            sortBy: [SortDescriptor(\.startTimeUtc)]
        )
        let upcoming = ((try? context.fetch(descriptor)) ?? []).filter { activeSeries.contains($0.series) }

        // Deduplicado por título completo + hora exacta, igual que RaceScheduleRepository —
        // defensivo por si dos fuentes distintas guardaron la misma sesión con uid distinto.
        var seen = Set<String>()
        var deduped: [EventModel] = []
        for event in upcoming {
            let key = "\(event.fullTitle)|\(event.startTimeUtc.timeIntervalSince1970)"
            if seen.insert(key).inserted { deduped.append(event) }
        }

        let limit = configuration.eventLimit
        let limited = limit >= WidgetPrefsConstants.noLimit ? deduped : Array(deduped.prefix(limit))
        let drafts = limited.map {
            EventDraft(series: $0.series, uid: $0.uid, fullTitle: $0.fullTitle, startTimeUtc: $0.startTimeUtc, timeZoneId: $0.timeZoneId, inferredBadge: $0.inferredBadge)
        }
        let groups = Self.splitByWeekend(drafts)

        let useDark: Bool
        switch configuration.appearance.appTheme {
        case .light: useDark = false
        case .dark: useDark = true
        case .system: useDark = UITraitCollection.current.userInterfaceStyle == .dark
        }

        return RaceWidgetEntry(
            date: now,
            weekendLabel: groups.weekendLabel,
            weekendEvents: groups.weekendEvents,
            laterEvents: groups.laterEvents,
            seriesTagColors: seriesTagColors,
            configuration: configuration,
            useDark: useDark
        )
    }

    /// Mismo algoritmo que `EventWeekendGrouper.split` (que trabaja sobre `[EventModel]`,
    /// un `@Model` de SwiftData) pero reimplementado directamente sobre `[EventDraft]` —
    /// evita tener que emparejar de vuelta por `uid`, que solo es estable DENTRO de una
    /// serie (dos series distintas podrían compartir el mismo uid por coincidencia; ver
    /// `EventEntity.kt`), así que no sirve como clave global de unión aquí.
    private static func splitByWeekend(_ events: [EventDraft], zone: TimeZone = .current) -> (weekendLabel: String, weekendEvents: [EventDraft], laterEvents: [EventDraft]) {
        guard let first = events.first else { return ("", [], []) }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone

        let firstDate = calendar.startOfDay(for: first.startTimeUtc)
        let firstWeekday = calendar.component(.weekday, from: firstDate)
        let daysBackToFriday = (firstWeekday - 6 + 7) % 7
        let daysForwardToSunday = (1 - firstWeekday + 7) % 7

        guard
            let friday = calendar.date(byAdding: .day, value: -daysBackToFriday, to: firstDate),
            let sunday = calendar.date(byAdding: .day, value: daysForwardToSunday, to: firstDate),
            let sundayEnd = calendar.date(byAdding: .day, value: 1, to: sunday).map({ $0.addingTimeInterval(-1) })
        else {
            return ("", [], [])
        }

        let today = calendar.startOfDay(for: Date())
        let label: String
        if firstDate == today {
            label = "Hoy"
        } else if today >= friday && today <= sunday {
            label = "Este fin de semana"
        } else {
            let weekday = calendar.component(.weekday, from: today)
            let daysForward = (6 - weekday + 7) % 7
            let offset = daysForward == 0 ? 7 : daysForward
            let strictNextFriday = calendar.date(byAdding: .day, value: offset, to: today) ?? today
            label = friday == strictNextFriday ? "Próximo fin de semana" : "Próxima cita"
        }

        let weekend = events.filter { $0.startTimeUtc >= friday && $0.startTimeUtc <= sundayEnd }
        let later = events.filter { $0.startTimeUtc > sundayEnd }
        return (label, weekend, later)
    }
}
