import Foundation
import SwiftSoup

/// Pilotos por coche para WEC y Le Mans Cup — fuente COMPARTIDA porque ambas webs
/// (fiawec.com y lemanscup.com) usan exactamente la misma plantilla (mismo organizador,
/// ACO). Equivalente exacto de `AcoCarDriversSource.kt`. No conforma `StandingsSource`
/// (igual que en Android): esto alimenta `CarDriverModel`, no `StandingModel`.
///
/// HONESTO sobre el coste: hace falta una petición por coche además del listado — WEC
/// tiene ~35 coches, Le Mans Cup ~45. Se visitan con concurrencia acotada (8 a la vez).
/// Un fallo al visitar la ficha de un coche concreto solo deja a ESE coche sin pilotos,
/// nunca tumba la sincronización completa.
public final class AcoCarDriversSource: @unchecked Sendable {
    private let category: StandingsCategory
    /// Página con todos los coches de la temporada — fiawec.com/en/page/grid o
    /// lemanscup.com/en/car/{año}.
    private let listingUrl: String
    /// Texto del badge de clase (`span.fs-11`) a StandingsClass — se prueban en orden,
    /// las coincidencias más específicas ("LMP3 Pro/Am") deben ir antes que las
    /// genéricas ("LMP3").
    private let classMatchers: [(StandingsClass, (String) -> Bool)]

    public init(category: StandingsCategory, listingUrl: String, classMatchers: [(StandingsClass, (String) -> Bool)]) {
        self.category = category
        self.listingUrl = listingUrl
        self.classMatchers = classMatchers
    }

    // internal (no private): expuesta a test — ver parseCarRefs arriba.
    struct CarRef {
        var standingsClass: StandingsClass
        var carUrl: String
    }

    public func fetch(nowUtc: Date) async throws -> [CarDriverDraft] {
        let listingHtml = try await HTTPClient.fetchHTML(listingUrl)
        let carRefs = try parseCarRefs(listingHtml)

        let results = await withBoundedConcurrency(carRefs, limit: Self.concurrency) { ref in
            await self.fetchCarDrivers(carUrl: ref.carUrl, standingsClass: ref.standingsClass, nowUtc: nowUtc)
        }
        return results.flatMap { $0 }
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear la
    // resolución de clase por el badge (span.fs-11) y la extracción de la url de cada
    // coche contra un fixture HTML sin red — ver AcoCarDriversSourceTests. CarRef pasa a
    // internal por el mismo motivo.
    func parseCarRefs(_ html: String) throws -> [CarRef] {
        let listingDoc = try SwiftSoup.parse(html, listingUrl)
        let cards = try listingDoc.select("div.card-team").array()
        return try cards.compactMap { card -> CarRef? in
            let classText = try card.select("span.fs-11").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard let standingsClass = classMatchers.first(where: { $0.1(classText) })?.0 else { return nil }
            guard let carUrl = try card.select("a.stretched-link").first()?.absUrl("href"), !carUrl.isEmpty else { return nil }
            return CarRef(standingsClass: standingsClass, carUrl: carUrl)
        }
    }

    private func fetchCarDrivers(carUrl: String, standingsClass: StandingsClass, nowUtc: Date) async -> [CarDriverDraft] {
        do {
            // El número de coche sale del propio tramo final de la URL de la ficha
            // (".../car/2026/35") — más fiable que un texto suelto.
            let trimmed = carUrl.hasSuffix("/") ? String(carUrl.dropLast()) : carUrl
            let carNumber = trimmed.components(separatedBy: "/").last ?? ""
            guard !carNumber.isEmpty, Int(carNumber) != nil else { return [] }

            let html = try await HTTPClient.fetchHTML(carUrl)
            return try parseCarPage(html, carUrl: carUrl, carNumber: carNumber, standingsClass: standingsClass, nowUtc: nowUtc)
        } catch {
            return []
        }
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetchCarDrivers() para poder
    // testear el parsing de la ficha de coche (foto real vía <img src>, sin el truco de
    // carga perezosa que sí usa imsa.com) contra un fixture HTML sin red.
    func parseCarPage(_ html: String, carUrl: String, carNumber: String, standingsClass: StandingsClass, nowUtc: Date) throws -> [CarDriverDraft] {
        let doc = try SwiftSoup.parse(html, carUrl)
        let cards = try doc.select("a.card-driver").array()
        return try cards.compactMap { card -> CarDriverDraft? in
            let name = try card.select("div.py-4").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !name.isEmpty else { return nil }
            let photoUrl = try card.select("img").first().flatMap { try? $0.absUrl("src") }.flatMap { $0.isEmpty ? nil : $0 }

            return CarDriverDraft(
                category: category,
                standingsClass: standingsClass,
                carNumber: carNumber,
                entryKey: TextNormalizer.normalize(name),
                name: name,
                photoUrl: photoUrl,
                updatedAtUtc: nowUtc
            )
        }
    }

    private static let concurrency = 8
}
