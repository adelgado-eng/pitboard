package com.pitboard.app.weather

import com.pitboard.app.standings.StandingsHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Clima de un piloto de un circuito conocido — resultado de [WeatherRepository.fetch]. Nunca
 *  es "clima" a secas: la UI necesita distinguir "no sabemos dónde está este circuito todavía"
 *  de "no hay previsión tan lejos" de "hubo un fallo de red", porque el mensaje correcto para
 *  cada caso es distinto. */
sealed class WeatherResult {
    data class Available(val tempCelsius: Double, val rainProbabilityPercent: Int) : WeatherResult()
    object CircuitUnknown : WeatherResult()
    object TooFarAhead : WeatherResult()
    object Error : WeatherResult()
}

/**
 * Clima del circuito para un evento, vía Open-Meteo (api.open-meteo.com) — gratis, sin clave de
 * API, licencia CC-BY-4.0. Se pide bajo demanda, solo cuando el usuario abre el detalle de un
 * evento concreto (EventDetailsSheet) — nunca para toda la lista de golpe, mismo criterio que ya
 * usa la app para las fotos de perfil oficiales (una petición extra solo cuando hace falta).
 */
object WeatherRepository {

    /** La previsión gratuita de Open-Meteo cubre unos 16 días vista — más allá de eso no hay
     *  nada que pedir todavía, así que ni se intenta la llamada de red. */
    private const val MAX_DAYS_AHEAD = 15L

    suspend fun fetch(eventFullTitle: String, startTimeUtc: Long, nowUtc: Long): WeatherResult {
        val (lat, lon) = CircuitCoordinates.find(eventFullTitle) ?: return WeatherResult.CircuitUnknown

        val today = Instant.ofEpochMilli(nowUtc).atZone(ZoneOffset.UTC).toLocalDate()
        val eventDate = Instant.ofEpochMilli(startTimeUtc).atZone(ZoneOffset.UTC).toLocalDate()
        val daysAhead = ChronoUnit.DAYS.between(today, eventDate)
        if (daysAhead !in 0..MAX_DAYS_AHEAD) return WeatherResult.TooFarAhead

        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,precipitation_probability" +
            "&timezone=UTC" +
            "&start_date=$eventDate&end_date=$eventDate"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PitBoard/1.0 (weather)")
                .build()
            val body = StandingsHttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Open-Meteo: HTTP ${response.code}")
                response.body?.string() ?: error("Open-Meteo: cuerpo vacío")
            }

            val hourly = JSONObject(body).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val rainChances = hourly.getJSONArray("precipitation_probability")
            if (times.length() == 0) return WeatherResult.TooFarAhead

            // La hora exacta del evento no siempre coincide con una marca de la previsión
            // (que viene en punto en punto) — se usa la más cercana.
            val targetHour = Instant.ofEpochMilli(startTimeUtc).atZone(ZoneOffset.UTC)
            var bestIndex = 0
            var bestDiffMinutes = Long.MAX_VALUE
            for (i in 0 until times.length()) {
                val slot = LocalDateTime.parse(times.getString(i)).atZone(ZoneOffset.UTC)
                val diff = abs(ChronoUnit.MINUTES.between(slot, targetHour))
                if (diff < bestDiffMinutes) {
                    bestDiffMinutes = diff
                    bestIndex = i
                }
            }

            WeatherResult.Available(
                tempCelsius = temps.getDouble(bestIndex),
                rainProbabilityPercent = rainChances.getInt(bestIndex)
            )
        } catch (_: Exception) {
            WeatherResult.Error
        }
    }
}
