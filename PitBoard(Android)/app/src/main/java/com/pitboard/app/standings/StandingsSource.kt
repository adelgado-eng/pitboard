package com.pitboard.app.standings

/**
 * Contrato que implementan 14 de las 15 fuentes de clasificación. Queda fuera solo IMSA (ver
 * ImsaStandingsSource sobre por qué). El repositorio no sabe ni le importa si detrás hay
 * JSON o HTML — solo pide una lista de filas ya normalizadas.
 */
interface StandingsSource {
    val category: StandingsCategory

    /** @param nowUtc se pasa desde fuera (en vez de leerlo aquí) para que todas las
     *  filas de una misma sincronización queden marcadas con el mismo instante. */
    suspend fun fetch(nowUtc: Long): List<StandingEntity>
}