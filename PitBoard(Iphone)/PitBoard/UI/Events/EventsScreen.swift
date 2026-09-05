import SwiftUI
import SwiftData
import PitBoardKit

/// Pantalla de Eventos — equivalente de `EventsScreen.kt` (`EventsViewModel` incluido).
/// Orquesta el panel de filtro (`EventsFilterPanel`), la lista (`EventsList`) y los dos
/// diálogos (`SeriesConfigSheet`/`EventDetailsSheet`), cada uno ya en su propio archivo.
///
/// 05/09/2026 (Fase 4 del diagnóstico): este archivo concentraba navegación + filtros +
/// lista + diálogos en un único sitio de 620 líneas — coincidía con la comunidad de menor
/// cohesión detectada por graphify. Se hace DESPUÉS de la Fase 1 (red de tests de los
/// parsers) a propósito, por ser el refactor de más riesgo del plan: se apoya en que
/// Android e iOS ya compilan y pasan sus tests reales antes de tocarlo. Pura extracción de
/// código verbatim a otros archivos — ningún comportamiento cambia, cada vista extraída
/// recibe exactamente los mismos datos con los que ya se construía aquí mismo.
///
/// Arquitectura: sin `ObservableObject`/`StateFlow` — `@Query` sustituye tanto al DAO
/// como al `Flow` de Room (SwiftData recompone la vista sola cuando cambian los datos).
/// El filtrado por fecha "próximos" y por series activas se hace en memoria DESPUÉS del
/// fetch (no en el predicado de `@Query`) — mismo criterio que ya usaba Android para
/// `selectedSessionTypes` (dataset de una temporada, filtrar en memoria es barato),
/// extendido aquí porque SwiftData no admite predicados con `Date()` cambiante sin
/// reconstruir la query.
struct EventsScreen: View {
    @Query(sort: \EventModel.startTimeUtc) private var allEvents: [EventModel]
    @Query private var seriesConfigs: [SeriesConfigModel]

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.syncManager) private var syncManager
    @Environment(\.modelContext) private var modelContext
    @Environment(\.pitBoardColors) private var colors

    @State private var syncing = false
    @State private var showSeriesEditor = false
    @State private var detailsEvent: EventModel?
    @State private var showFilterPanel = false
    @State private var searchQuery = ""
    // Se comprueba una vez al entrar, no en vivo — igual que en Android (`remember`): si la
    // conexión cambia mientras la pantalla está abierta, se refleja en el siguiente
    // "Actualizar" o la próxima vez que se abra la pantalla.
    @State private var isOnline = ConnectivityMonitor.shared.isOnline

    private var selectedSeries: Set<RaceSeries> { settings.eventScreenActiveSeries }
    private var selectedSessionTypes: Set<String> { settings.eventScreenActiveSessionTypes }

    private var seriesConfigByKey: [RaceSeries: SeriesConfigModel] {
        Dictionary(uniqueKeysWithValues: seriesConfigs.map { ($0.series, $0) })
    }

    private var groups: EventWeekendGroups {
        let now = Date()
        let endOfYear = SeasonWindow.endOfCurrentYearUtc(nowUtc: now)
        let upcoming = allEvents.filter { event in
            guard event.startTimeUtc >= now, event.startTimeUtc <= endOfYear else { return false }
            return selectedSeries.isEmpty || selectedSeries.contains(event.series)
        }
        return EventWeekendGrouper.split(upcoming)
    }

    private func matchesSearch(_ event: EventModel) -> Bool {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let matchesQuery = query.isEmpty
            || event.fullTitle.localizedCaseInsensitiveContains(query)
            || event.series.displayName.localizedCaseInsensitiveContains(query)
        let matchesSessionType = selectedSessionTypes.isEmpty || selectedSessionTypes.contains(event.inferredBadge)
        return matchesQuery && matchesSessionType
    }

    var body: some View {
        let allWeekendEvents = groups.weekendEvents
        let allLaterEvents = groups.laterEvents
        let noEventsAtAll = allWeekendEvents.isEmpty && allLaterEvents.isEmpty

        let weekendEvents = allWeekendEvents.filter(matchesSearch)
        let laterEvents = allLaterEvents.filter(matchesSearch)
        let nothingMatchesQuickFilters = !noEventsAtAll && weekendEvents.isEmpty && laterEvents.isEmpty
        let quickFiltersActive = !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || !selectedSeries.isEmpty || !selectedSessionTypes.isEmpty

        VStack(spacing: 0) {
            if showFilterPanel {
                EventsFilterPanel(
                    searchQuery: $searchQuery,
                    selectedSeries: selectedSeries,
                    selectedSessionTypes: selectedSessionTypes,
                    seriesConfigByKey: seriesConfigByKey,
                    onSeriesChange: { settings.setEventScreenActiveSeries($0) },
                    onSessionTypesChange: { settings.setEventScreenActiveSessionTypes($0) }
                )
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            Group {
                if noEventsAtAll && !selectedSeries.isEmpty {
                    // Antes que "sin conexión": con un filtro de por medio, "no hay eventos
                    // guardados para estas series" es el diagnóstico correcto haya o no
                    // internet — puede haber eventos guardados de OTRAS series.
                    EmptyStateView(
                        systemImage: "magnifyingglass",
                        title: settings.t("events_empty_filtered_title"),
                        message: settings.t("events_empty_filtered_message")
                    ) {
                        HStack(spacing: 8) {
                            Button(settings.t("events_remove_filter")) { settings.setEventScreenActiveSeries([]) }
                                .buttonStyle(.borderedProminent)
                            Button(settings.t("events_refresh")) { refreshNow() }
                                .buttonStyle(.bordered)
                        }
                    }
                } else if noEventsAtAll && !isOnline {
                    EmptyStateView(
                        systemImage: "wifi.slash",
                        title: settings.t("events_empty_offline_title"),
                        message: settings.t("events_empty_offline_message")
                    )
                } else if noEventsAtAll {
                    EmptyStateView(
                        systemImage: "calendar",
                        title: settings.t("events_empty_title"),
                        message: settings.t("events_empty_message")
                    ) {
                        Button(settings.t("events_refresh")) { refreshNow() }
                            .buttonStyle(.borderedProminent)
                    }
                } else if nothingMatchesQuickFilters {
                    EmptyStateView(
                        systemImage: "magnifyingglass",
                        title: settings.t("events_empty_no_match_title"),
                        message: settings.t("events_empty_no_match_message")
                    ) {
                        if quickFiltersActive {
                            Button(settings.t("events_clear_search_and_filters")) {
                                searchQuery = ""
                                settings.setEventScreenActiveSeries([])
                                settings.setEventScreenActiveSessionTypes([])
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                } else {
                    EventsList(
                        isOnline: isOnline,
                        weekendEvents: weekendEvents,
                        weekendLabelKey: groups.weekendLabelKey,
                        laterEvents: laterEvents,
                        seriesConfigByKey: seriesConfigByKey,
                        timeDisplayMode: settings.timeDisplayMode,
                        onEventTap: { detailsEvent = $0 }
                    )
                }
            }
        }
        .navigationTitle(settings.t("events_title"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if syncing {
                    ProgressView().frame(width: 24, height: 24)
                        .accessibilityIdentifier("events.syncing")
                } else {
                    Button {
                        refreshNow()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityIdentifier("events.refresh")
                    .accessibilityLabel(settings.t("cd_refresh"))
                }
                Button {
                    withAnimation { showFilterPanel.toggle() }
                } label: {
                    Image(systemName: quickFiltersActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                        .foregroundStyle(quickFiltersActive ? colors.primary : .primary)
                }
                .accessibilityIdentifier("events.filter")
                .accessibilityLabel(settings.t("cd_filter_events"))
                .accessibilityAddTraits(quickFiltersActive ? .isSelected : [])
                Button {
                    showSeriesEditor = true
                } label: {
                    Image(systemName: "pencil")
                }
                .accessibilityIdentifier("events.editSeries")
                .accessibilityLabel(settings.t("cd_edit_series"))
            }
        }
        .sheet(isPresented: $showSeriesEditor) {
            SeriesConfigSheet(seriesConfigByKey: seriesConfigByKey, onDismiss: { showSeriesEditor = false }, onSave: saveSeriesConfig)
        }
        .sheet(item: $detailsEvent) { event in
            EventDetailsSheet(event: event)
        }
    }

    private func refreshNow() {
        guard !syncing else { return }
        guard ConnectivityMonitor.shared.isOnline else { return } // sin Toast nativo en SwiftUI — se omite el aviso, ver nota de la Fase 6
        syncing = true
        Task {
            _ = await syncManager?.syncScheduleNow()
            syncing = false
        }
    }

    private func saveSeriesConfig(series: RaceSeries, tag: String, colorHex: String) {
        if let existing = seriesConfigs.first(where: { $0.series == series }) {
            existing.tag = tag
            existing.colorHex = colorHex
        } else {
            modelContext.insert(SeriesConfigModel(series: series, tag: tag, colorHex: colorHex))
        }
        try? modelContext.save()
    }
}
