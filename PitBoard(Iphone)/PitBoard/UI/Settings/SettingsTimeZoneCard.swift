import SwiftUI
import PitBoardKit

/// Tarjeta "Zona horaria" de Ajustes — extraída de `SettingsScreen.swift` (Fase 4 del
/// diagnóstico) sin cambiar ningún comportamiento.
struct SettingsTimeZoneCard: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        SettingsCard(title: settings.t("settings_timezone_title"), systemImage: "globe") {
            Text(settings.t("settings_timezone_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: 8) {
                timeModeChip(mode: .device, label: settings.t("settings_timezone_device"))
                timeModeChip(mode: .track, label: settings.t("settings_timezone_track"))
            }
        }
    }

    private func timeModeChip(mode: TimeDisplayMode, label: String) -> some View {
        let selected = settings.timeDisplayMode == mode
        return Button(label) { settings.setTimeDisplayMode(mode) }
            .buttonStyle(.bordered)
            .tint(selected ? colors.primary : colors.onSurfaceVariant)
            .background(selected ? colors.primaryContainer : .clear, in: Capsule())
            .accessibilityIdentifier("settings.timeDisplayMode.\(mode.rawValue)")
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
