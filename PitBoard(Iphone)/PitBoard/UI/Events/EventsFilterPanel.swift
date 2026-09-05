import SwiftUI
import PitBoardKit

/// Tipos de sesión que se pueden elegir en el filtro rápido — se deja fuera `.other`
/// ("" — sin clasificar), que no es algo que nadie elija filtrar a propósito. Equivalente
/// exacto de `SESSION_TYPE_FILTER_OPTIONS` en `EventsScreen.kt`.
private let sessionTypeFilterOptions: [SessionBadgeType] = [.race, .qualy, .sprint, .practice]

/// Panel de filtro de Eventos (búsqueda + series + tipo de sesión) — vive detrás del botón
/// de embudo de la barra superior, ver `EventsScreen`. Extraído de `EventsScreen.swift`
/// (Fase 4 del diagnóstico) sin cambiar ningún comportamiento, solo para que ese archivo
/// deje de concentrar navegación + filtros + lista + diálogos en un único sitio de 620
/// líneas.
struct EventsFilterPanel: View {
    @Binding var searchQuery: String
    let selectedSeries: Set<RaceSeries>
    let selectedSessionTypes: Set<String>
    let seriesConfigByKey: [RaceSeries: SeriesConfigModel]
    let onSeriesChange: (Set<RaceSeries>) -> Void
    let onSessionTypesChange: (Set<String>) -> Void

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
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
                    onSeriesChange([])
                }
                ForEach(RaceSeries.allCases) { series in
                    let active = selectedSeries.contains(series)
                    FilterChipView(label: seriesConfigByKey[series]?.tag ?? series.defaultTag, selected: active) {
                        var updated = selectedSeries
                        if active { updated.remove(series) } else { updated.insert(series) }
                        onSeriesChange(updated)
                    }
                }
            }

            FlowLayout(spacing: 8) {
                FilterChipView(label: settings.t("events_filter_all_sessions"), selected: selectedSessionTypes.isEmpty) {
                    onSessionTypesChange([])
                }
                ForEach(sessionTypeFilterOptions, id: \.rawValue) { badge in
                    let active = selectedSessionTypes.contains(badge.rawValue)
                    FilterChipView(label: settings.t(badge.labelKey), selected: active) {
                        var updated = selectedSessionTypes
                        if active { updated.remove(badge.rawValue) } else { updated.insert(badge.rawValue) }
                        onSessionTypesChange(updated)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
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
