import Foundation
import SwiftSoup

/// Los pilotos de cada coche de ELMS (3 por coche, a veces 2 o 4), para el desplegable
/// que sale al tocar un coche en Clasificaciones — dato que `ElmsStandingsSource` nunca
/// trae (su tabla de origen es de EQUIPOS, no de pilotos). Equivalente exacto de
/// `ElmsDriversSource.kt`.
///
/// Fuente: europeanlemansseries.com/en/page/drivers — una única página con las 4 clases
/// (LMP2, LMP2 PRO/AM, LMP3, LMGT3), cada una con un `<h2 class="h3 text-center">`
/// seguido de sus tarjetas `<div class="card-driver">`. Como la tarjeta siempre aparece
/// DESPUÉS de su `<h2>` de clase en el árbol del documento, basta con recorrerlo en
/// orden y recordar la última clase vista.
public final class ElmsDriversSource: @unchecked Sendable {

    private let pageUrl = "https://www.europeanlemansseries.com/en/page/drivers"

    private let classMatchers: [(StandingsClass, (String) -> Bool)] = [
        (.lmp2, { t in t.contains("LMP2") && !t.contains("PRO") }),
        (.lmp2ProAm, { t in t.contains("LMP2") && t.contains("PRO") }),
        (.lmp3, { t in t.contains("LMP3") }),
        (.lmgt3, { t in t.contains("LMGT3") || t.contains("GT3") })
    ]

    public init() {}

    public func fetch(nowUtc: Date) async throws -> [CarDriverDraft] {
        let html = try await HTTPClient.fetchHTML(pageUrl)
        let doc = try SwiftSoup.parse(html, pageUrl)

        var currentClass: StandingsClass?
        var rows: [CarDriverDraft] = []

        let elements = try doc.select("h2.h3.text-center, div.card-driver").array()
        for element in elements {
            if element.tagName() == "h2" {
                let text = try element.text().uppercased()
                currentClass = classMatchers.first { $0.1(text) }?.0
                continue
            }
            guard let standingsClass = currentClass else { continue }
            if let row = try parseDriverCard(element, standingsClass: standingsClass, nowUtc: nowUtc) {
                rows.append(row)
            }
        }
        return rows
    }

    private func parseDriverCard(_ card: Element, standingsClass: StandingsClass, nowUtc: Date) throws -> CarDriverDraft? {
        let name = try card.select("div.driver-name").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !name.isEmpty else { return nil }

        // "PROTON COMPETITION #9" -> el número de coche es lo único que se necesita.
        let teamText = try card.select("div.driver-team").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard let carNumber = Self.carNumberSuffix(in: teamText) else { return nil }

        let photoUrl = try card.select("div.driver-thumb img").first().flatMap { try? $0.absUrl("src") }.flatMap { $0.isEmpty ? nil : $0 }

        return CarDriverDraft(
            category: .elms,
            standingsClass: standingsClass,
            carNumber: carNumber,
            entryKey: TextNormalizer.normalize(name),
            name: name,
            photoUrl: photoUrl,
            updatedAtUtc: nowUtc
        )
    }

    private static func carNumberSuffix(in text: String) -> String? {
        guard let range = text.range(of: "#\\s*(\\d+)\\s*$", options: .regularExpression) else { return nil }
        let match = String(text[range])
        return match.trimmingCharacters(in: CharacterSet(charactersIn: "# "))
    }
}
