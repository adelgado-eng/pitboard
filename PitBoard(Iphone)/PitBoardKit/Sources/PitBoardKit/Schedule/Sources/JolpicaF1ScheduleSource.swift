import Foundation

/// F1 es la única serie con una API JSON pública y gratuita para el calendario completo,
/// sesión por sesión: la API de Jolpica (sucesora de Ergast), en el alias "current" — no
/// hace falta el año en ningún sitio, sigue funcionando sin tocar código. Equivalente
/// exacto de `JolpicaF1ScheduleSource.kt`.
public final class JolpicaF1ScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries = .f1

    public init() {}

    public func fetch() async throws -> [EventDraft] {
        let json = try await HTTPClient.fetchHTML("https://api.jolpi.ca/ergast/f1/current.json")
        return try parseJSON(json)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // parsing contra un fixture JSON real sin red — ver JolpicaF1ScheduleSourceTests.
    func parseJSON(_ json: String) throws -> [EventDraft] {
        let response = try JSONDecoder().decode(JolpicaResponse.self, from: Data(json.utf8))
        let races = response.mrData?.raceTable?.races ?? []
        return races.flatMap { sessions(for: $0) }
    }

    private func sessions(for race: JolpicaRace) -> [EventDraft] {
        let round = Int(race.round) ?? 0
        let roundLabel = String(format: "R%02d", round)
        let roundName = race.raceName
        let circuitName = race.circuit?.circuitName ?? ""

        var sessions: [(label: String, badge: String, session: JolpicaSession)] = []
        if let s = race.firstPractice { sessions.append(("Libres 1", SessionBadgeType.practice.rawValue, s)) }
        if let s = race.secondPractice { sessions.append(("Libres 2", SessionBadgeType.practice.rawValue, s)) }
        if let s = race.thirdPractice { sessions.append(("Libres 3", SessionBadgeType.practice.rawValue, s)) }
        // Distintas temporadas han usado nombres distintos para la sesión de
        // clasificación del sprint — se aceptan los dos, solo uno vendrá presente cada vez.
        if let s = race.sprintQualifying { sessions.append(("Sprint Shootout", SessionBadgeType.qualy.rawValue, s)) }
        if let s = race.sprintShootout { sessions.append(("Sprint Shootout", SessionBadgeType.qualy.rawValue, s)) }
        if let s = race.sprint { sessions.append(("Sprint", SessionBadgeType.sprint.rawValue, s)) }
        if let s = race.qualifying { sessions.append(("Clasificación", SessionBadgeType.qualy.rawValue, s)) }
        if race.date != nil {
            sessions.append(("Carrera", SessionBadgeType.race.rawValue, JolpicaSession(date: race.date, time: race.time)))
        }

        return sessions.compactMap { entry -> EventDraft? in
            guard let startTimeUtc = Self.parseInstant(entry.session) else { return nil }
            return EventDraft(
                series: .f1,
                uid: "F1-\(roundLabel)-\(entry.label.replacingOccurrences(of: " ", with: ""))",
                fullTitle: "\(RaceSeries.f1.displayName) - \(roundName) - \(circuitName) - \(entry.label)",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: entry.badge
            )
        }
    }

    private static func parseInstant(_ session: JolpicaSession) -> Date? {
        guard let date = session.date else { return nil }
        let time = session.time ?? "00:00:00Z"
        let normalizedTime = time.hasSuffix("Z") ? time : time + "Z"
        let text = "\(date)T\(normalizedTime)"

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = formatter.date(from: text) { return parsed }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: text)
    }
}

// Sin `moshi-kotlin-codegen` en Android hacía falta reflection pura — en Swift no hace
// falta nada especial: `Codable` con `CodingKeys` explícitos basta para mapear las
// claves en mayúsculas de la API (ej. "MRData", "Circuit").
struct JolpicaResponse: Decodable {
    let mrData: JolpicaMrData?
    enum CodingKeys: String, CodingKey { case mrData = "MRData" }
}

struct JolpicaMrData: Decodable {
    let raceTable: JolpicaRaceTable?
    enum CodingKeys: String, CodingKey { case raceTable = "RaceTable" }
}

struct JolpicaRaceTable: Decodable {
    let races: [JolpicaRace]?
    enum CodingKeys: String, CodingKey { case races = "Races" }
}

struct JolpicaRace: Decodable {
    let round: String
    let raceName: String
    let circuit: JolpicaCircuit?
    let date: String?
    let time: String?
    let firstPractice: JolpicaSession?
    let secondPractice: JolpicaSession?
    let thirdPractice: JolpicaSession?
    let sprint: JolpicaSession?
    let sprintQualifying: JolpicaSession?
    let sprintShootout: JolpicaSession?
    let qualifying: JolpicaSession?

    enum CodingKeys: String, CodingKey {
        case round, raceName, date, time
        case circuit = "Circuit"
        case firstPractice = "FirstPractice"
        case secondPractice = "SecondPractice"
        case thirdPractice = "ThirdPractice"
        case sprint = "Sprint"
        case sprintQualifying = "SprintQualifying"
        case sprintShootout = "SprintShootout"
        case qualifying = "Qualifying"
    }
}

struct JolpicaCircuit: Decodable {
    let circuitName: String?
}

struct JolpicaSession: Decodable {
    let date: String?
    let time: String?
}
