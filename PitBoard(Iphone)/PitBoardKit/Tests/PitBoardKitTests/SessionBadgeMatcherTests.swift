import XCTest
@testable import PitBoardKit

final class SessionBadgeMatcherTests: XCTestCase {

    func testMatchesQualifyingVariants() {
        XCTAssertEqual(SessionBadgeMatcher.match("Qualifying"), SessionBadgeType.qualy.rawValue)
        XCTAssertEqual(SessionBadgeMatcher.match("Q2"), SessionBadgeType.other.rawValue) // "q2" no contiene "quali"/"qualifying"
        XCTAssertEqual(SessionBadgeMatcher.match("Shootout"), SessionBadgeType.qualy.rawValue)
    }

    func testMatchesSprint() {
        XCTAssertEqual(SessionBadgeMatcher.match("Sprint Race"), SessionBadgeType.sprint.rawValue)
        // El orden de comprobación es qualy → sprint → practice → race (igual que el
        // `when` de Kotlin): "Sprint Qualifying" contiene "qualifying", así que gana QUALY,
        // no SPRINT — comportamiento intencional, no un descuido del matcher.
        XCTAssertEqual(SessionBadgeMatcher.match("Sprint Qualifying"), SessionBadgeType.qualy.rawValue)
    }

    func testMatchesPractice() {
        XCTAssertEqual(SessionBadgeMatcher.match("Free Practice 2"), SessionBadgeType.practice.rawValue)
        XCTAssertEqual(SessionBadgeMatcher.match("FP1"), SessionBadgeType.practice.rawValue)
        XCTAssertEqual(SessionBadgeMatcher.match("Warm Up"), SessionBadgeType.practice.rawValue)
    }

    func testMatchesRace() {
        XCTAssertEqual(SessionBadgeMatcher.match("Race"), SessionBadgeType.race.rawValue)
        XCTAssertEqual(SessionBadgeMatcher.match("Australian Grand Prix"), SessionBadgeType.race.rawValue)
    }

    func testFallsBackToOther() {
        XCTAssertEqual(SessionBadgeMatcher.match("Ceremonia de apertura"), SessionBadgeType.other.rawValue)
    }
}
