import SwiftUI

/// Color del número de puesto en una clasificación: 1º oro, 2º plata, 3º bronce, y del 4º
/// en adelante el color apagado del propio tema — equivalente exacto de `PodiumColors.kt`.
///
/// Reutiliza `ColorContrast.perceivedLuminance` (en vez del `Color.luminance()` de
/// Compose, que es la luminancia relativa WCAG) para decidir claro/oscuro — son fórmulas
/// distintas, pero para un umbral binario de "¿este fondo es oscuro?" sobre los colores
/// fijos de `surface` de esta app dan la misma decisión; se prefiere reutilizar la única
/// utilidad ya existente antes que mantener dos fórmulas de luminancia en el proyecto.
public enum PodiumColors {
    private static let goldOnDark = Color(hex: "#FFC933")!
    private static let silverOnDark = Color(hex: "#C8CEDA")!
    private static let bronzeOnDark = Color(hex: "#D98E4F")!

    private static let goldOnLight = Color(hex: "#8A6A00")!
    private static let silverOnLight = Color(hex: "#60666F")!
    private static let bronzeOnLight = Color(hex: "#8A4A17")!

    /// Color del puesto `position` (empezando en 1), o nil si no es podio — quien llama usa
    /// entonces su propio color apagado (normalmente onSurfaceVariant).
    public static func forPosition(_ position: Int, surface: Color) -> Color? {
        let onDarkBackground = ColorContrast.perceivedLuminance(surface) < 0.5
        switch position {
        case 1: return onDarkBackground ? goldOnDark : goldOnLight
        case 2: return onDarkBackground ? silverOnDark : silverOnLight
        case 3: return onDarkBackground ? bronzeOnDark : bronzeOnLight
        default: return nil
        }
    }
}
