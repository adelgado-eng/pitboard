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
        let scheme = useDark ? PitBoardColorScheme.dark : PitBoardColorScheme.light

        content()
            .environment(\.pitBoardColors, scheme)
            .preferredColorScheme(useDark ? .dark : .light)
            .tint(scheme.primary)
    }

    // 05/09/2026: "body" es una función @ViewBuilder implícita (por el requisito de
    // "View"), así que un "switch" con asignaciones simples puesto DIRECTAMENTE dentro de
    // "body" hace que el compilador intente tratar cada "case" como si tuviera que
    // producir una "View" ("'buildExpression' is unavailable: this expression does not
    // conform to 'View'") — lo detectó el CI de GitHub Actions al compilar de verdad por
    // primera vez. Sacar el switch a una propiedad calculada normal (fuera del
    // @ViewBuilder) es el arreglo estándar de SwiftUI.
    private var useDark: Bool {
        switch appTheme {
        case .light: return false
        case .dark: return true
        case .system: return systemColorScheme == .dark
        }
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
