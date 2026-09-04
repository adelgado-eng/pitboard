import Foundation
import SwiftSoup

/// indycar.com/Standings: la web OFICIAL de IndyCar, con tabla real en el HTML —
/// posición ya desempatada correctamente, foto real del piloto y equipo en la misma
/// fila. Equivalente exacto de `IndyCarStandingsSource.kt`. A diferencia de F1/NASCAR,
/// no hace falta cruzar con ninguna otra fuente.
public final class IndyCarStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory = .indycar
    private let standingsUrl = "https://www.indycar.com/Standings"

    public init() {}

    private struct DriverParse {
        var name: String
        var team: String
        var points: Double
        var photoUrl: String?
    }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let html = try await HTTPClient.fetchHTML(standingsUrl)
        return try parseHTML(html, nowUtc: nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // recorte del "?w=80" y el "Logo" pegado al alt del escudo contra un fixture HTML sin
    // red — ver IndyCarStandingsSourceTests.
    func parseHTML(_ html: String, nowUtc: Date) throws -> [StandingDraft] {
        let doc = try SwiftSoup.parse(html, standingsUrl)
        let tables = try doc.select("table").array()
        guard let table = try tables.first(where: { t in
            try t.select("th").array().contains { try $0.text().localizedCaseInsensitiveContains("Driver") }
        }) else {
            return []
        }

        let headers = try table.select("th").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
        let driverIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Driver") } ?? 1
        let teamIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Team") }
        guard let pointsIndex = headers.firstIndex(where: {
            $0.localizedCaseInsensitiveContains("Points") || $0.localizedCaseInsensitiveContains("Pts")
        }) else {
            return []
        }

        // La posición se renumera 1..N según el orden en que ya vienen listadas las filas
        // (el orden real de la tabla oficial) en vez de fiarse de la columna "Rank"/"Pos".
        let rows = try table.select("tbody tr").array()
        let parsed: [DriverParse] = try rows.compactMap { row -> DriverParse? in
            let cells = try row.select("td").array()
            guard !cells.isEmpty, cells.indices.contains(driverIndex) else { return nil }
            let driverCell = cells[driverIndex]

            let linkText = try driverCell.select("a").first()?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let rawName = (linkText?.isEmpty == false ? linkText : nil) ?? (try driverCell.text().trimmingCharacters(in: .whitespacesAndNewlines))
            let name = rawName.replacingOccurrences(of: "^#?\\d+\\s+", with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return nil }

            // La celda de piloto trae varias imágenes (endplate del coche + foto de
            // cara) — se busca la que apunte a la carpeta "Headshot".
            //
            // 04/09/2026: el HTML de la tabla trae siempre "?w=80" (miniatura de 80x64 —
            // se ve borrosa en el avatar grande de CategoryStandingsScreen y en la vista
            // previa a pantalla completa). Comprobado a mano que el mismo CMS de
            // indycar.com (Sitecore) sirve la foto real a resolución nativa (585x470) si
            // se quita el parámetro de ancho — mismo dominio y patrón que ya usan los
            // logos de equipo de aquí abajo (teamLogoUrls, con ?w=400).
            let images = try driverCell.select("img").array().compactMap { try? $0.absUrl("src") }
            let photoUrl = images.first { $0.localizedCaseInsensitiveContains("Headshot") }
                .flatMap { $0.isEmpty ? nil : $0 }
                .map { $0.replacingOccurrences(of: "[?&]w=\\d+", with: "", options: .regularExpression) }

            // El equipo a veces viene como logo (sin texto visible) — se cae al atributo
            // alt de esa imagen, que siempre trae "Logo" al final (ej. "Andretti Global
            // Logo "), se quita para no guardar el nombre con "Logo" pegado.
            var team = ""
            if let teamIndex, cells.indices.contains(teamIndex) {
                let teamCell = cells[teamIndex]
                let text = try teamCell.text().trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty {
                    team = text
                } else if let alt = try teamCell.select("img").first()?.attr("alt") {
                    team = alt.replacingOccurrences(of: "\\s*Logo\\s*$", with: "", options: [.regularExpression, .caseInsensitive])
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }

            let points = cells.indices.contains(pointsIndex)
                ? (Double(try cells[pointsIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0.0)
                : 0.0

            return DriverParse(name: name, team: team, points: points, photoUrl: photoUrl)
        }

        let driverRows: [StandingDraft] = parsed.enumerated().map { index, r in
            StandingDraft(
                category: category,
                type: .driver,
                entrantKey: "\(category.rawValue)-DRIVER-\(r.name)",
                position: index + 1,
                name: r.name,
                team: r.team,
                points: r.points,
                photoUrl: r.photoUrl,
                updatedAtUtc: nowUtc
            )
        }

        // Equipos: se agrupan los pilotos por equipo y se suman sus puntos — esta tabla
        // oficial no trae una clasificación de equipos aparte en la misma página.
        var totalsByTeam: [String: Double] = [:]
        var teamOrder: [String] = []
        for row in driverRows where !row.team.isEmpty {
            if totalsByTeam[row.team] == nil { teamOrder.append(row.team) }
            totalsByTeam[row.team, default: 0] += row.points
        }
        let teamRows: [StandingDraft] = teamOrder
            .map { ($0, totalsByTeam[$0] ?? 0) }
            .sorted { $0.1 > $1.1 }
            .enumerated()
            .map { index, entry in
                let (teamName, points) = entry
                return StandingDraft(
                    category: category,
                    type: .team,
                    entrantKey: "\(category.rawValue)-TEAM-\(teamName)",
                    position: index + 1,
                    name: teamName,
                    team: "",
                    points: points,
                    photoUrl: Self.teamLogoUrls[TextNormalizer.normalize(teamName)],
                    updatedAtUtc: nowUtc
                )
            }

        return driverRows + teamRows
    }

    /// Logos oficiales de los 13 equipos de la IndyCar 2026, por nombre normalizado —
    /// extraídos del propio indycar.com/Standings (atributo alt de la imagen de equipo,
    /// ya sin el sufijo "Logo").
    private static let teamLogoUrls: [String: String] = [
        "chip ganassi racing": "https://www.indycar.com/-/media/IndyCar/Team/ChipGanassiRacing.png?w=400",
        "andretti global": "https://www.indycar.com/-/media/IndyCar/Team/AndrettiGlobal.png?w=400",
        "arrow mclaren": "https://www.indycar.com/-/media/IndyCar/Team/ArrowMcLaren.png?w=400",
        "team penske": "https://www.indycar.com/-/media/IndyCar/Team/TeamPenske.png?w=400",
        "meyer shank racing": "https://www.indycar.com/-/media/IndyCar/Team/MeyerShankRacing.png?w=400",
        "juncos hollinger racing": "https://www.indycar.com/-/media/IndyCar/Team/JuncosHollinger.png?w=400",
        "rahal letterman lanigan racing": "https://www.indycar.com/-/media/IndyCar/Team/RahalLettermanLanigan.png?w=400",
        "ecr": "https://www.indycar.com/-/media/IndyCar/Team/EdCarpenterRacing.png?w=400",
        "aj foyt enterprises": "https://www.indycar.com/-/media/IndyCar/Team/AJFoytRacing.png?w=400",
        "dale coyne racing": "https://www.indycar.com/-/media/IndyCar/Team/DaleCoyneRacing.png?w=400",
        "dreyer reinbold racing": "https://www.indycar.com/-/media/IndyCar/Team/DreyerReinboldRacing.png?w=400",
        "abel motorsports": "https://www.indycar.com/-/media/IndyCar/Team/AbelMotorsports.png?w=400",
        "hmd motorsports w aj foyt racing": "https://www.indycar.com/-/media/IndyCar/Team/HMD-Foyt.png?w=400"
    ]
}
