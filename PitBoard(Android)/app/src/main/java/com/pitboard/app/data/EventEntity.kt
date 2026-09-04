package com.pitboard.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una sesión concreta de un fin de semana de carreras (ej: "Formula 1 - GP de Italia - Monza -
 * Carrera"), obtenida automáticamente por una fuente de com.pitboard.app.schedule.sources — ya
 * no de un .ics importado a mano (ver RaceSeries, paso 1 de este cambio).
 *
 * Índices:
 * - startTimeUtc: es el campo por el que más se consulta (ORDER BY + WHERE >= ahora).
 * - series: para filtrar rápido por series activas en Eventos/widget/notificaciones.
 *
 * Clave única (series + uid): `uid` lo construye cada fuente (ej: "F1-2026-R05-RACE") de forma
 * estable entre sincronizaciones, para que un REPLACE en cada sync actualice la fila en vez de
 * duplicarla.
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["startTimeUtc"]),
        Index(value = ["series"]),
        Index(value = ["series", "uid"], unique = true)
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val series: RaceSeries,

    // Id estable dentro de la serie (ej: "F1-2026-R05-RACE") — ver nota de la clase arriba
    val uid: String,

    // Texto completo a mostrar (ej: "Formula 1 - GP de Italia - Monza - Carrera") — se compone
    // siempre como "Serie - Nombre de la ronda - Circuito - Sesión" para que EventWeekendGrouper,
    // RaceWidget.eventDisplayName() y el resto de la UI, que ya esperan ese formato, no necesiten
    // ningún cambio.
    val fullTitle: String,

    // Epoch millis UTC del inicio de la sesión
    val startTimeUtc: Long,

    // Zona horaria del circuito (ej: "Europe/Rome"). null si la fuente no la da (ver
    // WikipediaSeasonCalendarSource, que solo tiene fecha sin hora).
    val timeZoneId: String? = null,

    // Q / S / C / L / "" — ver SessionBadgeType. Cada fuente lo asigna directamente a partir
    // del tipo de sesión que scrapea (ya no se infiere por palabras clave sobre el título).
    val inferredBadge: String
)
