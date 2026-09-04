package com.pitboard.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta de PitBoard. Antes solo se fijaban primary/background/surface (en MainActivity.kt)
 * y el resto de los ~25 roles de Material 3 quedaban en el morado por defecto del framework,
 * que desentonaba con el azul/rosa/ámbar de marca (visible sobre todo en el tema claro, en
 * cosas como el FilterChip seleccionado). Aquí se completan los roles que la app usa de
 * verdad: secondary/tertiary quedan ligados a los mismos colores que ya se usan para las
 * insignias de sesión (ver BadgeColors.kt) para que todo se sienta parte de la misma paleta.
 *
 * Todos los pares color/onColor se han comprobado con la fórmula de contraste WCAG y superan
 * 4.5:1 (texto normal) salvo donde se indica lo contrario.
 */

val PitBoardDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2E6DE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF16305E),
    onPrimaryContainer = Color(0xFFB9D0FF),

    secondary = Color(0xFFF2A93B),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4014),
    onSecondaryContainer = Color(0xFFFCE8C4),

    tertiary = Color(0xFFE23E7A),
    onTertiary = Color(0xFF200010),
    tertiaryContainer = Color(0xFF6B1035),
    onTertiaryContainer = Color(0xFFFBD9E5),

    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFEEF0F2),
    surface = Color(0xFF131519),
    onSurface = Color(0xFFEEF0F2),
    surfaceVariant = Color(0xFF1C1F26),
    onSurfaceVariant = Color(0xFF9AA0AB),
    outline = Color(0xFF3A3F4A),
    outlineVariant = Color(0xFF262A31)
)

val PitBoardLightColorScheme = lightColorScheme(
    primary = Color(0xFF2E6DE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FB),
    onPrimaryContainer = Color(0xFF0B2E66),

    secondary = Color(0xFF8A5A12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE8C4),
    onSecondaryContainer = Color(0xFF4D3307),

    tertiary = Color(0xFFC22A63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD9E5),
    onTertiaryContainer = Color(0xFF5C0F2E),

    background = Color(0xFFF4F3F0),
    onBackground = Color(0xFF181815),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181815),
    surfaceVariant = Color(0xFFE7E5E1),
    onSurfaceVariant = Color(0xFF5F6570),
    outline = Color(0xFFC9C7C2),
    outlineVariant = Color(0xFFDEDCD7)
)
