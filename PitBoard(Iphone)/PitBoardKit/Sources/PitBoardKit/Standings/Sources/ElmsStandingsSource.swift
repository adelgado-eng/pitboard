import Foundation
import SwiftSoup

/// ELMS es la única categoría que se trata "por clase": la página oficial
/// (europeanlemansseries.com) publica una tabla de EQUIPOS por cada una de las 4 clases
/// que corren a la vez (LMP2, LMP2 Pro/Am, LMP3, LMGT3). Equivalente exacto de
/// `ElmsStandingsSource.kt`. Por diseño, aquí solo se guardan filas de tipo TEAM (nunca
/// pilotos — ver `ElmsDriversSource` para eso).
///
/// El orden de las tablas en el HTML NO coincide con el orden visual de sus pestañas, así
/// que los títulos "<Clase> Teams Classification" se emparejan con las tablas por
/// POSICIÓN relativa entre sí (título Nº1 con tabla Nº1, etc.). Si esa correspondencia no
/// se sostiene (número de títulos ≠ número de tablas), se cae a identificar cada tabla
/// por los números de coche que contiene contra `officialTeamByCar`, que es un dato mucho
/// más estable que la maquetación — ver `pairByCarNumbers`. HONESTO: sigue siendo una
/// heurística sin acceso permanente al HTML real de la web.
public final class ElmsStandingsSource: StandingsSource, @unchecked Sendable {
    public let category: StandingsCategory = .elms
    private let pageUrl = "https://www.europeanlemansseries.com/en/page/classification-2"

    private let classMatchers: [(StandingsClass, (String) -> Bool)] = [
        (.lmp2, { t in t.contains("LMP2") && !t.contains("PRO") }),
        (.lmp2ProAm, { t in t.contains("LMP2") && t.contains("PRO") }),
        (.lmp3, { t in t.contains("LMP3") }),
        (.lmgt3, { t in t.contains("LMGT3") || t.contains("GT3") })
    ]

