package com.pitboard.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.pitboard.app.data.SessionBadgeType

/**
 * Colores de las insignias de sesión (Carrera/Clasificación/Sprint/Libres). Antes estaban
 * definidos por duplicado en dos sitios con dos claves distintas: EventsScreen.kt
 * (SessionBadgeChip, por el código de SessionBadgeType) y RaceWidget.kt (BADGE_COLORS, por
 * letra suelta) — coincidían por casualidad, pero nada obligaba a que siguieran coincidiendo.
 * SessionBadgeType.RACE/QUALY/SPRINT/PRACTICE YA son esas mismas letras ("C","Q","S","L"), así
 * que un único mapa vale para los dos sitios.
 */
object BadgeColors {
    val race = Color(0xFFE23E7A)
    val qualy = Color(0xFFF2A93B)
    val sprint = Color(0xFF2E6DE8)
    val practice = Color(0xFF6B7280)
    val fallback = Color(0xFF5F6570)

    fun forBadge(badge: String): Color = when (badge) {
        SessionBadgeType.RACE -> race
        SessionBadgeType.QUALY -> qualy
        SessionBadgeType.SPRINT -> sprint
        SessionBadgeType.PRACTICE -> practice
        else -> fallback
    }
}
