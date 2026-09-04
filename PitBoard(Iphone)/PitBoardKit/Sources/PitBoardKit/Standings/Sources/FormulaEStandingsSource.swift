import Foundation

/// Fórmula E: fiaformulae.com/en/standings es una SPA sin datos en el HTML servido, pero
/// el widget carga sus datos desde una API JSON pública del mismo proveedor que usa
/// MotoGP (Pulselive) — equivalente exacto de `FormulaEStandingsSource.kt`:
///
/// 1. GET `/formula-e/v1/championships` — lista de temporadas, cada una con `status`
///    ("Present" para la actual). Se busca la de `status == "Present"`.
/// 2. GET `/formula-e/v1/standings/drivers?championshipId={id}` y
///    `/formula-e/v1/standings/teams?championshipId={id}` — JSON plano con posición,
///    nombre y puntos.
///
/// Fotos/logos: ninguna respuesta anterior trae imagen, pero se construyen a mano con el
/// `driverId`/`teamId` que sí traen esas respuestas, contra el host de estáticos de la
/// propia web (ver `staticFilesBase`). El logo de equipo es SVG, no PNG/JPG como el resto
/// de la app — en iOS, `AsyncImage`/Kingfisher no rendrizan SVG nativamente sin ayuda; se
/// deja documentado aquí para la Fase 4 (UI), igual que Android necesitaba el decoder SVG
/// de Coil.
///
/// La posición NO se copia del campo de la API tal cual: se reordena por puntos
/// descendentes y se renumera, para que una fila con posición nula o duplicada en el JSON
/// de origen no pueda colar un hueco o un choque de posiciones (mismo criterio que
/// `WecStandingsSource` tras su propio bug de posiciones).
public final class FormulaEStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory = .formulaE

    public init() {}

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        guard let championshipId = try await fetchCurrentChampionshipId() else { return [] }

        let driverRows = (try? await fetchDriverRows(championshipId: championshipId)) ?? []
        let teamRows = (try? await fetchTeamRows(championshipId: championshipId)) ?? []

        return buildDriverEntities(driverRows, championshipId: championshipId, nowUtc: nowUtc)
            + buildTeamEntities(teamRows, championshipId: championshipId, nowUtc: nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): fetchCurrentChampionshipId/fetchDriverRows/
    // fetchTeamRows separadas en "red" (sin cambios) + "parsing puro" (las funciones de
    // abajo, internal) para poder testear contra fixtures JSON reales sin red — ver
    // FormulaEStandingsSourceTests.
    func buildDriverEntities(_ rows: [Row], championshipId: String, nowUtc: Date) -> [StandingDraft] {
        rows.sorted { $0.points > $1.points }.enumerated().map { index, row in
            StandingDraft(
                category: category,
                type: .driver,
                entrantKey: row.key,
                position: index + 1,
                name: row.name,
                team: row.team,
                points: row.points,
                photoUrl: row.imageId.map { "\(Self.staticFilesBase)/drivers/\(championshipId)/right/large/\($0).png" },
                updatedAtUtc: nowUtc
            )
        }
    }

    func buildTeamEntities(_ rows: [Row], championshipId: String, nowUtc: Date) -> [StandingDraft] {
        rows.sorted { $0.points > $1.points }.enumerated().map { index, row in
            StandingDraft(
                category: category,
                type: .team,
                entrantKey: row.key,
                position: index + 1,
                name: row.name,
                team: "",
                points: row.points,
                photoUrl: row.imageId.map { "\(Self.staticFilesBase)/badges/\($0).svg" },
                updatedAtUtc: nowUtc
            )
        }
    }

    private func fetchCurrentChampionshipId() async throws -> String? {
        try parseChampionshipsJSON(try await HTTPClient.fetchHTML("\(Self.apiBase)/championships"))
    }

    func parseChampionshipsJSON(_ json: String) throws -> String? {
        let data = Data(json.utf8)
        let response = try JSONDecoder().decode(FormulaEChampionshipsResponse.self, from: data)
        let championships = response.championships ?? []
        return championships.first { $0.status?.caseInsensitiveCompare("Present") == .orderedSame }?.id
            ?? championships.last?.id
    }

    // internal (no private): expuesta a test — ver parseDriverRowsJSON/parseTeamRowsJSON.
    struct Row {
        var key: String
        var name: String
        var team: String
        var points: Double
        /// driverId (piloto) o teamId (equipo) — nil solo si la API no lo trajo, y en ese
        /// caso no se puede construir la URL de foto/logo.
        var imageId: String?
    }

    private func fetchDriverRows(championshipId: String) async throws -> [Row] {
        try parseDriverRowsJSON(try await HTTPClient.fetchHTML("\(Self.apiBase)/standings/drivers?championshipId=\(championshipId)"))
    }

    func parseDriverRowsJSON(_ json: String) throws -> [Row] {
        let rows = try JSONDecoder().decode([FormulaEDriverStanding].self, from: Data(json.utf8))
        return rows.compactMap { row -> Row? in
            let name = [row.driverFirstName, row.driverLastName]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            guard !name.isEmpty else { return nil }
            let driverId = row.driverId?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            return Row(
                key: driverId ?? "\(category.rawValue)-DRIVER-\(name)",
                name: name,
                team: row.driverTeamName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
                points: row.driverPoints ?? 0.0,
                imageId: driverId
            )
        }
    }

    private func fetchTeamRows(championshipId: String) async throws -> [Row] {
        try parseTeamRowsJSON(try await HTTPClient.fetchHTML("\(Self.apiBase)/standings/teams?championshipId=\(championshipId)"))
    }

    func parseTeamRowsJSON(_ json: String) throws -> [Row] {
        let rows = try JSONDecoder().decode([FormulaETeamStanding].self, from: Data(json.utf8))
        return rows.compactMap { row -> Row? in
            let name = row.teamName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !name.isEmpty else { return nil }
            let teamId = row.teamId?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            return Row(
                key: teamId ?? "\(category.rawValue)-TEAM-\(name)",
                name: name,
                team: "",
                points: row.teamPoints ?? 0.0,
                imageId: teamId
            )
        }
    }

    private static let apiBase = "https://api.formula-e.pulselive.com/formula-e/v1"
    private static let staticFilesBase = "https://static-files.formula-e.pulselive.com"
}

private struct FormulaEChampionshipsResponse: Decodable {
    let championships: [FormulaEChampionship]?
}

private struct FormulaEChampionship: Decodable {
    let id: String?
    let status: String?
}

private struct FormulaEDriverStanding: Decodable {
    let driverId: String?
    let driverTeamName: String?
    let driverFirstName: String?
    let driverLastName: String?
    let driverPoints: Double?
}

private struct FormulaETeamStanding: Decodable {
    let teamId: String?
    let teamName: String?
    let teamPoints: Double?
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
