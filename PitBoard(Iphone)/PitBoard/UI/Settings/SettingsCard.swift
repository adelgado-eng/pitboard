import SwiftUI
import PitBoardKit

/// Envoltorio visual reutilizado por todas las tarjetas de Ajustes (icono + título +
/// contenido) — extraído de `SettingsScreen.swift` (Fase 4 del diagnóstico) sin cambiar
/// ningún comportamiento.
struct SettingsCard<Content: View>: View {
    let title: String
    let systemImage: String
    let content: () -> Content
    @Environment(\.pitBoardColors) private var colors

    init(title: String, systemImage: String, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.systemImage = systemImage
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .foregroundStyle(colors.primary)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.title3.weight(.bold))
            }
            .padding(.bottom, 16)

            VStack(alignment: .leading, spacing: 0) {
                content()
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: PitBoardShapes.large))
    }
}
