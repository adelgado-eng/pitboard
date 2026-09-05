import Foundation
import SwiftData
import os

/// Sincroniza las 14 fuentes de `StandingsSource` EN PARALELO y de forma independiente,
/// más las 3 fuentes "de pilotos por coche" (ELMS/WEC/Le Mans Cup) y la rama aparte de
/// IMSA — equivalente exacto de `StandingsRepository.kt`. Si una fuente falla (web
/// caída, cambio de diseño, sin conexión puntual...), las demás se guardan igual. Si una
/// fuente falla o devuelve una lista vacía, su caché anterior en SwiftData se deja
/// intacta — nunca se sustituye por "nada".
public final class StandingsRepository: @unchecked Sendable {

    private static let logger = Logger(subsystem: "com.pitboard.app", category: "StandingsSync")

    private let sources: [any StandingsSource]
    private let elmsDriversSource: ElmsDriversSource
    private let imsaStandingsSource: ImsaStandingsSource
    /// WEC y Le Mans Cup comparten la misma fuente de pilotos por coche (ver
    /// `AcoCarDriversSource`) — a diferencia de IMSA, aquí el logo de equipo ya viene en
    /// la propia tabla de clasificación.
    private let wecDriversSource: AcoCarDriversSource
    private let leMansCupDriversSource: AcoCarDriversSource
    /// Inyectable (por defecto el store real, compartido vía App Group) para poder
    /// testear `syncAll()` contra un `ModelContainer` en memoria sin tocar disco.
    private let modelContainer: ModelContainer

    public init(
        sources: [any StandingsSource] = StandingsRepository.defaultSources,
        elmsDriversSource: ElmsDriversSource = ElmsDriversSource(),
        imsaStandingsSource: ImsaStandingsSource = ImsaStandingsSource(),
        wecDriversSource: AcoCarDriversSource = StandingsRepository.makeWecDriversSource(),
        leMansCupDriversSource: AcoCarDriversSource = StandingsRepository.makeLeMansCupDriversSource(),
        modelContainer: ModelContainer = AppDatabase.container
    ) {
        self.sources = sources
        self.elmsDriversSource = elmsDriversSource
        self.imsaStandingsSource = imsaStandingsSource
        self.wecDriversSource = wecDriversSource
        self.leMansCupDriversSource = leMansCupDriversSource
        self.modelContainer = modelContainer
    }

    public struct CategoryOutcome: Sendable {
        public var category: StandingsCategory
        public var ok: Bool
        /// Filas guardadas (0 si falló).
        public var rowCount: Int
        /// Motivo del fallo, listo para mostrar. nil si fue bien.
        public var detail: String?
    }

    public struct SyncResult: Sendable {
        public var outcomes: [CategoryOutcome]
        public var succeeded: [StandingsCategory] { outcomes.filter(\.ok).map(\.category) }
        public var failed: [StandingsCategory] { outcomes.filter { !$0.ok }.map(\.category) }
    }

