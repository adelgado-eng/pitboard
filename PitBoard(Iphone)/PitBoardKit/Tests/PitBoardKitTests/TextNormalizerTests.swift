import XCTest
@testable import PitBoardKit

final class TextNormalizerTests: XCTestCase {

    func testNormalizeStripsAccentsAndLowercases() {
        XCTAssertEqual(TextNormalizer.normalize("Jorge Martín"), "jorge martin")
        XCTAssertEqual(TextNormalizer.normalize("Hülkenberg"), "hulkenberg")
        XCTAssertEqual(TextNormalizer.normalize("Pérez"), "perez")
    }

    func testNormalizeCollapsesWhitespace() {
        XCTAssertEqual(TextNormalizer.normalize("  Max   Verstappen  "), "max verstappen")
    }

    func testNormalizeStripsPunctuation() {
        XCTAssertEqual(TextNormalizer.normalize("A.J. Allmendinger"), "aj allmendinger")
    }

    func testSlugifyProducesHyphenatedLowercase() {
        XCTAssertEqual(TextNormalizer.slugify("Max Verstappen"), "max-verstappen")
        XCTAssertEqual(TextNormalizer.slugify("Kimi Antonelli"), "kimi-antonelli")
    }
}
