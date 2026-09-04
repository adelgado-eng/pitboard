import XCTest
import SwiftUI
@testable import PitBoardKit

final class ColorContrastTests: XCTestCase {

    func testHexParsingRoundTrip() {
        XCTAssertNotNil(Color(hex: "#FF0000"))
        XCTAssertNotNil(Color(hex: "FF0000"))
        XCTAssertNotNil(Color(hex: "#AAFF0000")) // con canal alfa
        XCTAssertNil(Color(hex: "no-es-un-color"))
        XCTAssertNil(Color(hex: "#FF00")) // longitud inválida
    }

    func testSafeParseColorFallsBackOnInvalidHex() {
        let fallback = Color.red
        let result = ColorContrast.safeParseColor("not-a-color", fallback: fallback)
        XCTAssertEqual(result, fallback)
    }

    func testPerceivedLuminanceOrdering() {
        // Blanco debe ser más luminoso que negro — la fórmula exacta importa menos que
        // el orden relativo, que es lo único de lo que depende ensureContrast/readableTextColor.
        XCTAssertGreaterThan(ColorContrast.perceivedLuminance(.white), ColorContrast.perceivedLuminance(.black))
    }

    func testReadableTextColorPicksContrastingColor() {
        XCTAssertEqual(ColorContrast.readableTextColor(background: .white), .black)
        XCTAssertEqual(ColorContrast.readableTextColor(background: .black), .white)
    }

    func testEnsureContrastLeavesAlreadyContrastingColorUnchanged() {
        // Un color ya muy distinto del fondo no debería tocarse.
        let result = ColorContrast.ensureContrast(.white, background: .black)
        XCTAssertEqual(ColorContrast.perceivedLuminance(result), ColorContrast.perceivedLuminance(.white), accuracy: 0.01)
    }
}
