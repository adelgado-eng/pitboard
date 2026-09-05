import SwiftUI
import WidgetKit
import PitBoardKit

/// Paleta interna del widget (independiente del tema de la app) — equivalente exacto de
/// `WidgetPalette`/`DarkWidgetPalette`/`LightWidgetPalette` en `RaceWidget.kt`.
private struct WidgetPalette {
    let chalk: Color
    let chalkDim: Color
    let cardBg: Color

    static let dark = WidgetPalette(chalk: Color(hex: "#EEF0F2")!, chalkDim: Color(hex: "#9AA0AB")!, cardBg: Color(hex: "#1C1F26")!)
    static let light = WidgetPalette(chalk: Color(hex: "#181815")!, chalkDim: Color(hex: "#5F6570")!, cardBg: Color(hex: "#FFFFFF")!)
}

/// Vista raíz del widget — equivalente de `WidgetUI` en `RaceWidget.kt`. A diferencia de
/// Android (3 tamaños fijos en dp, elegidos por `LocalSize.current`), aquí se conmuta por
/// `@Environment(\.widgetFamily)`: `.systemSmall` ≈ HeroRow, `.systemMedium` ≈ MiniRow
/// (una fila horizontal), `.systemLarge` ≈ EventList completa (bloque de fin de semana +
/// "más adelante"). No hay equivalente del botón "✎" que abría `RaceWidgetConfigActivity`
/// — en iOS la edición es el gesto nativo "mantener pulsado → Editar widget" sobre
/// `RaceWidgetConfigurationIntent`, no hace falta ningún botón dentro del propio widget.
struct RaceWidgetView: View {
    let entry: RaceWidgetEntry
    @Environment(\.widgetFamily) private var family

    private var palette: WidgetPalette { entry.useDark ? .dark : .light }

    var body: some View {
        let backgroundColor = (Color(hex: entry.configuration.backgroundColorHex) ?? palette.cardBg)
            .opacity(WidgetPrefsConstants.backgroundOpacity)

        ZStack {
            backgroundColor

            if entry.allEvents.isEmpty {
                Text(Strings.get("events_empty_title", language: entry.appLanguage))
                    .font(.caption)
                    .foregroundStyle(palette.chalkDim)
            } else {
                switch family {
                case .systemSmall:
                    HeroRowView(event: entry.allEvents[0], tag: tag(for: entry.allEvents[0]), tagColor: tagColor(for: entry.allEvents[0]), palette: palette, wordCount: entry.configuration.clampedWordCount, language: entry.appLanguage)
                case .systemLarge, .systemExtraLarge:
                    EventListView(entry: entry, palette: palette, tagColor: tagColor(for:))
                default:
                    MiniRowView(event: entry.allEvents[0], tag: tag(for: entry.allEvents[0]), tagColor: tagColor(for: entry.allEvents[0]), palette: palette, showTrackTime: entry.configuration.showTrackTime, wordCount: entry.configuration.clampedWordCount, language: entry.appLanguage)
                }
            }
        }
        .containerBackground(for: .widget) { backgroundColor }
    }

    private func tagColor(for event: EventDraft) -> Color {
        let hex = entry.seriesTagColors[event.series]?.colorHex ?? event.series.defaultColorHex
        let raw = Color(hex: hex) ?? ColorContrast.fallbackColor
        return ColorContrast.ensureContrast(raw, background: palette.cardBg)
    }

    private func tag(for event: EventDraft) -> String {
        entry.seriesTagColors[event.series]?.tag ?? event.series.defaultTag
    }
}

// MARK: - .systemSmall — equivalente de HeroRow

private struct HeroRowView: View {
    let event: EventDraft
    let tag: String
    let tagColor: Color
    let palette: WidgetPalette
    let wordCount: Int
    let language: AppLanguage

    var body: some View {
        let textOnTag = ColorContrast.readableTextColor(background: tagColor)

        VStack(spacing: 8) {
            VStack(spacing: 2) {
                Text(tag)
                    .font(.caption.bold())
                    .foregroundStyle(textOnTag)
                Text(Strings.get("widget_days_prefix", language: language) + "\(widgetDaysUntil(event.startTimeUtc))")
                    .font(.system(size: 10))
                    .foregroundStyle(textOnTag.opacity(0.85))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tagColor, in: RoundedRectangle(cornerRadius: 12))

            Text(widgetEventDisplayName(fullTitle: event.fullTitle, seriesDisplayName: event.series.displayName, wordLimit: wordCount))
                .font(.footnote.weight(.medium))
                .foregroundStyle(palette.chalk)
                .multilineTextAlignment(.center)
                .lineLimit(2)

            Text(DateTimeFormatters.formatEventDateTime(event.startTimeUtc))
                .font(.system(size: 11))
                .foregroundStyle(palette.chalkDim)
        }
        .padding(12)
    }
}

// MARK: - .systemMedium — equivalente de MiniRow

