package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory
import java.time.Year

/**
 * Mismo sitio y plantilla que MotoGpStandingsSource (autosport.com) — comprobado a mano el
 * 03/09/2026: la tabla de pilotos usa "Rider"/"Points" igual que MotoGP, así que
 * MotorsportStandingsHtmlSource la reconoce sin cambios.
 *
 * Fotos de piloto y logos de equipo (03/09/2026, añadidas a posteriori): en vivo desde la
 * API interna de motogp.com, con el UUID de categoría de Moto2
 * ("ea854a67-73a4-4a28-ac77-d67b3b2a530a", sacado de
 * api.pulselive.motogp.com/motogp/v1/categories?seasonYear=2026) — comprobados a mano
 * varios pilotos (foto real de estudio, logo de equipo real). Ver
 * MotorsportStandingsHtmlSource.pulseliveCategoryUuid.
 */
class Moto2StandingsSource : MotorsportStandingsHtmlSource(
    category = StandingsCategory.MOTO2,
    driverUrl = "https://www.autosport.com/moto2/standings/${Year.now().value}/?type=Driver",
    teamUrl = "https://www.autosport.com/moto2/standings/${Year.now().value}/?type=Team",
    pulseliveCategoryUuid = "ea854a67-73a4-4a28-ac77-d67b3b2a530a"
)