    public func syncAll() async -> SyncResult {
        let nowUtc = Date()

        async let standingsResultsTask: [(StandingsCategory, Result<[StandingDraft], Error>)] = withTaskGroup(
            of: (StandingsCategory, Result<[StandingDraft], Error>).self
        ) { group in
            for source in sources {
                group.addTask {
                    do { return (source.category, .success(try await source.fetch(nowUtc: nowUtc))) }
                    catch { return (source.category, .failure(error)) }
                }
            }
            var collected: [(StandingsCategory, Result<[StandingDraft], Error>)] = []
            for await entry in group { collected.append(entry) }
            return collected
        }

        // Pilotos por coche de ELMS/WEC/Le Mans Cup: lanzadas junto a lo de arriba para
        // que un fallo aquí (web caída, cambio de maquetación) no retrase ni tumbe la
        // sync de las demás categorías.
        async let elmsDriversResult = Self.runCatching { try await self.elmsDriversSource.fetch(nowUtc: nowUtc) }
        async let wecDriversResult = Self.runCatching { try await self.wecDriversSource.fetch(nowUtc: nowUtc) }
        async let leMansCupDriversResult = Self.runCatching { try await self.leMansCupDriversSource.fetch(nowUtc: nowUtc) }
        // IMSA no encaja en el bucle genérico: su clasificación y sus pilotos por coche
        // salen de la MISMA visita a cada página de equipo.
        async let imsaResult = Self.runCatching { try await self.imsaStandingsSource.fetch(nowUtc: nowUtc) }

        let standingsResults = await standingsResultsTask
        let context = ModelContext(modelContainer)

        if case .success(let rows) = await elmsDriversResult {
            if !rows.isEmpty {
                replaceCarDrivers(category: .elms, rows: rows, in: context)
            } else {
                Self.logger.warning("ELMS drivers: la fuente respondió pero sin filas (0 resultados)")
            }
        } else if case .failure(let error) = await elmsDriversResult {
            Self.logger.error("ELMS drivers: fallo al obtener datos — \(String(describing: error))")
        }

        if case .success(let rows) = await wecDriversResult {
            if !rows.isEmpty {
                replaceCarDrivers(category: .wec, rows: rows, in: context)
            } else {
                Self.logger.warning("WEC drivers: la fuente respondió pero sin filas (0 resultados)")
            }
        } else if case .failure(let error) = await wecDriversResult {
            Self.logger.error("WEC drivers: fallo al obtener datos — \(String(describing: error))")
        }

        if case .success(let rows) = await leMansCupDriversResult {
            if !rows.isEmpty {
                replaceCarDrivers(category: .lemansCup, rows: rows, in: context)
            } else {
                Self.logger.warning("Le Mans Cup drivers: la fuente respondió pero sin filas (0 resultados)")
            }
        } else if case .failure(let error) = await leMansCupDriversResult {
            Self.logger.error("Le Mans Cup drivers: fallo al obtener datos — \(String(describing: error))")
        }

        let imsaOutcome: CategoryOutcome
        switch await imsaResult {
        case .success(let result):
            if result.standings.isEmpty {
                Self.logger.warning("IMSA: la fuente respondió pero sin filas (0 resultados)")
                imsaOutcome = CategoryOutcome(category: .imsa, ok: false, rowCount: 0, detail: Self.noDataFoundMessage)
            } else {
                replaceStandings(category: .imsa, rows: result.standings, in: context)
                // Los pilotos son mejor esfuerzo — si vinieran vacíos no se toca la caché
                // anterior, pero la clasificación de coches sí se guarda igual.
                if !result.carDrivers.isEmpty {
                    replaceCarDrivers(category: .imsa, rows: result.carDrivers, in: context)
                }
                imsaOutcome = CategoryOutcome(category: .imsa, ok: true, rowCount: result.standings.count, detail: nil)
            }
        case .failure(let error):
            Self.logger.error("IMSA: fallo al obtener datos — \(String(describing: error))")
            imsaOutcome = CategoryOutcome(category: .imsa, ok: false, rowCount: 0, detail: Self.friendlyReason(error))
        }

        var outcomes: [CategoryOutcome] = standingsResults.map { category, result in
            switch result {
            case .success(let rows):
                if rows.isEmpty {
                    Self.logger.warning("\(category.rawValue): la fuente respondió pero sin filas (0 resultados)")
                    return CategoryOutcome(category: category, ok: false, rowCount: 0, detail: Self.noDataFoundMessage)
                }
                replaceStandings(category: category, rows: rows, in: context)
                return CategoryOutcome(category: category, ok: true, rowCount: rows.count, detail: nil)
            case .failure(let error):
                Self.logger.error("\(category.rawValue): fallo al obtener datos — \(String(describing: error))")
                return CategoryOutcome(category: category, ok: false, rowCount: 0, detail: Self.friendlyReason(error))
            }
        }
        outcomes.append(imsaOutcome)

        do {
            try context.save()
        } catch {
            Self.logger.error("No se pudo guardar la sincronización de standings — \(String(describing: error))")
        }

        return SyncResult(outcomes: outcomes)
    }

    // 05/09/2026: un #Predicate comparando una propiedad de tipo enum propio
    // (StandingsCategory) contra un valor capturado devolvía SIEMPRE una lista vacía en
    // esta versión de SwiftData (Xcode 16.4) — el fetch "existing" nunca encontraba nada
    // que borrar, así que la fila "stale" de la misma categoría se quedaba junto a la
    // nueva en vez de ser sustituida. Lo detectaron los tests reales de
    // StandingsRepositoryTests al ejecutarse por primera vez en el CI. Se evita el
    // #Predicate del todo: se trae todo y se filtra en Swift (la caché local es de, como
    // mucho, unos cientos de filas — el coste es irrelevante).
    private func replaceStandings(category: StandingsCategory, rows: [StandingDraft], in context: ModelContext) {
        if let existing = try? context.fetch(FetchDescriptor<StandingModel>()) {
            for model in existing where model.category == category { context.delete(model) }
        }
        for draft in rows { context.insert(StandingModel(draft: draft)) }
    }

