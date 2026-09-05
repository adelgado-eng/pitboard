import SwiftUI
import SwiftData
import PitBoardKit

/// Tipos de sesión que se pueden elegir en el filtro rápido — se deja fuera `.other`
/// ("" — sin clasificar), que no es algo que nadie elija filtrar a propósito. Equivalente
/// exacto de `SESSION_TYPE_FILTER_OPTIONS` en `EventsScreen.kt`.
private let sessionTypeFilterOptions: [SessionBadgeType] = [.race, .qualy, .sprint, .practice]

/// Pantalla de Eventos — equivalente de `EventsScreen.kt` (`EventsViewModel` incluido).
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
                filterPanel
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
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            if !isOnline {
                                OfflineBanner(message: settings.t("offline_banner_message"))
                            }

                            if !weekendEvents.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(settings.t(groups.weekendLabelKey).uppercased())
                                        .font(.caption.bold())
                                        .foregroundStyle(colors.primary)
                                        .padding(.leading, 4)

                                    VStack(spacing: 0) {
                                        ForEach(Array(weekendEvents.enumerated()), id: \.element.uid) { index, event in
                                            EventRow(event: event, config: seriesConfigByKey[event.series], timeDisplayMode: settings.timeDisplayMode) {
                                                detailsEvent = event
                                            }
                                            if index < weekendEvents.count - 1 {
                                                Divider().padding(.horizontal, 16)
                                            }
                                        }
                                    }
                                    .padding(.vertical, 4)
                                    .background(colors.surface)
                                    .clipShape(RoundedRectangle(cornerRadius: PitBoardShapes.large))
                                }
                            }

                            if !laterEvents.isEmpty {
                                Text(settings.t("events_later_section"))
                                    .font(.caption.bold())
                                    .foregroundStyle(colors.onSurfaceVariant)
                                    .padding(.leading, 4)
                                    .padding(.top, 8)

                                VStack(spacing: 8) {
                                    ForEach(laterEvents, id: \.uid) { event in
                                        EventCard(event: event, config: seriesConfigByKey[event.series], timeDisplayMode: settings.timeDisplayMode) {
                                            detailsEvent = event
                                        }
                                    }
                                }
                            }
                        }
                        .padding(16)
                    }
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

    private var filterPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary).accessibilityHidden(true)
                TextField(settings.t("events_search_placeholder"), text: $searchQuery)
                    .accessibilityIdentifier("events.searchField")
                if !searchQuery.isEmpty {
                    Button { searchQuery = "" } label: { Image(systemName: "xmark.circle.fill") }
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(settings.t("cd_clear_search"))
                }
            }
            .padding(10)
            .background(colors.surfaceVariant.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: PitBoardShapes.small))

            FlowLayout(spacing: 8) {
                FilterChipView(label: settings.t("events_filter_all_series"), selected: selectedSeries.isEmpty) {
                    settings.setEventScreenActiveSeries([])
                }
                ForEach(RaceSeries.allCases) { series in
                    let active = selectedSeries.contains(series)
                    FilterChipView(label: seriesConfigByKey[series]?.tag ?? series.defaultTag, selected: active) {
                        var updated = selectedSeries
                        if active { updated.remove(series) } else { updated.insert(series) }
                        settings.setEventScreenActiveSeries(updated)
                    }
                }
            }

            FlowLayout(spacing: 8) {
                FilterChipView(label: settings.t("events_filter_all_sessions"), selected: selectedSessionTypes.isEmpty) {
                    settings.setEventScreenActiveSessionTypes([])
                }
                ForEach(sessionTypeFilterOptions, id: \.rawValue) { badge in
                    let active = selectedSessionTypes.contains(badge.rawValue)
                    FilterChipView(label: settings.t(badge.labelKey), selected: active) {
                        var updated = selectedSessionTypes
                        if active { updated.remove(badge.rawValue) } else { updated.insert(badge.rawValue) }
                        settings.setEventScreenActiveSessionTypes(updated)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
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

// MARK: - Editor de series (botón lápiz de la barra superior)

/// Configura el tag corto (iniciales) y el color de cada una de las series — equivalente
/// de `SeriesConfigSheet`.
private struct SeriesConfigSheet: View {
    let seriesConfigByKey: [RaceSeries: SeriesConfigModel]
    let onDismiss: () -> Void
    let onSave: (RaceSeries, String, String) -> Void

    @State private var editingSeries: RaceSeries?
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        NavigationStack {
            List(RaceSeries.allCases) { series in
                let config = seriesConfigByKey[series]
                SeriesConfigRow(
                    series: series,
                    tag: config?.tag ?? series.defaultTag,
                    colorHex: config?.colorHex ?? series.defaultColorHex
                ) {
                    editingSeries = series
                }
            }
            .listStyle(.plain)
            .navigationTitle(settings.t("events_edit_series"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(settings.t("events_close"), action: onDismiss)
                }
            }
        }
        .sheet(item: $editingSeries) { series in
            let config = seriesConfigByKey[series]
            EditSeriesConfigSheet(
                series: series,
                initialTag: config?.tag ?? series.defaultTag,
                initialColorHex: config?.colorHex ?? series.defaultColorHex,
                onSave: { tag, colorHex in
                    onSave(series, tag, colorHex)
                    editingSeries = nil
                },
                onCancel: { editingSeries = nil }
            )
        }
    }
}

private struct SeriesConfigRow: View {
    let series: RaceSeries
    let tag: String
    let colorHex: String
    let onTap: () -> Void
    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        Button(action: onTap) {
            HStack {
                ColorSwatch(hex: colorHex)
                VStack(alignment: .leading, spacing: 2) {
                    Text(series.displayName).font(.subheadline.weight(.semibold))
                    Text(String(format: settings.t("events_series_tag_prefix"), tag)).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Text(settings.t("events_edit")).font(.footnote.weight(.semibold)).foregroundStyle(colors.primary)
            }
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }
}

private struct EditSeriesConfigSheet: View {
    let series: RaceSeries
    let initialTag: String
    let initialColorHex: String
    let onSave: (String, String) -> Void
    let onCancel: () -> Void

    @State private var tag: String
    @State private var colorHex: String
    @Environment(AppSettingsRepository.self) private var settings

    init(series: RaceSeries, initialTag: String, initialColorHex: String, onSave: @escaping (String, String) -> Void, onCancel: @escaping () -> Void) {
        self.series = series
        self.initialTag = initialTag
        self.initialColorHex = initialColorHex
        self.onSave = onSave
        self.onCancel = onCancel
        _tag = State(initialValue: initialTag)
        _colorHex = State(initialValue: initialColorHex)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(series.displayName) {
                    TextField(settings.t("events_tag_label"), text: $tag)
                        .onChange(of: tag) { _, newValue in
                            let upper = newValue.uppercased()
                            tag = String(upper.prefix(5))
                        }
                    TextField(settings.t("events_color_label"), text: $colorHex)
                        .autocorrectionDisabled()
                    HStack {
                        Text(settings.t("events_preview_label")).foregroundStyle(.secondary)
                        Spacer()
                        ColorSwatch(hex: colorHex)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(settings.t("events_cancel"), action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(settings.t("events_save")) { onSave(tag.isEmpty ? initialTag : tag, colorHex) }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

private struct ColorSwatch: View {
    let hex: String
    var body: some View {
        Circle()
            .fill(ColorContrast.safeParseColor(hex))
            .frame(width: 28, height: 28)
    }
}

// MARK: - Filas de evento

private struct EventRow: View {
    let event: EventModel
    let config: SeriesConfigModel?
    let timeDisplayMode: TimeDisplayMode
    let onTap: () -> Void
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        let tagColor = ColorContrast.ensureContrast(
            ColorContrast.safeParseColor(config?.colorHex ?? event.series.defaultColorHex, fallback: BadgeColors.fallback),
            background: colors.surface
        )

        Button(action: onTap) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: PitBoardShapes.extraSmall)
                    .fill(tagColor)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Text(config?.tag ?? event.series.defaultTag)
                            .font(.callout.bold())
                            .foregroundStyle(ColorContrast.readableTextColor(background: tagColor))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(event.fullTitle)
                        .font(.body.weight(.medium))
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(2)
                    Text(DateTimeFormatters.formatEventDateTime(event.startTimeUtc, mode: timeDisplayMode, eventZoneId: event.timeZoneId))
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                Spacer()

                if !event.inferredBadge.isEmpty {
                    SessionBadgeChip(badge: event.inferredBadge)
                }
            }
            .padding(12)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("events.row.\(event.uid)")
    }
}

private struct EventCard: View {
    let event: EventModel
    let config: SeriesConfigModel?
    let timeDisplayMode: TimeDisplayMode
    let onTap: () -> Void
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        EventRow(event: event, config: config, timeDisplayMode: timeDisplayMode, onTap: onTap)
            .background(colors.surfaceVariant.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: PitBoardShapes.medium))
    }
}

private struct SessionBadgeChip: View {
    let badge: String
    var body: some View {
        Circle()
            .fill(BadgeColors.forBadge(badge))
            .frame(width: 28, height: 28)
            .overlay(Text(badge).font(.caption2.bold()).foregroundStyle(.white))
    }
}

// MARK: - Detalle de evento

/// Popup con el detalle de un evento, al tocarlo — equivalente de `EventDetailsSheet`.
/// Solo campos que ya trae `EventModel`, sin ninguna petición de red adicional.
private struct EventDetailsSheet: View {
    let event: EventModel
    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    // Clima del circuito bajo demanda: solo se pide al abrir ESTE evento (nunca para toda la
    // lista de golpe), y solo si Open-Meteo puede tener algo que decir — ver WeatherService.
    @State private var weather: WeatherResult?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(event.series.displayName.uppercased())
                    .font(.footnote.bold())
                    .foregroundStyle(colors.primary)
                Spacer()
                if !event.inferredBadge.isEmpty {
                    SessionBadgeChip(badge: event.inferredBadge)
                }
            }
            Text(event.fullTitle)
                .font(.title2.bold())

            Divider()

            DetailLine(label: settings.t("events_detail_your_time"), value: DateTimeFormatters.formatEventDateTimeLong(event.startTimeUtc))
            if let zoneId = event.timeZoneId, let local = DateTimeFormatters.formatEventDateTime(event.startTimeUtc, inZone: zoneId) {
                DetailLine(label: String(format: settings.t("events_detail_track_time"), zoneId), value: local)
            }
            DetailLine(label: settings.t("events_detail_series"), value: event.series.displayName)
            if !event.inferredBadge.isEmpty, let badge = SessionBadgeType(rawValue: event.inferredBadge) {
                DetailLine(label: settings.t("events_detail_session_type"), value: settings.t(badge.labelKey))
            }
            // Sin fila cuando el circuito no se reconoce o está demasiado lejos en el
            // futuro — no aporta nada un "Clima: —" para el 90% de los eventos de la
            // temporada que todavía no tienen previsión.
            if case .available(let tempCelsius, let rainProbabilityPercent) = weather {
                DetailLine(label: settings.t("events_detail_weather"), value: String(format: settings.t("events_detail_weather_value"), Int(tempCelsius), rainProbabilityPercent))
            }
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .task(id: event.uid) {
            weather = await WeatherService.fetch(eventFullTitle: event.fullTitle, startTimeUtc: event.startTimeUtc)
        }
    }
}

private struct DetailLine: View {
    let label: String
    let value: String
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(.caption2.bold()).foregroundStyle(colors.primary)
            Text(value).font(.subheadline).foregroundStyle(colors.onSurface)
        }
    }
}

private struct FilterChipView: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.footnote.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? colors.primaryContainer : colors.surfaceVariant.opacity(0.5))
                .foregroundStyle(selected ? colors.onPrimaryContainer : colors.onSurfaceVariant)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        // 05/09/2026 (Fase 2, accesibilidad): sin esto VoiceOver nunca anunciaba si un chip
        // de filtro estaba activo o no.
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Envuelve chips en varias líneas, ajustando al ancho disponible — equivalente de
/// `FlowRow` de Compose (SwiftUI no trae un layout de ajuste de línea nativo antes de
/// iOS 16 `Layout`; con iOS 17 como mínimo del proyecto, se implementa directamente con
/// el protocolo `Layout`).
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var totalHeight: CGFloat = 0
        var lineWidth: CGFloat = 0
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if lineWidth > 0, lineWidth + size.width > maxWidth {
                totalHeight += lineHeight + spacing
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        totalHeight += lineHeight
        return CGSize(width: maxWidth.isFinite ? maxWidth : lineWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + spacing
                lineHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}