private struct MiniRowView: View {
    let event: EventDraft
    let tag: String
    let tagColor: Color
    let palette: WidgetPalette
    let showTrackTime: Bool
    let wordCount: Int
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 8) {
            Text(tag)
                .font(.caption.bold())
                .foregroundStyle(ColorContrast.readableTextColor(background: tagColor))
                .padding(.horizontal, 8)
                .frame(height: 28)
                .background(tagColor, in: RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(widgetEventDisplayName(fullTitle: event.fullTitle, seriesDisplayName: event.series.displayName, wordLimit: wordCount))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(palette.chalk)
                    .lineLimit(1)
                if showTrackTime, let trackTime = widgetTrackTimeLabel(startTimeUtc: event.startTimeUtc, timeZoneId: event.timeZoneId) {
                    Text(String(format: Strings.get("widget_track_time", language: language), trackTime))
                        .font(.system(size: 10))
                        .foregroundStyle(palette.chalkDim)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 4)

            Text(Strings.get("widget_days_prefix", language: language) + "\(widgetDaysUntil(event.startTimeUtc))")
                .font(.system(size: 12))
                .foregroundStyle(palette.chalkDim)
        }
        .padding(12)
    }
}

// MARK: - .systemLarge — equivalente de EventList

private struct EventListView: View {
    let entry: RaceWidgetEntry
    let palette: WidgetPalette
    let tagColor: (EventDraft) -> Color

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("🏁 PitBoard")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(palette.chalk)
                Spacer()
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !entry.weekendEvents.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(Strings.get(entry.weekendLabelKey, language: entry.appLanguage).uppercased())
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(palette.chalkDim)

                            VStack(spacing: 0) {
                                ForEach(Array(entry.weekendEvents.enumerated()), id: \.offset) { index, event in
                                    EventItemRow(event: event, tagColor: tagColor(event), tag: entry.seriesTagColors[event.series]?.tag ?? event.series.defaultTag, palette: palette, wordCount: entry.configuration.clampedWordCount, showTrackTime: entry.configuration.showTrackTime, language: entry.appLanguage)
                                    if index < entry.weekendEvents.count - 1 {
                                        Divider().background(palette.chalkDim.opacity(0.2))
                                    }
                                }
                            }
                            .background(palette.cardBg, in: RoundedRectangle(cornerRadius: 20))
                        }
                    }

                    ForEach(Array(entry.laterEvents.enumerated()), id: \.offset) { _, event in
                        EventItemRow(event: event, tagColor: tagColor(event), tag: entry.seriesTagColors[event.series]?.tag ?? event.series.defaultTag, palette: palette, wordCount: entry.configuration.clampedWordCount, showTrackTime: entry.configuration.showTrackTime, language: entry.appLanguage)
                            .background(palette.cardBg, in: RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
        }
        .padding(10)
    }
}

private struct EventItemRow: View {
    let event: EventDraft
    let tagColor: Color
    let tag: String
    let palette: WidgetPalette
    let wordCount: Int
    let showTrackTime: Bool
    let language: AppLanguage

    // 05/09/2026: `body` es @ViewBuilder (requisito del protocolo `View`) — el `if let`
    // que solo mutaba `subtitle` se interpretaba como código que debe producir una View,
    // no una mutación de variable. Detectado por el CI al compilar por primera vez el
    // target real de la app (mismo patrón que PitBoardTheme.swift, Fase 1).
    private var subtitle: String {
        var text = DateTimeFormatters.formatEventDateTime(event.startTimeUtc)
        if showTrackTime, let trackTime = widgetTrackTimeLabel(startTimeUtc: event.startTimeUtc, timeZoneId: event.timeZoneId) {
            text += " (\(trackTime))"
        }
        return text
    }

    var body: some View {
        let textOnTag = ColorContrast.readableTextColor(background: tagColor)

        HStack(spacing: 10) {
            VStack(spacing: 2) {
                Text(tag)
                    .font(.caption.bold())
                    .foregroundStyle(textOnTag)
                Text(Strings.get("widget_days_prefix", language: language) + "\(widgetDaysUntil(event.startTimeUtc))")
                    .font(.system(size: 10))
                    .foregroundStyle(textOnTag.opacity(0.8))
            }
            .frame(width: 44)
            .padding(.vertical, 8)
            .background(tagColor, in: RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(widgetEventDisplayName(fullTitle: event.fullTitle, seriesDisplayName: event.series.displayName, wordLimit: wordCount))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(palette.chalk)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.chalkDim)
                    .lineLimit(1)
            }

            Spacer(minLength: 4)

            if !event.inferredBadge.isEmpty {
                Text(event.inferredBadge)
                    .font(.caption2.bold())
                    .foregroundStyle(.white)
                    .frame(width: 26, height: 26)
                    .background(BadgeColors.forBadge(event.inferredBadge), in: Circle())
            }
        }
        .padding(10)
    }
}
