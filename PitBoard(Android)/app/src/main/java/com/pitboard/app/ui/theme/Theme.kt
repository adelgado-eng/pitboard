package com.pitboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.pitboard.app.data.AppTheme

/**
 * Punto único de entrada al tema de PitBoard. Antes MainActivity.kt y
 * RaceWidgetConfigActivity.kt definían cada uno su propio MaterialTheme (y el de la pantalla
 * de configurar el widget ni siquiera miraba la preferencia de tema del usuario, se quedaba
 * siempre oscuro). Ahora los dos llaman a esta misma función.
 */
@Composable
fun PitBoardTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) PitBoardDarkColorScheme else PitBoardLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PitBoardShapes,
        content = content
    )
}
