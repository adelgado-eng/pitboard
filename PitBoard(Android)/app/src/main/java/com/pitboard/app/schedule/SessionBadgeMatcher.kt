package com.pitboard.app.schedule

import com.pitboard.app.data.SessionBadgeType

/**
 * Traduce el nombre de una sesión (tal como lo da cada fuente: "Qualifying", "Free Practice 2",
 * "Sprint Race", "Race"...) al badge Q/S/C/L que ya entiende el resto de la app (ver
 * SessionBadgeType). Lo usan las fuentes cuyo dato de origen ya distingue el tipo de sesión por
 * nombre (JSON-LD de F2/F3/ELMS, tablas de horario de IMSA/GT World Challenge) — MotoGP no lo
 * necesita porque la API de Pulselive ya da un "kind" explícito.
 */
object SessionBadgeMatcher {
    fun match(sessionName: String): String {
        val t = sessionName.lowercase()
        return when {
            "qualifying" in t || "quali" in t || "shootout" in t -> SessionBadgeType.QUALY
            "sprint" in t -> SessionBadgeType.SPRINT
            "practice" in t || "warm up" in t || "warmup" in t || t.startsWith("fp") -> SessionBadgeType.PRACTICE
            "race" in t || "grand prix" in t -> SessionBadgeType.RACE
            else -> SessionBadgeType.OTHER
        }
    }
}
