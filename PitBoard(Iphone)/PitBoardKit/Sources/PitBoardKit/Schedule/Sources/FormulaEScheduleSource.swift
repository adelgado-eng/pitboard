import Foundation
import SwiftSoup

/// Fórmula E — equivalente exacto de `FormulaEScheduleSource.kt`. fiaformulae.com/en/calendar
/// sirve, ya renderizado en el HTML (no hace falta ejecutar JS), un único bloque
/// `<script type="application/ld+json">` con un `ItemList` de toda la temporada — cada
/// ronda es un `SportsEvent` con `startDate`/`location`, y algunas (las más próximas, con
/// horarios ya confirmados) traen además un `subEvent` con Free Practice/Qualifying/Race
/// por separado; las rondas más lejanas en el calendario solo traen la hora de la
/// carrera todavía.
///
/// No reutiliza `JsonLdSportsEventScheduleSource`: esa fuente espera un listado con un
/// enlace por ronda y visita cada ronda por separado — aquí toda la temporada vive en
/// una sola página y el `SportsEvent` de cada ronda no es la raíz del JSON-LD sino que
/// cuelga de `itemListElement[].item`. Sí reutiliza sus tipos `JsonLdSportsEvent`/
/// `JsonLdLocation` (mismo módulo), que ya representan exactamente esa forma "evento con
/// subEvent opcional".
public final class FormulaEScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries = .formulaE
    private let calendarUrl: String

    public init(calendarUrl: String = "https://www.fiaformulae.com/en/calendar") {
        self.calendarUrl = calendarUrl
    }

    public func fetch() async throws -> [EventDraft] {
        let html = try await HTTPClient.fetchHTML(calendarUrl)
        return try parseHTML(html)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear la
    // lectura del JSON-LD embebido contra un fixture HTML sin red — ver
    // FormulaEScheduleSourceTests.
    func parseHTML(_ html: String) throws -> [EventDraft] {
        let doc = try SwiftSoup.parse(html, calendarUrl)

        let scripts = try doc.select("script[type=application/ld+json]").array()
        var itemList: FormulaEItemList?
        for script in scripts {
            let json = script.data()
            guard let data = json.data(using: .utf8),
                  let decoded = try? JSONDecoder().decode(FormulaEItemList.self, from: data) else { continue }
            if let items = decoded.itemListElement, !items.isEmpty {
                itemList = decoded
                break
            }
        }
        guard let itemList else { return [] }

        var events: [EventDraft] = []
        for (index, listItem) in (itemList.itemListElement ?? []).enumerated() {
            guard let event = listItem.item else { continue }
            events.append(contentsOf: sessionsForEvent(event, index: index))
        }
        return events
    }

    private func sessionsForEvent(_ event: JsonLdSportsEvent, index: Int) -> [EventDraft] {
        let roundName = event.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let circuitName = event.location?.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let subEvents = event.subEvent ?? []

        if subEvents.isEmpty {
            // Ronda todavía sin desglose de sesiones publicado — se usa la propia
            // carrera (fecha/hora del SportsEvent raíz) como única sesión, mejor que
            // dejarla fuera.
            guard let raw = event.startDate, let startTimeUtc = JsonLdSportsEventScheduleSource.parseInstant(raw) else { return [] }
            return [EventDraft(
                series: series,
                uid: "\(series.rawValue)-\(index)-race",
                fullTitle: "\(series.displayName) - \(roundName) - \(circuitName) - Race",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeMatcher.match("Race")
            )]
        }

        var result: [EventDraft] = []
        for (subIndex, sub) in subEvents.enumerated() {
            guard let label = sub.name?.trimmingCharacters(in: .whitespacesAndNewlines), !label.isEmpty else { continue }
            guard let raw = sub.startDate, let startTimeUtc = JsonLdSportsEventScheduleSource.parseInstant(raw) else { continue }
            result.append(EventDraft(
                series: series,
                uid: "\(series.rawValue)-\(index)-\(subIndex)",
                fullTitle: "\(series.displayName) - \(roundName) - \(circuitName) - \(label)",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeMatcher.match(label)
            ))
        }
        return result
    }
}

struct FormulaEItemList: Decodable {
    let itemListElement: [FormulaEListItem]?
}

struct FormulaEListItem: Decodable {
    let item: JsonLdSportsEvent?
}
