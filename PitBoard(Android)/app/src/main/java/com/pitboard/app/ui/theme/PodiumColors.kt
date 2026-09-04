package com.pitboard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Color del número de puesto en una clasificación (30/08/2026, petición 3): 1º oro,
 * 2º plata, 3º bronce, y del 4º en adelante el blanco apagado del propio tema — a
 * propósito menos intenso que el de los nombres, para que el podio destaque de un vistazo.
 *
 * Hay dos juegos de tonos porque el mismo oro no sirve para los dos temas: sobre fondo
 * oscuro se usan tonos claros y saturados, y sobre fondo claro versiones oscurecidas (un
 * #FFD700 sobre blanco es prácticamente ilegible). Cuál usar se decide mirando la
 * luminancia real del `surface` del tema, así no hay que ir pasando el AppTheme por media
 * app ni duplicar la lógica de PitBoardTheme.
 */
object PodiumColors {
    private val GoldOnDark = Color(0xFFFFC933)
    private val SilverOnDark = Color(0xFFC8CEDA)
    private val BronzeOnDark = Color(0xFFD98E4F)

    private val GoldOnLight = Color(0xFF8A6A00)
    private val SilverOnLight = Color(0xFF60666F)
    private val BronzeOnLight = Color(0xFF8A4A17)

    /**
     * Color del puesto [position] (empezando en 1), o null si no es podio — quien llama
     * usa entonces su propio color apagado (normalmente onSurfaceVariant).
     */
    @Composable
    fun forPosition(position: Int): Color? {
        val onDarkBackground = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return when (position) {
            1 -> if (onDarkBackground) GoldOnDark else GoldOnLight
            2 -> if (onDarkBackground) SilverOnDark else SilverOnLight
            3 -> if (onDarkBackground) BronzeOnDark else BronzeOnLight
            else -> null
        }
    }
}
