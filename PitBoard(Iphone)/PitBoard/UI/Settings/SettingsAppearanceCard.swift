import SwiftUI
import PitBoardKit

/// Tarjeta "Apariencia" de Ajustes — extraída de `SettingsScreen.swift` (Fase 4 del
/// diagnóstico) sin cambiar ningún comportamiento.
struct SettingsAppearanceCard: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        SettingsCard(title: settings.t("settings_appearance_title"), systemImage: "paintpalette.fill") {
            Text(settings.t("settings_appearance_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: 8) {
                themeChip(theme: .light, label: settings.t("settings_theme_light"))
                themeChip(theme: .dark, label: settings.t("settings_theme_dark"))
                themeChip(theme: .system, label: settings.t("settings_theme_auto"))
            }
        }
    }

    private func themeChip(theme: AppTheme, label: String) -> some View {
        let selected = settings.appTheme == theme
        return Button(label) { settings.setAppTheme(theme) }
            .buttonStyle(.bordered)
            .tint(selected ? colors.primary : colors.onSurfaceVariant)
            .background(selected ? colors.primaryContainer : .clear, in: Capsule())
            .accessibilityIdentifier("settings.theme.\(theme.rawValue)")
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
