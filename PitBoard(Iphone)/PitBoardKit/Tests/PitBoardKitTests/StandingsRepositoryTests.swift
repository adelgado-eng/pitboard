import XCTest
import SwiftData
@testable import PitBoardKit

private struct FakeStandingsSource: StandingsSource, Sendable {
    let category: StandingsCategory
    let result: @Sendable () throws -> [StandingDraft]
    func fetch(nowUtc: Date) async throws -> [StandingDraft] { try result() }
}

/// HONESTO: `elmsDriversSource`/`imsaStandingsSource` no exponen ningún punto de
/// inyección (sus URLs están fijas dentro de la clase, a diferencia de
/// `wecDriversSource`/`leMansCupDriversSource`, que sí reciben `listingUrl` por
/// parámetro) — aquí se les pasa una `AcoCarDriversSource` con `listingUrl` vacía para
/// WEC/Le Mans Cup, que falla al instante sin tocar la red (`URL(string: "")` es `nil`),
/// pero `ElmsDriversSource`/`ImsaStandingsSource` reales SÍ intentan una petición de red
/// real en este test. `StandingsRepository.syncAll()` ya trata esas dos ramas como
/// aisladas (un fallo ahí no tumba nada más), así que el test sigue siendo válido sin
/// red — solo puede tardar un poco más mientras esa petición falla.
final class StandingsRepositoryTests: XCTestCase {

    private func makeRepository(sources: [any StandingsSource], container: ModelContainer) -> StandingsRepository {
        StandingsRepository(
            sources: sources,
            elmsDriversSource: ElmsDriversSource(),
            imsaStandingsSource: ImsaStandingsSource(),
            wecDriversSource: AcoCarDriversSource(category: .wec, listingUrl: "", classMatchers: []),
            leMansCupDriversSource: AcoCarDriversSource(category: .lemansCup, listingUrl: "", classMatchers: []),
            modelContainer: container
        )
    }

    func testPartialFailureIsolation() async {
        let container = makeInMemoryContainer()
        let okSource = FakeStandingsSource(category: .f1) {
            [StandingDraft(category: .f1, type: .driver, entrantKey: "k1", position: 1, name: "Piloto", team: "Equipo", points: 100, updatedAtUtc: Date())]
        }
        let failingSource = FakeStandingsSource(category: .motoGp) {
            throw URLError(.timedOut)
        }

        let repository = makeRepository(sources: [okSource, failingSource], container: container)
        let result = await repository.syncAll()

        XCTAssertTrue(result.succeeded.contains(.f1))
        XCTAssertTrue(result.failed.contains(.motoGp))
    }

    func testEmptyResultCountsAsFailure() async {
        let container = makeInMemoryContainer()
        let emptySource = FakeStandingsSource(category: .f1) { [] }

        let repository = makeRepository(sources: [emptySource], container: container)
        let result = await repository.syncAll()

        XCTAssertTrue(result.failed.contains(.f1))
    }

    func testSuccessfulSourcePersistsRowsAndReplacesStaleOnes() async {
        let container = makeInMemoryContainer()
        let context = ModelContext(container)
        context.insert(StandingModel(category: .f1, standingsClass: .overall, type: .driver, entrantKey: "stale", position: 1, name: "Ya no puntúa", team: "", points: 0, updatedAtUtc: Date()))
        try! context.save()

        let freshSource = FakeStandingsSource(category: .f1) {
            [StandingDraft(category: .f1, type: .driver, entrantKey: "fresh", position: 1, name: "Piloto Actual", team: "Equipo", points: 200, updatedAtUtc: Date())]
        }

        let repository = makeRepository(sources: [freshSource], container: container)
        _ = await repository.syncAll()

        let stored = try! ModelContext(container).fetch(FetchDescriptor<StandingModel>())
        // Solo se sustituye la categoría F1 sincronizada; la fila "stale" de esa misma
        // categoría desaparece (reemplazo completo, ver `replaceStandings`).
        XCTAssertEqual(stored.filter { $0.category == .f1 }.map(\.entrantKey), ["fresh"])
    }

    func testFailedCategoryLeavesPreviousCacheIntact() async {
        let container = makeInMemoryContainer()
        let context = ModelContext(container)
        context.insert(StandingModel(category: .motoGp, standingsClass: .overall, type: .driver, entrantKey: "cached", position: 1, name: "Última sync buena", team: "", points: 150, updatedAtUtc: Date()))
        try! context.save()

        let failingSource = FakeStandingsSource(category: .motoGp) { throw URLError(.timedOut) }

        let repository = makeRepository(sources: [failingSource], container: container)
        _ = await repository.syncAll()

        let stored = try! ModelContext(container).fetch(FetchDescriptor<StandingModel>())
        // Filtrado por categoría (igual que en testSuccessfulSourcePersistsRowsAndReplacesStaleOnes):
        // imsaStandingsSource/wecDriversSource/leMansCupDriversSource hacen una petición de
        // red REAL en este test (ver comentario de la clase) — en un entorno con internet
        // (como el runner de CI) esa petición puede tener éxito y guardar filas de IMSA
        // reales, que no son el objeto de este test y no deben hacerlo fallar.
        XCTAssertEqual(stored.filter { $0.category == .motoGp }.map(\.entrantKey), ["cached"])
    }
}
