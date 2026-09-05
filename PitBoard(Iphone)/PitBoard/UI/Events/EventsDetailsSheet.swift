import SwiftUI
import PitBoardKit

/// Popup con el detalle de un evento, al tocarlo — equivalente de `EventDetailsSheet.kt`.
/// Solo campos que ya trae `EventModel`, sin ninguna petición de red adicional. Extraído de
/// `EventsScreen.swift` (Fase 4 del diagnóstico) sin cambiar ningún comportamiento.
struct EventDetailsSheet: View {
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
