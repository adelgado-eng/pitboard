import Foundation
import SwiftSoup

/// nascar.com es JavaScript puro para el listado de la tabla, así que la tabla "de
/// autoridad" viene de espn.com/racing/standings — equivalente exacto de
/// `NascarStandingsSource.kt`. Envuelve `OfficialRosterStandingsSource`.
///
/// La foto sale DIRECTAMENTE de la propia tabla de espn.com, sin petición extra: cada
/// fila trae un enlace `/racing/driver/_/id/{id}/{slug}`, y ese id compuesto con el CDN
/// de fotos de ESPN da una foto de estudio fiable — nascar.com resultó poco fiable
/// página a página (fotos desactualizadas o 404 para pilotos de temporada parcial).
public final class NascarStandingsSource: StandingsSource, @unchecked Sendable {
    // 04/09/2026 (Fase 1 del diagnóstico): internal (no private) para poder testear el
    // extractor de foto de ESPN a través de OfficialRosterStandingsSource.parseRosterHTML
    // — ver OfficialRosterStandingsSourceTests.
    let inner: OfficialRosterStandingsSource

    public init() {
        inner = OfficialRosterStandingsSource(
            category: .nascarCup,
            rosterUrl: "https://www.espn.com/racing/standings",
            driverDbSlug: "nascar-sprint-cup-series",
            rosterPhotoUrlExtractor: { nameCell in
                guard let href = try? nameCell.select("a").first()?.attr("href") else { return nil }
                guard let espnId = NascarStandingsSource.espnId(from: href) else { return nil }
                // 04/09/2026: 350x350 -> 500x500 — comprobado a mano (Hamlin, Blaney,
                // Reddick, Larson, Elliott...) que ESPN mantiene esta foto al día
                // temporada a temporada, pero a 350px se veía pequeña/poco nítida en el
                // avatar grande de CategoryStandingsScreen y en la vista previa a
                // pantalla completa.
                return "https://a.espncdn.com/combiner/i?img=/i/headshots/rpm/players/full/\(espnId).png&w=500&h=500"
            }
        )
    }

    public var category: StandingsCategory { inner.category }
    public func fetch(nowUtc: Date) async throws -> [StandingDraft] { try await inner.fetch(nowUtc: nowUtc) }

    private static let espnIdRegex = try! NSRegularExpression(pattern: "/id/(\\d+)/")

    private static func espnId(from href: String) -> String? {
        let range = NSRange(href.startIndex..<href.endIndex, in: href)
        guard let match = espnIdRegex.firstMatch(in: href, range: range), match.numberOfRanges > 1,
              let group = Range(match.range(at: 1), in: href) else {
            return nil
        }
        return String(href[group])
    }
}
