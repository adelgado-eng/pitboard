import Foundation
import SwiftSoup

/// f1academy.com es una SPA en Next.js, pero — a diferencia de motogp.com — sí deja los
/// datos de la temporada completa incrustados en el propio HTML servido, dentro del
/// script `#__NEXT_DATA__` (el mecanismo estándar de Next.js para hidratar la página sin
/// una llamada de red aparte). Equivalente exacto de `F1AcademyScheduleSource.kt`. Con
/// una sola petición a la página del calendario se obtienen TODAS las rondas y TODAS las
/// sesiones de la temporada actual, con hora ya incluida — no hace falta calcular el año
/// ni visitar una página por ronda.
public final class F1AcademyScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries = .f1Academy

    public init() {}

    public func fetch() async throws -> [EventDraft] {
        let url = "https://www.f1academy.com/Racing-Series/Calendar"
        let html = try await HTTPClient.fetchHTML(url)

        let doc = try SwiftSoup.parse(html)
        guard let script = try doc.select("script[id=__NEXT_DATA__]").first() else { return [] }
        let json = script.data()
        guard let data = json.data(using: .utf8) else { return [] }

        let root = try? JSONDecoder().decode(NextDataRoot.self, from: data)
        let races = root?.props?.pageProps?.pageData?.races ?? []

        return races.flatMap { sessionsForRace($0) }
    }

    private func sessionsForRace(_ race: F1AcademyRace) -> [EventDraft] {
        let circuitName = race.circuitShortName ?? race.circuitName ?? ""
        let round = race.roundNumber ?? 0

        var events: [EventDraft] = []
        for (index, session) in (race.sessions ?? []).enumerated() {
            guard let name = session.sessionName?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else { continue }
            guard let raw = session.sessionStartTime, let startTimeUtc = Self.parseInstant(raw) else { continue }

            events.append(EventDraft(
                series: .f1Academy,
                uid: "F1ACADEMY-R\(round)-\(index)",
                fullTitle: "\(RaceSeries.f1Academy.displayName) - \(circuitName) - \(name)",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeMatcher.match(name)
            ))
        }
        return events
    }

    static func parseInstant(_ raw: String) -> Date? {
        let withFractional = ISO8601DateFormatter()
        withFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFractional.date(from: raw) { return date }
        let standard = ISO8601DateFormatter()
        standard.formatOptions = [.withInternetDateTime]
        return standard.date(from: raw)
    }
}

struct NextDataRoot: Decodable {
    let props: NextDataProps?
}

struct NextDataProps: Decodable {
    let pageProps: NextDataPageProps?
}

struct NextDataPageProps: Decodable {
    let pageData: F1AcademySeasonData?
}

struct F1AcademySeasonData: Decodable {
    let races: [F1AcademyRace]?

    enum CodingKeys: String, CodingKey {
        case races = "Races"
    }
}

struct F1AcademyRace: Decodable {
    let roundNumber: Int?
    let circuitName: String?
    let circuitShortName: String?
    let sessions: [F1AcademySession]?

    enum CodingKeys: String, CodingKey {
        case roundNumber = "RoundNumber"
        case circuitName = "CircuitName"
        case circuitShortName = "CircuitShortName"
        case sessions = "Sessions"
    }
}

struct F1AcademySession: Decodable {
    let sessionName: String?
    let sessionStartTime: String?

    enum CodingKeys: String, CodingKey {
        case sessionName = "SessionName"
        case sessionStartTime = "SessionStartTime"
    }
}
