import SwiftUI
import PitBoardKit

/// Lista de eventos de Eventos (bloque "este fin de semana" + "más adelante") — extraída de
/// `EventsScreen.swift` (Fase 4 del diagnóstico) sin cambiar ningún comportamiento.
struct EventsList: View {
    let isOnline: Bool
    let weekendEvents: [EventModel]
    let weekendLabelKey: String
    let laterEvents: [EventModel]
    let seriesConfigByKey: [RaceSeries: SeriesConfigModel]
    let timeDisplayMode: TimeDisplayMode
    let onEventTap: (EventModel) -> Void

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if !isOnline {
                    OfflineBanner(message: settings.t("offline_banner_message"))
                }

                if !weekendEvents.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(settings.t(weekendLabelKey).uppercased())
                            .font(.caption.bold())
                            .foregroundStyle(colors.primary)
                            .padding(.leading, 4)

                        VStack(spacing: 0) {
                            ForEach(Array(weekendEvents.enumerated()), id: \.element.uid) { index, event in
                                EventRow(event: event, config: seriesConfigByKey[event.series], timeDisplayMode: timeDisplayMode) {
                                    onEventTap(event)
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
                            EventCard(event: event, config: seriesConfigByKey[event.series], timeDisplayMode: timeDisplayMode) {
                                onEventTap(event)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

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

/// Círculo con las iniciales de la sesión (C/Q/S/L) — usado tanto en la lista (`EventRow`)
/// como en el popup de detalle (`EventDetailsSheet`, en su propio archivo), de ahí que no
/// sea `private` aquí.
struct SessionBadgeChip: View {
    let badge: String
    var body: some View {
        Circle()
            .fill(BadgeColors.forBadge(badge))
            .frame(width: 28, height: 28)
            .overlay(Text(badge).font(.caption2.bold()).foregroundStyle(.white))
    }
}
