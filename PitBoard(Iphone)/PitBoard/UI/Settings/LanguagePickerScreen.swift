import SwiftUI
import PitBoardKit

/// Selector de idioma de primer arranque — equivalente exacto de `LanguagePickerScreen.kt`.
/// Se enseña ANTES que cualquier otra pantalla mientras `AppSettingsRepository.appLanguage`
/// sea `nil` (ver `RootTabView.runStartupSync()`). Los 5 nombres de idioma se enseñan siempre
/// en su propio idioma (nunca traducidos).
///
/// El texto propio de "Elige tu idioma"/"Continuar" usa la selección TODAVÍA NO GUARDADA
/// (`selected`, no `settings.appLanguage`) — según se va tocando una opción, el propio
/// selector cambia de idioma delante del usuario, la mejor confirmación posible de que ha
/// elegido bien antes de continuar.
struct LanguagePickerScreen: View {
    let onLanguageChosen: (AppLanguage) -> Void

    @State private var selected: AppLanguage = .spanish
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        VStack(spacing: 20) {
            Circle()
                .fill(colors.primary)
                .frame(width: 64, height: 64)
                .overlay(
                    Image(systemName: "globe")
                        .foregroundStyle(colors.onPrimary)
                        .font(.title2)
                )

            VStack(spacing: 6) {
                Text(Strings.get("language_picker_title", language: selected))
                    .font(.title.bold())
                    .multilineTextAlignment(.center)
                Text(Strings.get("language_picker_subtitle", language: selected))
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }

            ScrollView {
                VStack(spacing: 10) {
                    ForEach(AppLanguage.allCases, id: \.self) { language in
                        LanguageRow(language: language, selected: language == selected) {
                            selected = language
                        }
                    }
                }
            }

            Button {
                onLanguageChosen(selected)
            } label: {
                Text(Strings.get("language_picker_continue", language: selected))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.background)
    }
}

private struct LanguageRow: View {
    let language: AppLanguage
    let selected: Bool
    let onTap: () -> Void
    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(language.nativeName)
                    .font(.body.weight(selected ? .bold : .regular))
                    .foregroundStyle(colors.onSurface)
                Spacer()
                if selected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(colors.primary)
                }
            }
            .padding(16)
            .background(
                selected ? colors.primaryContainer : colors.surfaceVariant.opacity(0.4),
                in: RoundedRectangle(cornerRadius: PitBoardShapes.medium)
            )
        }
        .buttonStyle(.plain)
    }
}
