import SwiftUI
import UIKit

/// Utilidades de contraste para pintar texto legible sobre colores de serie arbitrarios
/// (el usuario los elige libremente en el editor de series) — equivalente exacto de
/// `ColorContrast.kt`.
public enum ColorContrast {
    public static func perceivedLuminance(_ color: Color) -> Double {
        let (r, g, b, _) = components(of: color)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    /// Empuja `color` hacia claro/oscuro si su luminancia percibida está demasiado cerca
    /// de la de `background`, para que no se confundan visualmente entre sí.
    public static func ensureContrast(_ color: Color, background: Color) -> Color {
        if abs(perceivedLuminance(color) - perceivedLuminance(background)) >= 0.25 { return color }

        var h: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
        _ = UIColor(color).getHue(&h, saturation: &s, brightness: &v, alpha: &a)

        let backgroundIsDark = perceivedLuminance(background) < 0.5
        let newValue = backgroundIsDark ? min(v + 0.4, 1.0) : max(v - 0.4, 0.0)
        return Color(UIColor(hue: h, saturation: s, brightness: newValue, alpha: a))
    }

    /// Blanco o negro, el que mejor se lea encima de `background`.
    public static func readableTextColor(background: Color) -> Color {
        perceivedLuminance(background) > 0.55 ? .black : .white
    }

    public static let fallbackColor = Color(hex: "#5F6570") ?? .gray

    public static func safeParseColor(_ hex: String, fallback: Color = ColorContrast.fallbackColor) -> Color {
        Color(hex: hex) ?? fallback
    }

    private static func components(of color: Color) -> (Double, Double, Double, Double) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        _ = UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Double(r), Double(g), Double(b), Double(a))
    }
}

public extension Color {
    /// "#RRGGBB" o "#AARRGGBB" (con o sin "#") — equivalente de
    /// `android.graphics.Color.parseColor`. nil si el texto no es un hex válido, para que
    /// quien llama caiga a su propio color de respaldo (nunca un color inventado).
    init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6 || s.count == 8, let value = UInt64(s, radix: 16) else { return nil }

        let r, g, b, a: Double
        if s.count == 8 {
            a = Double((value >> 24) & 0xFF) / 255
            r = Double((value >> 16) & 0xFF) / 255
            g = Double((value >> 8) & 0xFF) / 255
            b = Double(value & 0xFF) / 255
        } else {
            a = 1
            r = Double((value >> 16) & 0xFF) / 255
            g = Double((value >> 8) & 0xFF) / 255
            b = Double(value & 0xFF) / 255
        }
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}
