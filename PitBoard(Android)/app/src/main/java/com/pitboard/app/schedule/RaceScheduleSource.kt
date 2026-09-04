package com.pitboard.app.schedule

import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.RaceSeries

/**
 * Contrato que implementan las fuentes de calendario, una por serie (ver RaceSeries) — mismo
 * patrón que StandingsSource para las clasificaciones. El repositorio no sabe ni le importa si
 * detrás hay JSON o HTML, solo pide la lista de sesiones ya normalizadas.
 */
interface RaceScheduleSource {
    val series: RaceSeries

    suspend fun fetch(): List<EventEntity>
}
