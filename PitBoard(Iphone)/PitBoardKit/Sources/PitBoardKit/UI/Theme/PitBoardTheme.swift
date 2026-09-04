import SwiftUI

/// Punto único de entrada al tema de PitBoard — equivalente de `Theme.kt`. Envuelve el
/// contenido, decide claro/oscuro según `AppTheme` (o el sistema si es `.system`), y
/// publica la paleta en el Environment (`\.pitBoardColors`) para que cualquier vista
/// hija la lea sin pasarla a mano por cada init.
public struct PitBoardTheme<Content: View>: View {
    private let appTheme: AppTheme
    @Environment(\.colorScheme) private var systemColorScheme
    private let content: () -> Content

    public init(appTheme: AppTheme = .system, @ViewBuilder content: @escaping () -> Content) {
        self.appTheme = appTheme
        self.content = content
    }

    public var body: some View {
        let useDark: Bool
        switch appTheme {
        case .light: useDark = false
        case .dark: useDark = true
        case .system: useDark = systemColorScheme == .dark
        }
        let scheme = useDark ? PitBoardColorScheme.dark : PitBoardColorScheme.light

        content()
            .environment(\.pitBoardColors, scheme)
            .preferredColorScheme(useDark ? .dark : .light)
            .tint(scheme.primary)
    }
}

/// Radios de esquina — equivalente de `Shape.kt` (los 5 niveles que Material 3 usa por
/// defecto en Card/Button/ModalBottomSheet).
public enum PitBoardShapes {
    public static let extraSmall: CGFloat = 8
    public static let small: CGFloat = 12
    public static let medium: CGFloat = 16
    public static let large: CGFloat = 20
    public static let extraLarge: CGFloat = 28
}
