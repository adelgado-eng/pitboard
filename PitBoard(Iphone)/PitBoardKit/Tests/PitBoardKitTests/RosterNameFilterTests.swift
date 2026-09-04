import XCTest
@testable import PitBoardKit

final class RosterNameFilterTests: XCTestCase {

    func testEmptyKnownNamesMeansNoFiltering() {
        XCTAssertTrue(RosterNameFilter.isInRoster("Cualquier Piloto", knownNames: []))
    }

    func testExactAndSubstringMatch() {
        let known: Set<String> = ["Max Verstappen", "Lando Norris"]
        XCTAssertTrue(RosterNameFilter.isInRoster("Max Verstappen", knownNames: known))
        XCTAssertTrue(RosterNameFilter.isInRoster("Lando", knownNames: known))
    }

    func testAccentInsensitiveMatch() {
        // "Jorge Martín" (con tilde) en la tabla de puntos vs "Jorge Martin" (sin tilde)
        // en la página de referencia — deben coincidir igual.
        XCTAssertTrue(RosterNameFilter.isInRoster("Jorge Martín", knownNames: ["Jorge Martin"]))
    }

    func testInitialsWithDifferentPunctuationMatchViaNoSpaceComparison() {
        // "AJ Allmendinger" (espn.com) vs "A. J. Allmendinger" (driverdb.com).
        XCTAssertTrue(RosterNameFilter.isInRoster("AJ Allmendinger", knownNames: ["A. J. Allmendinger"]))
    }

    func testAbbreviatedFirstNameMatchesViaLastNameFallback() {
        // "J. Martin" (autosport.com abrevia) vs "Jorge Martin" (nombre completo) — ninguna
        // subcadena contiene a la otra, así que cae al último recurso: mismo apellido de
        // 3+ letras.
        XCTAssertTrue(RosterNameFilter.isInRoster("J. Martin", knownNames: ["Jorge Martin"]))
    }

    func testUnrelatedNameDoesNotMatch() {
        XCTAssertFalse(RosterNameFilter.isInRoster("Piloto Reserva", knownNames: ["Max Verstappen", "Lando Norris"]))
    }

    func testFilterKeepingRealDropsNamesNotInRoster() {
        let rows = ["Max Verstappen", "Piloto Reserva", "Lando Norris"]
        let filtered = RosterNameFilter.filterKeepingReal(rows, knownNames: ["Max Verstappen", "Lando Norris"]) { $0 }
        XCTAssertEqual(filtered, ["Max Verstappen", "Lando Norris"])
    }

    func testFilterKeepingRealFallsBackToUnfilteredWhenEverythingWouldBeDropped() {
        // Si el filtro fallara por completo (ej. página de referencia sin nombres reales),
        // nunca debe vaciar la clasificación entera.
        let rows = ["Max Verstappen", "Lando Norris"]
        let filtered = RosterNameFilter.filterKeepingReal(rows, knownNames: ["Nombre Que No Coincide Con Nadie"]) { $0 }
        XCTAssertEqual(filtered, rows)
    }

    func testFilterKeepingRealOnEmptyInputStaysEmpty() {
        let filtered = RosterNameFilter.filterKeepingReal([String](), knownNames: ["Max Verstappen"]) { $0 }
        XCTAssertTrue(filtered.isEmpty)
    }

    // 04/09/2026 (Fase 1 del diagnóstico): parseNames es la parte de fetchKnownNames que
    // antes no tenía test — la heurística de "parece un nombre propio" en títulos,
    // enlaces y negritas de la página de referencia.
    func testParseNamesRecognizesProperNamesInHeadingsLinksAndBold() throws {
        let html = """
            <html><body>
            <h2>2026 MotoGP Rider Line-Ups</h2>
            <ul>
              <li>Jorge Martin</li>
              <li><a>Marc Marquez</a></li>
              <li><strong>Fabio Di Giannantonio</strong></li>
              <li>Menú de navegación</li>
            </ul>
            </body></html>
            """

        let names = try RosterNameFilter.parseNames(html, url: "https://example.com")

        XCTAssertTrue(names.contains("Jorge Martin"))
        XCTAssertTrue(names.contains("Marc Marquez"))
        XCTAssertTrue(names.contains("Fabio Di Giannantonio"))
        // "de" no empieza en mayúscula, así que no cumple la heurística de "nombre propio".
        XCTAssertFalse(names.contains("Menú de navegación"))
    }

    func testParseNamesWithNothingRecognizableReturnsEmptySetInsteadOfThrowing() throws {
        let html = "<html><body><p>contenido en minúsculas, sin nombres</p></body></html>"

        let names = try RosterNameFilter.parseNames(html, url: "https://example.com")

        XCTAssertTrue(names.isEmpty)
    }
}
