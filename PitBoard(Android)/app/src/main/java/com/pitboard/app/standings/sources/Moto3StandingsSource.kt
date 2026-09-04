package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory
import java.time.Year

/**
 * Mismo tratamiento que Moto2StandingsSource.kt (ver allí el detalle) — autosport.com,
 * misma plantilla de tabla, comprobado a mano el 03/09/2026.
 *
 * Fotos de piloto y logos de equipo (03/09/2026, añadidas a posteriori): en vivo desde la
 * API interna de motogp.com, con el UUID de categoría de Moto3
 * ("1ab203aa-e292-4842-8bed-971911357af1", sacado de
 * api.pulselive.motogp.com/motogp/v1/categories?seasonYear=2026).
 */
class Moto3StandingsSource : MotorsportStandingsHtmlSource(
    category = StandingsCategory.MOTO3,
    driverUrl = "https://www.autosport.com/moto3/standings/${Year.now().value}/?type=Driver",
    teamUrl = "https://www.autosport.com/moto3/standings/${Year.now().value}/?type=Team",
    pulseliveCategoryUuid = "1ab203aa-e292-4842-8bed-971911357af1"
)
