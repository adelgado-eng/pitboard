import Foundation
import SwiftData
import os

/// Sincroniza las fuentes de calendario, una por serie, EN PARALELO y de forma
/// independiente — equivalente exacto de `RaceScheduleRepository.kt`. Mismo patrón que
/// `StandingsRepository.syncAll()`: si una serie falla, las demás se guardan igual, y si
/// una fuente falla o no encuentra eventos, sus sesiones ya guardadas se dejan intactas.
public final class RaceScheduleRepository: @unchecked Sendable {

    private static let logger = Logger(subsystem: "com.pitboard.app", category: "RaceScheduleSync")

    private let sources: [any RaceScheduleSource]
    /// Inyectable (por defecto el store real, compartido vía App Group) para poder
    /// testear `syncAll()` contra un `ModelContainer` en memoria sin tocar disco.
    private let modelContainer: ModelContainer

    public init(
        sources: [any RaceScheduleSource] = RaceScheduleRepository.defaultSources,
        modelContainer: ModelContainer = AppDatabase.container
    ) {
        self.sources = sources
        self.modelContainer = modelContainer
    }

    public struct SeriesOutcome: Sendable {
        public var series: RaceSeries
        public var ok: Bool
        public var sessionCount: Int
        public var detail: String?
    }

    public struct SyncResult: Sendable {
        public var outcomes: [SeriesOutcome]
        public var succeeded: [RaceSeries] { outcomes.filter(\.ok).map(\.series) }
        public var failed: [RaceSeries] { outcomes.filter { !$0.ok }.map(\.series) }
    }

    public func syncAll() async -> SyncResult {
        let results: [(RaceSeries, Result<[EventDraft], Error>)] = await withTaskGroup(
            of: (RaceSeries, Result<[EventDraft], Error>).self
        ) { group in
            for source in sources {
                group.addTask {
                    do { return (source.series, .success(try await source.fetch())) }
                    catch { return (source.series, .failure(error)) }
                }
            }
            var collected: [(RaceSeries, Result<[EventDraft], Error>)] = []
            for await entry in group { collected.append(entry) }
            return collected
        }

        let context = ModelContext(modelContainer)

        let outcomes: [SeriesOutcome] = results.map { series, result in
            switch result {
            case .failure(let error):
                Self.logger.error("\(series.rawValue): fallo al obtener el calendario — \(String(describing: error))")
                return SeriesOutcome(series: series, ok: false, sessionCount: 0, detail: Self.friendlyReason(error))

            case .success(let rawEvents):
                // Filtro defensivo: una fuente puede listar la misma sesión dos veces
                // bajo un `uid` distinto cada vez — eso NO choca con ninguna
                // restricción de esquema (no hay índice único aquí, ver EventModel), así
                // que sin este filtro ambas filas se guardarían como el mismo evento
                // repetido. Se deduplica por contenido real (título completo + hora
                // exacta), no por uid, conservando la primera aparición.
                var seen = Set<EventDraft>()
                var events: [EventDraft] = []
                for event in rawEvents {
                    let key = EventDraft(
                        series: event.series, uid: "", fullTitle: event.fullTitle,
                        startTimeUtc: event.startTimeUtc, inferredBadge: ""
                    )
                    if seen.insert(key).inserted { events.append(event) }
                }
                if events.count != rawEvents.count {
                    Self.logger.warning("\(series.rawValue): se descartaron \(rawEvents.count - events.count) sesiones duplicadas (mismo título y hora)")
                }

                if events.isEmpty {
                    Self.logger.warning("\(series.rawValue): la fuente respondió pero sin sesiones")
                    return SeriesOutcome(
                        series: series, ok: false, sessionCount: 0,
                        detail: "La web respondió pero no se encontró ninguna sesión (¿cambio de diseño en la fuente?)"
                    )
                }

                replaceSeries(series, events: events, in: context)
                return SeriesOutcome(series: series, ok: true, sessionCount: events.count, detail: nil)
            }
        }

        do {
            try context.save()
        } catch {
            Self.logger.error("No se pudo guardar la sincronización de calendario — \(String(describing: error))")
        }

        return SyncResult(outcomes: outcomes)
    }

