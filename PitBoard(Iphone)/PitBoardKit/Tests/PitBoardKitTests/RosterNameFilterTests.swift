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
}
