package com.pitboard.app.weather

/**
 * Coordenadas (lat, lon) de circuitos conocidos, para pedir el clima a Open-Meteo (ver
 * [WeatherRepository]) — mismo patrón de "mapa de mejor esfuerzo" que TEAM_LOGO_URLS en las
 * fuentes de standings: la clave es una palabra que debe aparecer en [EventEntity.fullTitle]
 * (el circuito, que la propia app ya compone ahí — ver com.pitboard.app.data.EventEntity), y
 * si ningún circuito coincide simplemente no hay clima para ese evento (nunca una fila
 * inventada).
 *
 * 04/09/2026: cobertura inicial centrada en F1 (los 24 circuitos de la temporada 2026, la serie
 * más consultada) más un puñado de circuitos icónicos de otras series que se repiten temporada
 * tras temporada (Daytona, Indianápolis, Le Mans, Spa, Nürburgring, Sebring...). Ampliar la
 * cobertura del resto de series es solo añadir filas aquí — no hace falta tocar ninguna fuente
 * de scraping ni el esquema de la base de datos.
 */
object CircuitCoordinates {

    private data class Circuit(val keyword: String, val lat: Double, val lon: Double)

    // Ordenado de más a menos específico a propósito: "Las Vegas" antes que un genérico
    // "Vegas", "Miami" antes que cualquier otra cosa con "Internacional" etc. — la primera
    // coincidencia gana.
    private val circuits = listOf(
        // --- Fórmula 1 (temporada 2026, 24 circuitos) ---
        Circuit("Albert Park", -37.8497, 144.968),
        Circuit("Shanghai", 31.3389, 121.2198),
        Circuit("Suzuka", 34.8431, 136.5410),
        Circuit("Sakhir", 26.0325, 50.5106),
        Circuit("Jeddah", 21.6319, 39.1044),
        Circuit("Miami", 25.9581, -80.2389),
        Circuit("Imola", 44.3439, 11.7167),
        Circuit("Monte Carlo", 43.7347, 7.4206),
        Circuit("Mónaco", 43.7347, 7.4206),
        Circuit("Monaco", 43.7347, 7.4206),
        Circuit("Barcelona", 41.5700, 2.2611),
        Circuit("Catalunya", 41.5700, 2.2611),
        Circuit("Montreal", 45.5000, -73.5228),
        Circuit("Spielberg", 47.2197, 14.7647),
        Circuit("Red Bull Ring", 47.2197, 14.7647),
        Circuit("Silverstone", 52.0786, -1.0169),
        Circuit("Spa", 50.4372, 5.9714),
        Circuit("Hungaroring", 47.5789, 19.2486),
        Circuit("Zandvoort", 52.3888, 4.5409),
        Circuit("Monza", 45.6156, 9.2811),
        Circuit("Madrid", 40.3086, -3.6519),
        Circuit("Baku", 40.3725, 49.8533),
        Circuit("Marina Bay", 1.2914, 103.8640),
        Circuit("Singapur", 1.2914, 103.8640),
        Circuit("Singapore", 1.2914, 103.8640),
        Circuit("Austin", 30.1328, -97.6411),
        Circuit("Mexico", 19.4042, -99.0907),
        Circuit("México", 19.4042, -99.0907),
        Circuit("Interlagos", -23.7014, -46.6970),
        Circuit("Sao Paulo", -23.7014, -46.6970),
        Circuit("São Paulo", -23.7014, -46.6970),
        Circuit("Las Vegas", 36.1147, -115.1728),
        Circuit("Losail", 25.4900, 51.4542),
        Circuit("Qatar", 25.4900, 51.4542),
        Circuit("Yas Marina", 24.4672, 54.6031),
        Circuit("Abu Dhabi", 24.4672, 54.6031),

        // --- Resistencia / otras series recurrentes ---
        Circuit("Daytona", 29.1853, -81.0697),
        Circuit("Indianápolis", 39.7950, -86.2347),
        Circuit("Indianapolis", 39.7950, -86.2347),
        Circuit("Le Mans", 47.9500, 0.2231),
        Circuit("Nürburgring", 50.3356, 6.9475),
        Circuit("Nurburgring", 50.3356, 6.9475),
        Circuit("Sebring", 27.4547, -81.3487),
        Circuit("Watkins Glen", 42.3369, -76.9272),
        Circuit("Road Atlanta", 34.1500, -83.8158),
        Circuit("Fuji", 35.3717, 138.9264),
        Circuit("Bahréin", 26.0325, 50.5106),
        Circuit("Bahrein", 26.0325, 50.5106)
    )

    /** Busca el circuito por palabra clave en el título del evento (ej. "Formula 1 - GP de
     *  Italia - Monza - Carrera" -> coincide con "Monza"). Null si no hay ninguno conocido
     *  todavía — la UI debe ocultar el clima en ese caso, nunca inventar uno. */
    fun find(eventFullTitle: String): Pair<Double, Double>? =
        circuits.firstOrNull { eventFullTitle.contains(it.keyword, ignoreCase = true) }
            ?.let { it.lat to it.lon }
}
