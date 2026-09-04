import Foundation
import SwiftSoup

/// Utilidad compartida para excluir pilotos reserva/wildcard/test que no están en la
/// parrilla oficial de esta temporada pero sí aparecen en la tabla de puntos con 0 (o
/// pocos) puntos — equivalente exacto de `RosterNameFilter.kt`.
///
/// HONESTO: `fetchKnownNames` es una heurística — busca texto que "parece un nombre
/// propio" (dos o más palabras que empiezan en mayúscula) dentro de títulos, enlaces,
/// listas y negritas de la página de referencia. Si no encuentra nada reconocible,
/// devuelve un conjunto vacío y quien la llama debe tratarlo como "no filtrar nada" —
/// mejor un piloto reserva de más que perder la clasificación completa.
public enum RosterNameFilter {

    private static let nameRegex = try! NSRegularExpression(
        pattern: "^\\p{Lu}[\\p{L}'-]+(\\s+\\p{Lu}[\\p{L}'-]+){1,3}$"
    )

    public static func fetchKnownNames(_ url: String) async throws -> Set<String> {
        let html = try await HTTPClient.fetchHTML(url)
        return try parseNames(html, url: url)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetchKnownNames() para poder
    // testear la heurística de "parece un nombre propio" contra un fixture HTML sin red
    // — ver RosterNameFilterTests.
    static func parseNames(_ html: String, url: String) throws -> Set<String> {
        let doc = try SwiftSoup.parse(html, url)
        let candidates = try doc.select("h1, h2, h3, h4, li, a, strong, b, td").array()

        var names = Set<String>()
        for element in candidates {
            let text = element.ownText().trimmingCharacters(in: .whitespacesAndNewlines)
            guard (4...40).contains(text.count) else { continue }
            let range = NSRange(text.startIndex..<text.endIndex, in: text)
            if nameRegex.firstMatch(in: text, range: range) != nil {
                names.insert(text)
            }
        }
        return names
    }

    /// true si `name` coincide (mejor esfuerzo, por subcadena normalizada) con algún
    /// nombre de `knownNames`, o si `knownNames` está vacío (no se pudo obtener la
    /// parrilla de referencia — en ese caso no se filtra nada).
    public static func isInRoster(_ name: String, knownNames: Set<String>) -> Bool {
        if knownNames.isEmpty { return true }
        let normalizedName = TextNormalizer.normalize(name)
        if normalizedName.isEmpty { return false }

        for known in knownNames {
            let normalizedKnown = TextNormalizer.normalize(known)
            if normalizedKnown.isEmpty { continue }
            if normalizedName == normalizedKnown
                || normalizedName.contains(normalizedKnown)
                || normalizedKnown.contains(normalizedName) {
                return true
            }
            // Iniciales con puntuación distinta entre las dos páginas ("AJ" vs "A. J.")
            // dejan un espacio en un sitio distinto tras quitar los signos — se compara
            // también sin espacios.
            let ca = normalizedName.replacingOccurrences(of: " ", with: "")
            let cb = normalizedKnown.replacingOccurrences(of: " ", with: "")
            if ca == cb || ca.contains(cb) || cb.contains(ca) { return true }
            // Último recurso: comparar solo apellidos (última palabra), y solo si tienen
            // 3+ letras, para no dar positivos por azar con apellidos cortos.
            let lastA = normalizedName.components(separatedBy: " ").last ?? ""
            let lastB = normalizedKnown.components(separatedBy: " ").last ?? ""
            if lastA.count >= 3 && lastA == lastB { return true }
        }
        return false
    }

    /// Aplica `isInRoster` a toda la lista, pero si el resultado se queda vacío mientras
    /// la lista original no lo estaba, no se fía del filtro y devuelve la lista sin
    /// filtrar — resguardo para cuando el filtro falla por completo (página de
    /// referencia es JavaScript puro, o los dos formatos de nombre no llegan a coincidir
    /// nunca), para dejar pasar como mucho algún piloto reserva de más antes que perder
    /// la clasificación entera.
    public static func filterKeepingReal<T>(
        _ rows: [T],
        knownNames: Set<String>,
        nameOf: (T) -> String
    ) -> [T] {
        if rows.isEmpty { return rows }
        let filtered = rows.filter { isInRoster(nameOf($0), knownNames: knownNames) }
        return filtered.isEmpty ? rows : filtered
    }
}
