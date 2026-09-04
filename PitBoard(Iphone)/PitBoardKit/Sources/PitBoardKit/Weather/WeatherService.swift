import Foundation

/// Clima de un circuito conocido — resultado de `WeatherService.fetch`. Equivalente exacto de
/// `WeatherResult` en Android: nunca es "clima" a secas, la UI necesita distinguir "no sabemos
/// dónde está este circuito todavía" de "no hay previsión tan lejos" de "hubo un fallo de red".
public enum WeatherResult: Equatable, Sendable {
    case available(tempCelsius: Double, rainProbabilityPercent: Int)
    case circuitUnknown
    case tooFarAhead
    case error
}

private struct OpenMeteoResponse: Decodable {
    struct Hourly: Decodable {
        let time: [String]
        let temperatureC: [Double]
        let rainProbability: [Int]

        enum CodingKeys: String, CodingKey {
            case time
            case temperatureC = "temperature_2m"
            case rainProbability = "precipitation_probability"
        }
    }
    let hourly: Hourly
}

/// Clima del circuito para un evento, vía Open-Meteo (api.open-meteo.com) — gratis, sin clave
/// de API. Equivalente exacto de `WeatherRepository.kt`. Se pide bajo demanda, solo cuando el
/// usuario abre el detalle de un evento concreto (EventDetailsSheet) — nunca para toda la
/// lista de golpe.
public enum WeatherService {

    /// La previsión gratuita de Open-Meteo cubre unos 16 días vista.
    private static let maxDaysAhead = 15

    public static func fetch(eventFullTitle: String, startTimeUtc: Date, now: Date = Date()) async -> WeatherResult {
        guard let coords = CircuitCoordinates.find(eventFullTitle: eventFullTitle) else {
            return .circuitUnknown
        }

        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC") ?? .current

        let today = utc.startOfDay(for: now)
        let eventDay = utc.startOfDay(for: startTimeUtc)
        guard let daysAhead = utc.dateComponents([.day], from: today, to: eventDay).day,
              (0...maxDaysAhead).contains(daysAhead) else {
            return .tooFarAhead
        }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        dateFormatter.timeZone = TimeZone(identifier: "UTC")
        let dateString = dateFormatter.string(from: startTimeUtc)

        let url = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=\(coords.lat)&longitude=\(coords.lon)"
            + "&hourly=temperature_2m,precipitation_probability"
            + "&timezone=UTC"
            + "&start_date=\(dateString)&end_date=\(dateString)"

        do {
            let response = try await HTTPClient.fetchJSON(url, as: OpenMeteoResponse.self)
            guard !response.hourly.time.isEmpty else { return .tooFarAhead }

            // La hora exacta del evento no siempre coincide con una marca de la previsión
            // (que viene en punto en punto) — se usa la más cercana.
            let isoFormatter = DateFormatter()
            isoFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
            isoFormatter.timeZone = TimeZone(identifier: "UTC")

            var bestIndex = 0
            var bestDiff = Double.greatestFiniteMagnitude
            for (index, timeString) in response.hourly.time.enumerated() {
                guard let slot = isoFormatter.date(from: timeString) else { continue }
                let diff = abs(slot.timeIntervalSince(startTimeUtc))
                if diff < bestDiff {
                    bestDiff = diff
                    bestIndex = index
                }
            }

            guard response.hourly.temperatureC.indices.contains(bestIndex),
                  response.hourly.rainProbability.indices.contains(bestIndex) else {
                return .error
            }

            return .available(
                tempCelsius: response.hourly.temperatureC[bestIndex],
                rainProbabilityPercent: response.hourly.rainProbability[bestIndex]
            )
        } catch {
            return .error
        }
    }
}
