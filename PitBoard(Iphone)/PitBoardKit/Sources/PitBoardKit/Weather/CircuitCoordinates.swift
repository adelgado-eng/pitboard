import Foundation

/// Coordenadas (lat, lon) de circuitos conocidos, para pedir el clima a Open-Meteo (ver
/// `WeatherService`) — equivalente exacto de `CircuitCoordinates.kt`. La clave es una palabra
/// que debe aparecer en `EventModel.fullTitle` (el circuito, que la propia app ya compone
/// ahí) — si ningún circuito coincide, no hay clima para ese evento (nunca una fila inventada).
public enum CircuitCoordinates {

    private struct Circuit {
        let keyword: String
        let lat: Double
        let lon: Double
    }

    // Ordenado de más a menos específico a propósito — la primera coincidencia gana. Misma
    // cobertura inicial que Android: los 24 circuitos de F1 2026 más un puñado de circuitos
    // icónicos de otras series que se repiten temporada tras temporada.
    private static let circuits: [Circuit] = [
        // --- Fórmula 1 (temporada 2026, 24 circuitos) ---
        Circuit(keyword: "Albert Park", lat: -37.8497, lon: 144.968),
        Circuit(keyword: "Shanghai", lat: 31.3389, lon: 121.2198),
        Circuit(keyword: "Suzuka", lat: 34.8431, lon: 136.5410),
        Circuit(keyword: "Sakhir", lat: 26.0325, lon: 50.5106),
        Circuit(keyword: "Jeddah", lat: 21.6319, lon: 39.1044),
        Circuit(keyword: "Miami", lat: 25.9581, lon: -80.2389),
        Circuit(keyword: "Imola", lat: 44.3439, lon: 11.7167),
        Circuit(keyword: "Monte Carlo", lat: 43.7347, lon: 7.4206),
        Circuit(keyword: "Mónaco", lat: 43.7347, lon: 7.4206),
        Circuit(keyword: "Monaco", lat: 43.7347, lon: 7.4206),
        Circuit(keyword: "Barcelona", lat: 41.5700, lon: 2.2611),
        Circuit(keyword: "Catalunya", lat: 41.5700, lon: 2.2611),
        Circuit(keyword: "Montreal", lat: 45.5000, lon: -73.5228),
        Circuit(keyword: "Spielberg", lat: 47.2197, lon: 14.7647),
        Circuit(keyword: "Red Bull Ring", lat: 47.2197, lon: 14.7647),
        Circuit(keyword: "Silverstone", lat: 52.0786, lon: -1.0169),
        Circuit(keyword: "Spa", lat: 50.4372, lon: 5.9714),
        Circuit(keyword: "Hungaroring", lat: 47.5789, lon: 19.2486),
        Circuit(keyword: "Zandvoort", lat: 52.3888, lon: 4.5409),
        Circuit(keyword: "Monza", lat: 45.6156, lon: 9.2811),
        Circuit(keyword: "Madrid", lat: 40.3086, lon: -3.6519),
        Circuit(keyword: "Baku", lat: 40.3725, lon: 49.8533),
        Circuit(keyword: "Marina Bay", lat: 1.2914, lon: 103.8640),
        Circuit(keyword: "Singapur", lat: 1.2914, lon: 103.8640),
        Circuit(keyword: "Singapore", lat: 1.2914, lon: 103.8640),
        Circuit(keyword: "Austin", lat: 30.1328, lon: -97.6411),
        Circuit(keyword: "Mexico", lat: 19.4042, lon: -99.0907),
        Circuit(keyword: "México", lat: 19.4042, lon: -99.0907),
        Circuit(keyword: "Interlagos", lat: -23.7014, lon: -46.6970),
        Circuit(keyword: "Sao Paulo", lat: -23.7014, lon: -46.6970),
        Circuit(keyword: "São Paulo", lat: -23.7014, lon: -46.6970),
        Circuit(keyword: "Las Vegas", lat: 36.1147, lon: -115.1728),
        Circuit(keyword: "Losail", lat: 25.4900, lon: 51.4542),
        Circuit(keyword: "Qatar", lat: 25.4900, lon: 51.4542),
        Circuit(keyword: "Yas Marina", lat: 24.4672, lon: 54.6031),
        Circuit(keyword: "Abu Dhabi", lat: 24.4672, lon: 54.6031),

        // --- Resistencia / otras series recurrentes ---
        Circuit(keyword: "Daytona", lat: 29.1853, lon: -81.0697),
        Circuit(keyword: "Indianápolis", lat: 39.7950, lon: -86.2347),
        Circuit(keyword: "Indianapolis", lat: 39.7950, lon: -86.2347),
        Circuit(keyword: "Le Mans", lat: 47.9500, lon: 0.2231),
        Circuit(keyword: "Nürburgring", lat: 50.3356, lon: 6.9475),
        Circuit(keyword: "Nurburgring", lat: 50.3356, lon: 6.9475),
        Circuit(keyword: "Sebring", lat: 27.4547, lon: -81.3487),
        Circuit(keyword: "Watkins Glen", lat: 42.3369, lon: -76.9272),
        Circuit(keyword: "Road Atlanta", lat: 34.1500, lon: -83.8158),
        Circuit(keyword: "Fuji", lat: 35.3717, lon: 138.9264),
        Circuit(keyword: "Bahréin", lat: 26.0325, lon: 50.5106),
        Circuit(keyword: "Bahrein", lat: 26.0325, lon: 50.5106)
    ]

    /// Busca el circuito por palabra clave en el título del evento. `nil` si no hay ninguno
    /// conocido todavía — la UI debe ocultar el clima en ese caso, nunca inventar uno.
    public static func find(eventFullTitle: String) -> (lat: Double, lon: Double)? {
        circuits.first { eventFullTitle.localizedCaseInsensitiveContains($0.keyword) }
            .map { ($0.lat, $0.lon) }
    }
}