    private func replaceCarDrivers(category: StandingsCategory, rows: [CarDriverDraft], in context: ModelContext) {
        if let existing = try? context.fetch(FetchDescriptor<CarDriverModel>()) {
            for model in existing where model.category == category { context.delete(model) }
        }
        for draft in rows { context.insert(CarDriverModel(draft: draft)) }
    }

    private static func runCatching<T: Sendable>(_ body: @Sendable () async throws -> T) async -> Result<T, Error> {
        do { return .success(try await body()) }
        catch { return .failure(error) }
    }

    /// Traduce la excepción real (código HTTP, timeout, sin DNS...) a un motivo corto que
    /// cualquier usuario entienda — equivalente de `friendlyReason()` en Android.
    private static func friendlyReason(_ error: Error) -> String {
        if let requestError = error as? HTTPClient.RequestError, let code = requestError.statusCode {
            switch code {
            case 403, 429: return "La web ha bloqueado la petición (inténtalo más tarde)"
            case 500...599: return "La web está caída ahora mismo"
            default: return "La web no ha respondido correctamente"
            }
        }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .cannotFindHost, .dnsLookupFailed, .cannotConnectToHost, .networkConnectionLost:
                return "Sin conexión a internet"
            case .timedOut:
                return "La web ha tardado demasiado en responder"
            default:
                return "No se pudo conectar con la web"
            }
        }
        return "No se pudo leer la información de esta fuente"
    }

    private static let noDataFoundMessage = "No se encontró información en la web de origen"

    /// Las 14 fuentes por defecto — mismo orden que en Android. Cada tipo `XStandingsSource`
    /// vive en `Standings/Sources/` con un `init()` sin parámetros (algunas envuelven
    /// `MotorsportStandingsHTMLSource`/`OfficialRosterStandingsSource`/
    /// `DriverDbStandingsSource` ya configuradas; otras tienen lógica propia).
    public static let defaultSources: [any StandingsSource] = [
        F1StandingsSource(),
        MotoGpStandingsSource(),
        Moto2StandingsSource(),
        Moto3StandingsSource(),
        NascarStandingsSource(),
        IndyCarStandingsSource(),
        F1AcademyStandingsSource(),
        PorscheSupercupStandingsSource(),
        ElmsStandingsSource(),
        F2StandingsSource(),
        F3StandingsSource(),
        WecStandingsSource(),
        LeMansCupStandingsSource(),
        FormulaEStandingsSource()
    ]

    // 04/09/2026: public (no fileprivate/internal) — un valor por defecto de un
    // parámetro de un init() *público* necesita un símbolo igual de público, no solo
    // internal (el primer intento con "internal" seguía fallando exactamente igual, con
    // el mismo mensaje cambiando "fileprivate" por "internal" — lo detectó el CI de
    // GitHub Actions al compilar de verdad por primera vez).
    public static func makeWecDriversSource() -> AcoCarDriversSource {
        AcoCarDriversSource(
            category: .wec,
            listingUrl: "https://www.fiawec.com/en/page/grid",
            classMatchers: [
                (.hypercar, { $0.localizedCaseInsensitiveContains("Hypercar") }),
                (.lmgt3, { $0.localizedCaseInsensitiveContains("LMGT3") })
            ]
        )
    }

    public static func makeLeMansCupDriversSource() -> AcoCarDriversSource {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        return AcoCarDriversSource(
            category: .lemansCup,
            listingUrl: "https://www.lemanscup.com/en/car/\(year)",
            classMatchers: [
                // Pro/Am antes que el genérico LMP3, mismo motivo que en LeMansCupStandingsSource.
                (.lmp3ProAm, { $0.localizedCaseInsensitiveContains("LMP3") && $0.localizedCaseInsensitiveContains("Pro") }),
                (.lmp3, { $0.localizedCaseInsensitiveContains("LMP3") }),
                (.gt3, { $0.localizedCaseInsensitiveContains("GT3") })
            ]
        )
    }
}
