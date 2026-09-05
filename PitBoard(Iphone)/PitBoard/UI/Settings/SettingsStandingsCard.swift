import SwiftUI
import PitBoardKit

/// Tarjeta "Clasificaciones" de Ajustes — extraída de `SettingsScreen.swift` (Fase 4 del
/// diagnóstico) sin cambiar ningún comportamiento.
struct SettingsStandingsCard: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.syncManager) private var syncManager
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        SettingsCard(title: settings.t("settings_standings_title"), systemImage: "trophy.fill") {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(settings.t("settings_standings_enable")).font(.body)
                    Text(settings.t("settings_standings_subtitle"))
                    .font(.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { settings.standingsEnabled },
                    set: { enabled in setStandingsEnabled(enabled) }
                ))
                .labelsHidden()
                .accessibilityIdentifier("settings.standingsToggle")
                .accessibilityLabel(settings.t("settings_standings_enable"))
            }
        }
    }

    /// Al activar, programa el ciclo semanal y lanza una sincronización inmediata — así
    /// el usuario no tiene que esperar hasta el lunes. Al desactivar, cancela la tarea
    /// programada.
    private func setStandingsEnabled(_ enabled: Bool) {
        settings.setStandingsEnabled(enabled)
        if enabled {
            syncManager?.scheduleWeeklyStandingsSync()
            Task { _ = await syncManager?.syncStandingsNow() }
        } else {
            syncManager?.cancelStandingsSync()
        }
    }
}
