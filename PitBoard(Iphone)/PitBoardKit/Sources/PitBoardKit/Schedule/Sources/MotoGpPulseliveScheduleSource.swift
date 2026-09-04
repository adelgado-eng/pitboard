import Foundation

/// motogp.com es una aplicación en JavaScript sin datos en el HTML servido, pero por
/// debajo usa una API pública propia (api.pulselive.motogp.com) — equivalente exacto de
/// `MotoGpPulseliveScheduleSource.kt`. Cada fin de semana real de Gran Premio viene
/// marcado con `kind: "GP"` y trae un array `broadcasts` con TODAS las sesiones de las 3
/// clases (MotoGP/Moto2/Moto3) — se filtra por `category.acronym` y `type == "SESSION"`
/// para quedarnos solo con las de una clase.
///
/// Las 3 clases comparten esta misma clase con 3 instancias distintas (acrónimos reales
/// de la API: "MGP" MotoGP, "MT2" Moto2, "MT3" Moto3 — ver `RaceScheduleRepository`).
public final class MotoGpPulseliveScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries
    private let categoryAcronym: String

    public init(series: RaceSeries = .motoGp, classCode: String = "MGP") {
        self.series = series
        self.categoryAcronym = classCode
    }

    public func fetch() async throws -> [EventDraft] {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let url = "https://api.pulselive.motogp.com/motogp/v1/events?seasonYear=\(year)"
        let json = try await HTTPClient.fetchHTML(url)
        return try parseJSON(json)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // filtrado por tipo de evento ("GP", sin tests ni presentaciones) y por clase
    // (acrónimo MGP/MT2/MT3) contra un fixture JSON real sin red — ver
    // MotoGpPulseliveScheduleSourceTests.
    func parseJSON(_ json: String) throws -> [EventDraft] {
        let events = try JSONDecoder().decode([PulseliveEvent].self, from: Data(json.utf8))
        return events
            .filter { $0.kind == "GP" }
            .flatMap { sessionsForEvent($0) }
    }

    private func sessionsForEvent(_ event: PulseliveEvent) -> [EventDraft] {
        let circuitName = event.circuit?.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let eventKey: String = {
            if let hashtag = event.hashtag?.trimmingCharacters(in: .whitespacesAndNewlines), hashtag.hasPrefix("#") {
                let stripped = String(hashtag.dropFirst())
                if !stripped.isEmpty { return stripped }
            }
            return circuitName
        }()

        return (event.broadcasts ?? [])
            .enumerated()
            .compactMap { index, broadcast -> EventDraft? in
                guard broadcast.type == "SESSION", broadcast.category?.acronym == categoryAcronym else { return nil }
                guard let dateStart = broadcast.dateStart, let startTimeUtc = Self.parseInstant(dateStart) else { return nil }

                return EventDraft(
                    series: series,
                    uid: "\(series.rawValue)-\(eventKey)-\(index)",
                    fullTitle: "\(series.displayName) - \(circuitName) - \(sessionLabel(broadcast))",
                    startTimeUtc: startTimeUtc,
                    timeZoneId: nil,
                    inferredBadge: badgeFor(broadcast)
                )
            }
    }

    private func sessionLabel(_ broadcast: PulseliveBroadcast) -> String {
        if let name = broadcast.name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            return name
        }
        return broadcast.shortname ?? ""
    }

    private func badgeFor(_ broadcast: PulseliveBroadcast) -> String {
        let name = (broadcast.name ?? "").lowercased()
        switch broadcast.kind {
        case "PRACTICE", "WARM_UP":
            return SessionBadgeType.practice.rawValue
        case "QUALIFYING":
            return SessionBadgeType.qualy.rawValue
        case "RACE":
            return name.contains("sprint") ? SessionBadgeType.sprint.rawValue : SessionBadgeType.race.rawValue
        default:
            return SessionBadgeType.other.rawValue
        }
    }

    /// La API mezcla "-0300" (sin dos puntos) y "-03:00" (con dos puntos) para el mismo
    /// campo según la sesión — se prueban ambos patrones.
    private static func parseInstant(_ raw: String) -> Date? {
        for format in ["yyyy-MM-dd'T'HH:mm:ssXX", "yyyy-MM-dd'T'HH:mm:ssXXX"] {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = format
            if let date = formatter.date(from: raw) { return date }
        }
        return nil
    }
}

private struct PulseliveEvent: Decodable {
    let kind: String?
    let hashtag: String?
    let circuit: PulseliveCircuit?
    let broadcasts: [PulseliveBroadcast]?
}

private struct PulseliveCircuit: Decodable {
    let name: String?
}

private struct PulseliveBroadcast: Decodable {
    let type: String?
    let kind: String?
    let shortname: String?
    let name: String?
    let dateStart: String?
    let category: PulseliveCategory?

    enum CodingKeys: String, CodingKey {
        case type, kind, shortname, name, category
        case dateStart = "date_start"
    }
}

private struct PulseliveCategory: Decodable {
    let acronym: String?
}
