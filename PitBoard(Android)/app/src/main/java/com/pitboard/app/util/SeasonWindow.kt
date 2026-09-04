package com.pitboard.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Límite superior para "eventos de este año": el 31 de diciembre a las 23:59:59 en UTC del año
 * en curso. Existe porque un par de fuentes (GT World Challenge, F1 Academy) listan "los
 * próximos eventos" sin pedir un año concreto — si esa web publica pronto el primer evento de
 * la temporada siguiente (pasa en diciembre), sin este límite se colaría en la lista antes de
 * tiempo.
 *
 * Se recalcula siempre a partir de [nowUtc], nunca de un año fijo — así al pasar al año
 * siguiente el límite se mueve solo, sin publicar una versión nueva de la app. Un evento que se
 * cuele en la base de datos por pertenecer ya al año próximo no se pierde: simplemente no
 * aparece hasta que ese año llegue de verdad.
 */
object SeasonWindow {
    fun endOfCurrentYearUtc(nowUtc: Long = System.currentTimeMillis()): Long {
        val year = Instant.ofEpochMilli(nowUtc).atZone(ZoneOffset.UTC).year
        return LocalDate.of(year, 12, 31)
            .atTime(23, 59, 59)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
