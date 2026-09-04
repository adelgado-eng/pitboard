import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): ElmsStandingsSource es la fuente con más lógica
/// propia documentada como corregida a mano (ver su KDoc/comentario): el orden de las
/// tablas en el HTML no coincide con el de las pestañas visuales, la cabecera mezcla
/// `<th>`/`<td>`, y "Total points" no está en la posición de su propio encabezado. Los
/// números de coche del fixture son reales (los mismos que ya vive `officialTeamByCar` en
/// producción), así que el test también protege que esa tabla siga bien enlazada con el
/// nombre oficial de cada equipo.
final class ElmsStandingsSourceTests: XCTestCase {

    private let source = ElmsStandingsSource()

    // Cabecera mixta th/td: "N°" es un <td>, no un <th>.
    private func teamsTable(_ rows: String) -> String {
        """
        <table>
          <thead><tr><th>Pos.</th><td>N°</td><th>Team</th><th>Race pts</th><th>Total points</th></tr></thead>
          <tbody>\(rows)</tbody>
        </table>
        """
    }

    func testPairsHeadingAndTableByPositionAndUsesOfficialTeamName() throws {
        let html = """
            <html><body>
            <div>LMP2 Teams Classification</div>
            \(teamsTable("""
                <tr><td>1</td><td>#18</td><td>IDEC SPORT</td><td>10</td><td>65</td></tr>
                <tr><td>2</td><td>#9</td><td>PROTON COMPETITION</td><td>8</td><td>58</td></tr>
                """))
            <div>LMGT3 Teams Classification</div>
            \(teamsTable("""
                <tr><td>1</td><td>#33</td><td>TF SPORT</td><td>12</td><td>70</td></tr>
                """))
            </body></html>
            """

        let rows = try source.parseHTML(html, nowUtc: Date())

        XCTAssertEqual(rows.count, 3)
        let lmp2First = try XCTUnwrap(rows.first { $0.standingsClass == .lmp2 && $0.position == 1 })
        XCTAssertEqual(lmp2First.team, "IDEC Sport") // grafía oficial, no "IDEC SPORT" tal cual
        XCTAssertEqual(lmp2First.points, 65.0) // la última celda, no la columna "Race pts"
        let lmgt3 = try XCTUnwrap(rows.first { $0.standingsClass == .lmgt3 })
        XCTAssertEqual(lmgt3.team, "TF Sport")
    }

    func testWhenHeadingCountDoesNotMatchTableCountItIdentifiesClassByCarNumbers() throws {
        // Sin ningún título de clase — solo el plan B (pairByCarNumbers) puede etiquetarlas.
        let html = """
            <html><body>
            \(teamsTable("""
                <tr><td>1</td><td>#18</td><td>IDEC SPORT</td><td>10</td><td>65</td></tr>
                <tr><td>2</td><td>#9</td><td>PROTON COMPETITION</td><td>8</td><td>58</td></tr>
                """))
            \(teamsTable("<tr><td>1</td><td>#33</td><td>TF SPORT</td><td>12</td><td>70</td></tr>"))
            </body></html>
            """

        let rows = try source.parseHTML(html, nowUtc: Date())

        XCTAssertEqual(rows.filter { $0.standingsClass == .lmp2 }.count, 2)
        XCTAssertEqual(rows.filter { $0.standingsClass == .lmgt3 }.count, 1)
    }

    func testNoRecognizableTeamTableReturnsEmptyInsteadOfThrowing() throws {
        let html = "<html><body><table><thead><tr><th>Drivers</th></tr></thead></table></body></html>"

        let rows = try source.parseHTML(html, nowUtc: Date())

        XCTAssertTrue(rows.isEmpty)
    }
}
