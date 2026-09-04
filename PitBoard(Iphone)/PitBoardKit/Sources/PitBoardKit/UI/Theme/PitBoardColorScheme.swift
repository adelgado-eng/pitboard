import SwiftUI

/// Paleta de PitBoard — equivalente exacto de `Color.kt` (los ~20 roles de Material 3 que
/// la app usa de verdad). SwiftUI no tiene un "ColorScheme" con roles nombrados como
/// Material 3 (solo claro/oscuro binario) — esta struct + `pitBoardColors` en el
/// Environment (ver `PitBoardTheme.swift`) es el equivalente de `MaterialTheme.colorScheme`.
public struct PitBoardColorScheme: Sendable {
    public var primary: Color
    public var onPrimary: Color
    public var primaryContainer: Color
    public var onPrimaryContainer: Color

    public var secondary: Color
    public var onSecondary: Color
    public var secondaryContainer: Color
    public var onSecondaryContainer: Color

    public var tertiary: Color
    public var onTertiary: Color
    public var tertiaryContainer: Color
    public var onTertiaryContainer: Color

    public var background: Color
    public var onBackground: Color
    public var surface: Color
    public var onSurface: Color
    public var surfaceVariant: Color
    public var onSurfaceVariant: Color
    public var outline: Color
    public var outlineVariant: Color

    public var error: Color
    public var onError: Color
}

public extension PitBoardColorScheme {
    static let dark = PitBoardColorScheme(
        primary: Color(hex: "#2E6DE8")!, onPrimary: Color(hex: "#FFFFFF")!,
        primaryContainer: Color(hex: "#16305E")!, onPrimaryContainer: Color(hex: "#B9D0FF")!,

        secondary: Color(hex: "#F2A93B")!, onSecondary: Color(hex: "#402D00")!,
        secondaryContainer: Color(hex: "#5C4014")!, onSecondaryContainer: Color(hex: "#FCE8C4")!,

        tertiary: Color(hex: "#E23E7A")!, onTertiary: Color(hex: "#200010")!,
        tertiaryContainer: Color(hex: "#6B1035")!, onTertiaryContainer: Color(hex: "#FBD9E5")!,

        background: Color(hex: "#0B0C0F")!, onBackground: Color(hex: "#EEF0F2")!,
        surface: Color(hex: "#131519")!, onSurface: Color(hex: "#EEF0F2")!,
        surfaceVariant: Color(hex: "#1C1F26")!, onSurfaceVariant: Color(hex: "#9AA0AB")!,
        outline: Color(hex: "#3A3F4A")!, outlineVariant: Color(hex: "#262A31")!,

        error: Color(hex: "#FFB4AB")!, onError: Color(hex: "#690005")!
    )

    static let light = PitBoardColorScheme(
        primary: Color(hex: "#2E6DE8")!, onPrimary: Color(hex: "#FFFFFF")!,
        primaryContainer: Color(hex: "#DCE6FB")!, onPrimaryContainer: Color(hex: "#0B2E66")!,

        secondary: Color(hex: "#8A5A12")!, onSecondary: Color(hex: "#FFFFFF")!,
        secondaryContainer: Color(hex: "#FCE8C4")!, onSecondaryContainer: Color(hex: "#4D3307")!,

        tertiary: Color(hex: "#C22A63")!, onTertiary: Color(hex: "#FFFFFF")!,
        tertiaryContainer: Color(hex: "#FBD9E5")!, onTertiaryContainer: Color(hex: "#5C0F2E")!,

        background: Color(hex: "#F4F3F0")!, onBackground: Color(hex: "#181815")!,
        surface: Color(hex: "#FFFFFF")!, onSurface: Color(hex: "#181815")!,
        surfaceVariant: Color(hex: "#E7E5E1")!, onSurfaceVariant: Color(hex: "#5F6570")!,
        outline: Color(hex: "#C9C7C2")!, outlineVariant: Color(hex: "#DEDCD7")!,

        error: Color(hex: "#BA1A1A")!, onError: Color(hex: "#FFFFFF")!
    )
}

private struct PitBoardColorSchemeKey: EnvironmentKey {
    static let defaultValue = PitBoardColorScheme.light
}

public extension EnvironmentValues {
    var pitBoardColors: PitBoardColorScheme {
        get { self[PitBoardColorSchemeKey.self] }
        set { self[PitBoardColorSchemeKey.self] = newValue }
    }
}
