import Foundation

/// Equivalente de `kotlinx.coroutines.sync.Semaphore(n).withPermit { }` — varias fuentes
/// (roster oficial, driverdb, coches de resistencia, IMSA) necesitan pedir docenas de
/// páginas de perfil/equipo en paralelo, pero con un tope de peticiones en vuelo a la vez
/// para no parecer un scraper agresivo (y no golpear límites 429/403 del servidor).
///
/// Ejecuta `operation` sobre todos los `elements`, como mucho `limit` a la vez, y
/// devuelve los resultados EN EL MISMO ORDEN que `elements` — mismo comportamiento que
/// `elements.map { async { gate.withPermit { ... } } }.awaitAll()` en Kotlin.
public func withBoundedConcurrency<Element: Sendable, Result: Sendable>(
    _ elements: [Element],
    limit: Int,
    operation: @escaping @Sendable (Element) async -> Result
) async -> [Result] {
    guard !elements.isEmpty else { return [] }

    return await withTaskGroup(of: (Int, Result).self) { group in
        var results = [Result?](repeating: nil, count: elements.count)
        var nextIndex = 0

        func addNext() {
            guard nextIndex < elements.count else { return }
            let index = nextIndex
            let element = elements[index]
            nextIndex += 1
            group.addTask {
                (index, await operation(element))
            }
        }

        for _ in 0..<min(limit, elements.count) {
            addNext()
        }

        while let (index, result) = await group.next() {
            results[index] = result
            addNext()
        }

        return results.compactMap { $0 }
    }
}
