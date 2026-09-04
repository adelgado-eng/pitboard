package com.pitboard.app.data

/**
 * Configuración de avisos para un tipo de sesión (badge inferido del título).
 */
data class BadgeNotificationSetting(
    val enabled: Boolean,
    val minutesBefore: Int
)

object SessionBadgeType {
    const val RACE = "C"
    const val QUALY = "Q"
    const val SPRINT = "S"
    const val PRACTICE = "L"
    const val OTHER = ""

    val ALL = listOf(RACE, QUALY, SPRINT, PRACTICE, OTHER)

    fun label(badge: String): String = when (badge) {
        RACE -> "Carrera"
        QUALY -> "Clasificación"
        SPRINT -> "Sprint"
        PRACTICE -> "Libres"
        else -> "Otros"
    }

    /** Misma etiqueta que [label] pero como clave de com.pitboard.app.i18n.Strings — [label]
     *  se deja igual a propósito (lo sigue usando EventReminderWorker.kt para el texto de las
     *  notificaciones) y esta clave nueva es solo para quien quiera traducirla (ver
     *  EventsScreen.kt). */
    fun labelKey(badge: String): String = when (badge) {
        RACE -> "session_race"
        QUALY -> "session_qualy"
        SPRINT -> "session_sprint"
        PRACTICE -> "session_practice"
        else -> "session_other"
    }

    fun defaultMinutes(badge: String): Int = when (badge) {
        RACE -> 60
        QUALY, SPRINT -> 30
        PRACTICE -> 15
        else -> 30
    }
}