    public init() {}

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        let html = try await HTTPClient.fetchHTML(pageUrl)
        return try parseHTML(html, nowUtc: nowUtc)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): separado de fetch() para poder testear el
    // emparejado título-tabla y el plan B por número de coche contra un fixture HTML sin
    // red — ver ElmsStandingsSourceTests.
    func parseHTML(_ html: String, nowUtc: Date) throws -> [StandingDraft] {
        let doc = try SwiftSoup.parse(html, pageUrl)

        // Títulos "<Clase> Teams Classification": solo los elementos "hoja" que cumplen
        // el criterio (ninguno de sus hijos directos lo cumple también), para no coger
        // por error un contenedor ancestro que envuelve varios títulos a la vez.
        let allElements = try doc.select("*").array()
        let candidateHeadings = try allElements.filter { el in
            let text = try el.text().uppercased()
            return text.contains("TEAM") && text.contains("CLASSIFICATION") && (try el.select("table").isEmpty())
        }
        let headings = try candidateHeadings.filter { el in
            let children = el.children().array()
            return try !children.contains { child in
                let childText = try child.text().uppercased()
                return childText.contains("TEAM") && childText.contains("CLASSIFICATION")
            }
        }

        let allTables = try doc.select("table").array()
        let teamTables = try allTables.filter { try isTeamTable($0) }
        guard !teamTables.isEmpty else { return [] }

        var paired: [(StandingsClass, Element)] = []
        if !headings.isEmpty, headings.count == teamTables.count {
            for (heading, table) in zip(headings, teamTables) {
                let headingText = try heading.text().uppercased()
                if let matched = classMatchers.first(where: { $0.1(headingText) })?.0 {
                    paired.append((matched, table))
                }
            }
        } else {
            paired = try pairByCarNumbers(teamTables)
        }

        var result: [StandingDraft] = []
        for (standingsClass, table) in paired {
            result.append(contentsOf: try parseTeamTable(table, standingsClass: standingsClass, nowUtc: nowUtc))
        }
        return result
    }

    /// Plan B para saber a qué clase pertenece cada tabla: se mira qué números de coche
    /// trae y se elige la clase de `officialTeamByCar` que más comparte con ella. Se
    /// prefiere no decir nada antes que etiquetar mal: una tabla se descarta si menos de
    /// la mitad de sus coches están en la clase candidata, y cada clase se asigna una
    /// sola vez.
    private func pairByCarNumbers(_ tables: [Element]) throws -> [(StandingsClass, Element)] {
        var used = Set<StandingsClass>()
        var result: [(StandingsClass, Element)] = []
        for table in tables {
            let numbers = try carNumbersIn(table)
            guard !numbers.isEmpty else { continue }

            var best: (StandingsClass, [String: String])?
            var bestHits = -1
            for (klass, cars) in Self.officialTeamByCar where !used.contains(klass) {
                let hits = numbers.filter { cars[$0] != nil }.count
                if hits > bestHits {
                    bestHits = hits
                    best = (klass, cars)
                }
            }
            guard let best, bestHits * 2 > numbers.count else { continue }
            used.insert(best.0)
            result.append((best.0, table))
        }
        return result
    }

    private func carNumbersIn(_ table: Element) throws -> [String] {
        let index = try resolveCarIndex(table)
        guard index >= 0 else { return [] }
        let rows = try dataRows(table)
        return try rows.compactMap { row in
            try carNumberAt(row.select("td").array(), carIndex: index)
        }
    }

    private func dataRows(_ table: Element) throws -> [Element] {
        let tbodyRows = try table.select("tbody tr").array()
        if !tbodyRows.isEmpty { return tbodyRows }
        let allRows = try table.select("tr").array()
        return Array(allRows.dropFirst())
    }

    /// TODAS las celdas de la fila de cabecera, tanto `<th>` como `<td>` — la web mezcla
    /// las dos en la misma fila.
    private func headerTexts(_ table: Element) throws -> [String] {
        guard let headerRow = try table.select("thead tr").first() ?? table.select("tr").first() else {
            return []
        }
        return try headerRow.select("th, td").array().map { try $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    /// Índice de la columna del número de coche (-1 si no está). Se compara sin acentos
    /// ni signos, así valen "N°", "Nº", "No.", "N" o "Num".
    private func carIndexOf(_ headers: [String]) -> Int {
        headers.firstIndex { header in
            let key = header.uppercased().replacingOccurrences(of: "[^A-Z0-9]", with: "", options: .regularExpression)
            return key == "N" || key == "NO" || key == "NUM" || key == "CAR" || header.localizedCaseInsensitiveContains("Car")
        } ?? -1
    }

    /// Columna del dorsal, con red de seguridad: si la cabecera no la nombra, se busca en
    /// las propias filas la columna cuyas celdas tienen forma de dorsal ("#29").
    private func resolveCarIndex(_ table: Element) throws -> Int {
        let fromHeader = carIndexOf(try headerTexts(table))
        if fromHeader >= 0 { return fromHeader }

        var hits: [Int: Int] = [:]
        let rows = try dataRows(table).prefix(5)
        for row in rows {
            let cells = try row.select("td").array()
            for (index, cell) in cells.enumerated() where index > 0 {
                let text = try cell.text().trimmingCharacters(in: .whitespacesAndNewlines)
                if Self.carNumberCell.firstMatch(in: text, range: NSRange(text.startIndex..<text.endIndex, in: text)) != nil {
                    hits[index, default: 0] += 1
                }
            }
        }
        return hits.max(by: { $0.value < $1.value })?.key ?? -1
    }

    private func carNumberAt(_ cells: [Element], carIndex: Int) throws -> String? {
        guard carIndex >= 0, cells.indices.contains(carIndex) else { return nil }
        let text = try cells[carIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
        let stripped = text.hasPrefix("#") ? String(text.dropFirst()).trimmingCharacters(in: .whitespaces) : text
        return stripped.isEmpty ? nil : stripped
    }

    /// Distingue una tabla de EQUIPOS ("Pos. N° Team ...") de una de PILOTOS ("Pos. N°
    /// Drivers ...") por su propia cabecera.
    private func isTeamTable(_ table: Element) throws -> Bool {
        try headerTexts(table).contains { $0.localizedCaseInsensitiveContains("Team") }
    }

    private struct TeamRow {
        var name: String
        var carNumber: String?
        var points: Double
        var carLabel: String
    }

    private func parseTeamTable(_ table: Element, standingsClass: StandingsClass, nowUtc: Date) throws -> [StandingDraft] {
        let headers = try headerTexts(table)
        let carIndex = try resolveCarIndex(table)
        let teamIndex = headers.firstIndex { $0.localizedCaseInsensitiveContains("Team") }
            ?? (carIndex >= 0 ? carIndex + 1 : 2)

        let rows = try dataRows(table)
        var parsed: [TeamRow] = []
        for row in rows {
            let cells = try row.select("td").array()
            guard !cells.isEmpty else { continue }

            let carNumber = try carNumberAt(cells, carIndex: carIndex)

            var scrapedTeam = ""
            if cells.indices.contains(teamIndex) {
                let raw = try cells[teamIndex].text().trimmingCharacters(in: .whitespacesAndNewlines)
                let looksLikeCarNumber = Self.carNumberCell.firstMatch(in: raw, range: NSRange(raw.startIndex..<raw.endIndex, in: raw)) != nil
                if !raw.isEmpty, !looksLikeCarNumber, Double(raw) == nil {
                    scrapedTeam = raw
                }
            }
            let teamText = carNumber.flatMap { Self.officialTeamByCar[standingsClass]?[$0] } ?? scrapedTeam
            guard !teamText.isEmpty else { continue }

            let points = cells.last.flatMap { try? $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
                .flatMap(Double.init) ?? 0.0
            let carLabel = (carNumber?.isEmpty == false) ? "#\(carNumber!)" : ""

            parsed.append(TeamRow(name: teamText, carNumber: carNumber, points: points, carLabel: carLabel))
        }

        return parsed.enumerated().map { index, row in
            StandingDraft(
                category: category,
                standingsClass: standingsClass,
                type: .team,
                entrantKey: "\(category.rawValue)-\(standingsClass.rawValue)-TEAM-\(row.name)-\(row.carNumber ?? "")",
                position: index + 1,
                name: row.carLabel.isEmpty ? row.name : row.carLabel,
                team: row.name,
                points: row.points,
                photoUrl: Self.teamLogoUrls[TextNormalizer.normalize(row.name)],
                updatedAtUtc: nowUtc
            )
        }
    }

    /// Una celda que es un dorsal y nada más: "#29", "# 7".
    private static let carNumberCell = try! NSRegularExpression(pattern: "^#\\s*\\d{1,3}$")

    /// Equipo oficial de cada coche, por clase y número — copiado de la web oficial de
    /// ELMS y comprobado uno a uno contra las tablas reales (ver comentario extenso en el
    /// Kotlin original: 47 coches, 30 escuderías).
    private static let officialTeamByCar: [StandingsClass: [String: String]] = [
        .lmp2: [
            "9": "Proton Competition", "10": "Vector Sport", "18": "IDEC Sport",
            "22": "United Autosports", "24": "Nielsen Racing", "25": "Algarve Pro Racing",
            "28": "IDEC Sport", "29": "Forestier Racing by Panis", "34": "Inter Europol Competition",
            "37": "CLX Motorsport", "43": "Inter Europol Competition"
        ],
        .lmp2ProAm: [
            "3": "DKR Engineering", "7": "Vector Sport", "14": "TDS Racing",
            "19": "Rossa Racing by Virage", "20": "Algarve Pro Racing", "21": "United Autosports",
            "27": "Nielsen Racing", "30": "Duqueine Team", "47": "CLX Motorsport",
            "83": "AF Corse", "88": "Proton Competition", "99": "AO by TF"
        ],
        .lmp3: [
            "4": "DKR Engineering", "5": "Rinaldi Racing", "8": "Team Virage",
            "11": "Eurointernational", "13": "Inter Europol Competition", "17": "CLX Motorsport",
            "31": "Racing Spirit of Leman", "35": "Ultimate", "68": "M Racing", "85": "R-ace GP"
        ],
        .lmgt3: [
            "23": "United Autosports", "33": "TF Sport", "50": "Richard Mille AF Corse",
            "51": "AF Corse", "54": "High Class Racing", "55": "Spirit of Race",
            "57": "Kessel Racing", "59": "Racing Spirit of Leman", "62": "Team Qatar by Iron Lynx",
            "63": "Iron Lynx", "74": "Kessel Racing", "75": "Proton Competition",
            "77": "Proton Competition", "86": "GR Racing"
        ]
    ]

    /// Logos de equipo por nombre normalizado — de la web oficial de ELMS
    /// (europeanlemansseries.com/en/teams), copiados tal cual del Kotlin original.
    private static let teamLogoUrls: [String: String] = [
        "af corse": "https://www.europeanlemansseries.com/uploads/af-corse-logo-rwf-2026-6a1050db75a59882854473.jpg",
        "algarve pro racing": "https://www.europeanlemansseries.com/uploads/logo-apr-grey-4-69fb32bf5abca703789835.jpg",
        "ao by tf": "https://www.europeanlemansseries.com/uploads/ao-69fb372fbce42693281829.png",
        "clx motorsport": "https://www.europeanlemansseries.com/uploads/clx-02-fondblanc-600x-8-6a104d436f2c8016040958.png",
        "dkr engineering": "https://www.europeanlemansseries.com/uploads/logo-dkr-6a104ddcf2be2522615970.jpg",
        "duqueine team": "https://www.europeanlemansseries.com/uploads/duqueine-fond-fonce-rvb-principal-vert-1387d8-69fb32424287d736277658.png",
        "eurointernational": "https://www.europeanlemansseries.com/uploads/eurointernational-69b4358ae3cbc269898583.png",
        "forestier racing by panis": "https://www.europeanlemansseries.com/uploads/panis-racing-logo-6970a70365497655741936.png",
        "gr racing": "https://www.europeanlemansseries.com/uploads/grracing-69b43709303d3982114829.png",
        "high class racing": "https://www.europeanlemansseries.com/uploads/high-class-racing-team-full-logo-6a10511ebcaca030176156.png",
        "idec sport": "https://www.europeanlemansseries.com/uploads/logo-idec-sport-signature-69fb35e5476e9923138721.png",
        "inter europol competition": "https://www.europeanlemansseries.com/uploads/bild1-69fb36bcd4173387502825.png",
        "iron lynx": "https://www.europeanlemansseries.com/uploads/ironlynx-69b4379bf12e4781333205.png",
        "kessel racing": "https://www.europeanlemansseries.com/uploads/kessel-ra-69fa2133a8675957048954.jpg",
        "m racing": "https://www.europeanlemansseries.com/uploads/capture-d-ecran-2019-02-11-a-18-14-19-6a104f0b2a460059499819.png",
        "nielsen racing": "https://www.europeanlemansseries.com/uploads/nielsenracing-69b43001251fe977012084.png",
        "proton competition": "https://www.europeanlemansseries.com/uploads/protoncompetition-left-69fb3661a27b9065802455.jpg",
        "race gp": "https://www.europeanlemansseries.com/uploads/racegp-69b43d5a7bb12568008372.png",
        "racing spirit of leman": "https://www.europeanlemansseries.com/uploads/racing-of-leman-fond-transp-03-69fb30a68842b083442787.png",
        "richard mille af corse": "https://www.europeanlemansseries.com/uploads/richard-mille-af-corse-logo-69faf0be76a4e012429055.jpg",
        "rinaldi racing": "https://www.europeanlemansseries.com/uploads/logo-rinaldi-racing-6a104da903a40406466216.png",
        "rossa racing by virage": "https://www.europeanlemansseries.com/uploads/rossaracingvirage-69b439ef72f3f176279175.png",
        "spirit of race": "https://www.europeanlemansseries.com/uploads/spiritofrace-69b43a472fd20008829558.png",
        "tds racing": "https://www.europeanlemansseries.com/uploads/logo-tds-69fb334d3e327775241613.jpg",
        "team qatar by iron lynx": "https://www.europeanlemansseries.com/uploads/qmmfiron-69b43b3c8e00b215906208.png",
        "team virage": "https://www.europeanlemansseries.com/uploads/escudo-virage-2020-6a104e3c9fe59272031071.png",
        "tf sport": "https://www.europeanlemansseries.com/uploads/logo-tf-sport-full-black-69fae8addc867999538358.png",
        "ultimate": "https://www.europeanlemansseries.com/uploads/ultimate-logo-02-6a104df5da4fd412204634.png",
        "united autosports": "https://www.europeanlemansseries.com/uploads/united-autosports-black-6a10503c2b10b938159166.png",
        "vector sport": "https://www.europeanlemansseries.com/uploads/vector-sport-logo-69fb316db07cd851127223.jpg"
    ]
}
