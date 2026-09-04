package com.pitboard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Personalización del usuario para una serie (tag corto + color) — reemplaza a la antigua
 * CategoryEntity, que estaba atada a un calendario importado (calendarSourceId). Ahora las
 * series son fijas (ver RaceSeries), así que esto es simplemente una fila por serie con sus
 * valores por defecto (RaceSeries.defaultTag/defaultColorHex) hasta que el usuario los cambie
 * desde el editor de series (botón lápiz en Eventos).
 */
@Entity(tableName = "series_config")
data class SeriesConfigEntity(
    @PrimaryKey
    val series: RaceSeries,
    val tag: String,
    val colorHex: String
)
