package com.pitboard.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * Utilidades de contraste para pintar texto legible sobre colores de categoría arbitrarios
 * (el usuario los elige libremente como texto en CategoryManagerScreen, sin selector visual
 * ni vista previa de contraste). Esta lógica vivía solo dentro de RaceWidget.kt — el widget ya
 * protegía sus etiquetas, pero la app de teléfono pintaba el texto en blanco fijo encima de
 * cualquier color, así que un color claro podía dejar el texto invisible. Ahora la comparten
 * los dos sitios.
 */
object ColorContrast {
    fun perceivedLuminance(color: Color): Float =
        0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

    /** Empuja [color] hacia claro/oscuro si su luminancia percibida está demasiado cerca de
     *  la de [background], para que no se confundan visualmente entre sí. */
    fun ensureContrast(color: Color, background: Color): Color {
        if (abs(perceivedLuminance(color) - perceivedLuminance(background)) >= 0.25f) return color
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[2] = if (perceivedLuminance(background) < 0.5f) {
            (hsv[2] + 0.4f).coerceAtMost(1f)
        } else {
            (hsv[2] - 0.4f).coerceAtLeast(0f)
        }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** Blanco o negro, el que mejor se lea encima de [background]. */
    fun readableTextColor(background: Color): Color =
        if (perceivedLuminance(background) > 0.55f) Color.Black else Color.White

    fun safeParseColor(hex: String, fallback: Color = Color(0xFF5F6570)): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
