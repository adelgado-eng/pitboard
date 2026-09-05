import SwiftUI
import PitBoardKit

/// Configura el tag corto (iniciales) y el color de cada una de las series — botón lápiz de
/// la barra superior de Eventos. Extraído de `EventsScreen.swift` (Fase 4 del diagnóstico)
/// sin cambiar ningún comportamiento.
struct SeriesConfigSheet: View {
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
