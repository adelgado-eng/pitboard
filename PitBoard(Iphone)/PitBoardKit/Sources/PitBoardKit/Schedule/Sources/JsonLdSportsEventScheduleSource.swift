import Foundation
import SwiftSoup

/// Fuente genérica para las webs que publican cada ronda con datos estructurados
/// `schema.org/SportsEvent` (bloque `<script type="application/ld+json">` con un array
/// `subEvent`, cada uno con `name` tipo "Practice - Australian Grand Prix" y `startDate`
/// en ISO-8601 con offset) — equivalente exacto de `JsonLdSportsEventScheduleSource.kt`.
/// La reutilizan F2, F3, ELMS y WEC y Le Mans Cup (ver `RaceScheduleRepository.swift`).
///
/// Funciona en dos pasos: 1) se lee la página de listado de la temporada y se sacan los
/// enlaces a cada ronda (empiezan por `roundHrefPrefixTemplate`, con el año ya
/// sustituido); 2) se visita cada ronda y se lee su JSON-LD.
///
/// HONESTO: si el sitio cambia el marcado del listado o dejara de usar JSON-LD, esta
/// fuente empieza a devolver listas vacías (fallo silencioso y aislado, ver
/// `RaceScheduleRepository`) — no hay tabla HTML de la que caer hacia atrás.
public final class JsonLdSportsEventScheduleSource: RaceScheduleSource, @unchecked Sendable {
    public let series: RaceSeries
    private let baseUrl: String
    /// URL de listado de la temporada, con "{year}" como marcador.
    private let listingUrlTemplate: String
    /// Prefijo del href de cada ronda dentro del listado, con "{year}" como marcador.
    private let roundHrefPrefixTemplate: String
    /// Fragmentos que, si aparecen en el slug de la ronda, la descartan (ej. "test").
    private let excludeSlugContaining: [String]

    public init(
        series: RaceSeries,
        baseUrl: String,
        listingUrlTemplate: String,
        roundHrefPrefixTemplate: String,
        excludeSlugContaining: [String] = []
    ) {
        self.series = series
        self.baseUrl = baseUrl
        self.listingUrlTemplate = listingUrlTemplate
        self.roundHrefPrefixTemplate = roundHrefPrefixTemplate
        self.excludeSlugContaining = excludeSlugContaining
    }

    public func fetch() async throws -> [EventDraft] {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        let listingUrl = listingUrlTemplate.replacingOccurrences(of: "{year}", with: String(year))
        let roundHrefPrefix = roundHrefPrefixTemplate.replacingOccurrences(of: "{year}", with: String(year))

        let listingHtml = try await HTTPClient.fetchHTML(listingUrl)
        let listingDoc = try SwiftSoup.parse(listingHtml, baseUrl)

        let anchors = try listingDoc.select("a[href]").array()
        var seen = Set<String>()
        var roundUrls: [String] = []
        for anchor in anchors {
            let href = try anchor.attr("href")
            guard href.hasPrefix(roundHrefPrefix) else { continue }
            guard let absolute = try? anchor.absUrl("href"), !absolute.isEmpty else { continue }
            guard seen.insert(absolute).inserted else { continue }
            let lowered = absolute.lowercased()
            if excludeSlugContaining.contains(where: { lowered.contains($0.lowercased()) }) { continue }
            roundUrls.append(absolute)
        }

        var events: [EventDraft] = []
        for roundUrl in roundUrls {
            if let sessions = try? await sessionsForRound(roundUrl) {
                events.append(contentsOf: sessions)
            }
        }
        return events
    }

    private func sessionsForRound(_ roundUrl: String) async throws -> [EventDraft] {
        let html = try await HTTPClient.fetchHTML(roundUrl)
        let doc = try SwiftSoup.parse(html, roundUrl)

        let scripts = try doc.select("script[type=application/ld+json]").array()
        var event: JsonLdSportsEvent?
        for script in scripts {
            let json = script.data()
            guard let data = json.data(using: .utf8),
                  let decoded = try? JSONDecoder().decode(JsonLdSportsEvent.self, from: data) else { continue }
            if let sub = decoded.subEvent, !sub.isEmpty {
                event = decoded
                break
            }
        }
        guard let event else { return [] }

        let trimmed = roundUrl.hasSuffix("/") ? String(roundUrl.dropLast()) : roundUrl
        let slug = trimmed.components(separatedBy: "/").last ?? trimmed

        // El nombre del subEvent trae "Sesión - Nombre del Gran Premio" — la parte de
        // después del guion es más corta y legible que el "name" del evento raíz (que en
        // fiaformula2/3.com viene con el título completo del GP, patrocinador incluido).
        let subEvents = event.subEvent ?? []
        var roundName = ""
        for sub in subEvents {
            if let name = sub.name, let range = name.range(of: " - ") {
                let candidate = String(name[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !candidate.isEmpty {
                    roundName = candidate
                    break
                }
            }
        }
        if roundName.isEmpty {
            roundName = event.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }
        let circuitName = event.location?.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        var result: [EventDraft] = []
        for (index, sub) in subEvents.enumerated() {
            guard let rawName = sub.name?.trimmingCharacters(in: .whitespacesAndNewlines), !rawName.isEmpty else { continue }
            guard let raw = sub.startDate, let startTimeUtc = Self.parseInstant(raw) else { continue }
            let label: String
            if let range = rawName.range(of: " - ") {
                let before = String(rawName[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
                label = before.isEmpty ? rawName : before
            } else {
                label = rawName
            }

            result.append(EventDraft(
                series: series,
                uid: "\(series.rawValue)-\(slug)-\(index)",
                fullTitle: "\(series.displayName) - \(roundName) - \(circuitName) - \(label)",
                startTimeUtc: startTimeUtc,
                timeZoneId: nil,
                inferredBadge: SessionBadgeMatcher.match(label)
            ))
        }
        return result
    }

    static func parseInstant(_ iso: String) -> Date? {
        let withFractional = ISO8601DateFormatter()
        withFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFractional.date(from: iso) { return date }
        let standard = ISO8601DateFormatter()
        standard.formatOptions = [.withInternetDateTime]
        return standard.date(from: iso)
    }
}

/// Forma "evento con subEvent opcional" de schema.org/SportsEvent — también la reutiliza
/// `FormulaEScheduleSource` (mismo paquete en Android, mismo módulo aquí).
struct JsonLdSportsEvent: Decodable {
    let name: String?
    let startDate: String?
    let location: JsonLdLocation?
    let subEvent: [JsonLdSportsEvent]?
}

struct JsonLdLocation: Decodable {
    let name: String?
}