    private func replaceSeries(_ series: RaceSeries, events: [EventDraft], in context: ModelContext) {
        let predicate = #Predicate<EventModel> { $0.series == series }
        if let existing = try? context.fetch(FetchDescriptor(predicate: predicate)) {
            for model in existing { context.delete(model) }
        }
        for draft in events { context.insert(EventModel(draft: draft)) }
    }

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

    /// Mismas 21 fuentes que en Android (`RaceScheduleRepository.kt`), mismo orden y
    /// mismos parámetros.
    public static let defaultSources: [any RaceScheduleSource] = [
        JolpicaF1ScheduleSource(),
        IndyCarScheduleSource(),
        EspnNascarScheduleSource(series: .nascarCup, slug: "nascar-premier"),
        EspnNascarScheduleSource(series: .nascarTruck, slug: "truck"),
        // El slug de ESPN sigue siendo "xfinity" aunque la serie se llame ahora "NASCAR
        // O'Reilly Auto Parts Series" (mismo comentario que en el Kotlin original).
        EspnNascarScheduleSource(series: .nascarXfinity, slug: "xfinity"),
        JsonLdSportsEventScheduleSource(
            series: .f2,
            baseUrl: "https://www.fiaformula2.com",
            listingUrlTemplate: "https://www.fiaformula2.com/en/racing/{year}",
            roundHrefPrefixTemplate: "/en/racing/{year}/"
        ),
        JsonLdSportsEventScheduleSource(
            series: .f3,
            baseUrl: "https://www.fiaformula3.com",
            listingUrlTemplate: "https://www.fiaformula3.com/en/racing/{year}",
            roundHrefPrefixTemplate: "/en/racing/{year}/"
        ),
        F1AcademyScheduleSource(),
        JsonLdSportsEventScheduleSource(
            series: .elms,
            baseUrl: "https://www.europeanlemansseries.com",
            listingUrlTemplate: "https://www.europeanlemansseries.com/en/season/{year}",
            roundHrefPrefixTemplate: "/en/race/",
            excludeSlugContaining: ["test"]
        ),
        // WEC: mismo organizador y misma plantilla que ELMS (fiawec.com) — reutiliza la
        // misma fuente genérica. "prologue" es el test de pretemporada.
        JsonLdSportsEventScheduleSource(
            series: .wec,
            baseUrl: "https://www.fiawec.com",
            listingUrlTemplate: "https://www.fiawec.com/en/season/{year}",
            roundHrefPrefixTemplate: "/en/race/",
            excludeSlugContaining: ["prologue"]
        ),
        // Le Mans Cup: mismo organizador y misma plantilla que ELMS/WEC.
        // "collective-test" es el día de test oficial antes de la primera cita.
        JsonLdSportsEventScheduleSource(
            series: .lemansCup,
            baseUrl: "https://www.lemanscup.com",
            listingUrlTemplate: "https://www.lemanscup.com/en/season/{year}",
            roundHrefPrefixTemplate: "/en/race/",
            excludeSlugContaining: ["test"]
        ),
        FormulaEScheduleSource(),
        ImsaScheduleSource(),
        // Porsche Supercup: su web oficial es una SPA sin datos de sesiones en el HTML
        // servido — se queda con el calendario de Wikipedia (solo fecha).
        WikipediaSeasonCalendarSource(series: .porscheSupercup, wikipediaSlug: "Porsche_Supercup"),
        MotoGpPulseliveScheduleSource(series: .motoGp, classCode: "MGP"),
        // Moto2/Moto3: misma API que MotoGP, solo cambia el acrónimo de clase.
        MotoGpPulseliveScheduleSource(series: .moto2, classCode: "MT2"),
        MotoGpPulseliveScheduleSource(series: .moto3, classCode: "MT3"),
        GtWorldChallengeScheduleSource(series: .gtChallengeEurope, baseUrl: "https://www.gt-world-challenge-europe.com"),
        GtWorldChallengeScheduleSource(series: .gtChallengeAmerica, baseUrl: "https://www.gt-world-challenge-america.com"),
        GtWorldChallengeScheduleSource(series: .gtChallengeAsia, baseUrl: "https://www.gt-world-challenge-asia.com"),
        GtWorldChallengeScheduleSource(series: .gtChallengeAustralia, baseUrl: "https://www.gt-world-challenge-australia.com")
    ]
}
