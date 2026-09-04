package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * formula1.com en vez de driverdb.com (28/08/2026) — driverdb incluía pilotos reserva/de
 * test sin carreras disputadas (32 filas en vez de los 23 reales) y no siempre desempataba
 * bien las posiciones en caso de empate a puntos. La tabla oficial de F1 trae exactamente
 * los 23 pilotos que han corrido esta temporada, ya con las posiciones bien desempatadas —
 * pero sin fotos, así que se completan (mejor esfuerzo) buscando el nombre en driverdb.
 *
 * 28/08/2026: además se intenta la foto oficial de la página de perfil de cada piloto en
 * formula1.com (más profesional/actual que driverdb) antes de recurrir a la de driverdb —
 * a costa de una petición HTTP extra por piloto en cada sincronización (23 en total).
 */
class F1StandingsSource : OfficialRosterStandingsSource(
    category = StandingsCategory.F1,
    rosterUrl = "https://www.formula1.com/en/results/2026/drivers",
    driverDbSlug = "formula-1",
    officialProfileUrlTemplate = "https://www.formula1.com/en/drivers/{slug}"
)
