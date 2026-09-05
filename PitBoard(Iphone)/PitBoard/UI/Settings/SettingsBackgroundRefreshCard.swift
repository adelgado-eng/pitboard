import SwiftUI
import UIKit
import PitBoardKit

/// Tarjeta de ayuda sobre avisos/widget que llegan tarde — extraída de
/// `SettingsScreen.swift` (Fase 4 del diagnóstico) sin cambiar ningún comportamiento.
///
/// Equivalente iOS del enlace a dontkillmyapp.com de Android — ese sitio es específico
/// de fabricantes Android (Samsung/Xiaomi/Huawei matando procesos en segundo plano), así
/// que aquí no aplica tal cual. El problema equivalente en iOS es "Actualización en
/// segundo plano" desactivada (a mano o por Modo de bajo consumo) para PitBoard — este
/// botón lleva directo a esa pantalla de Ajustes del sistema.
struct SettingsBackgroundRefreshCard: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        SettingsCard(title: settings.t("settings_battery_help_title"), systemImage: "battery.25") {
            Text(settings.t("settings_battery_help_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)
                .padding(.bottom, 4)

            Button(settings.t("settings_battery_help_button")) {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
