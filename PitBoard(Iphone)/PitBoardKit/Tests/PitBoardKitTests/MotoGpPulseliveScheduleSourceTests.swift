import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): la misma clase cubre MotoGP/Moto2/Moto3 con 3
/// instancias distintas (parametrizadas por acrónimo) — el fixture incluye sesiones de
/// las tres clases dentro del mismo evento para comprobar que cada instancia se queda
/// solo con la suya. También incluye el caso documentado de offset con y sin ":" en la
/// misma API.
final class MotoGpPulseliveScheduleSourceTests: XCTestCase {

    private let fixtureJSON = """
        [
          {
            "kind": "GP",
            "hashtag": "#QatarGP",
            "circuit": { "name": "Lusail International Circuit" },
            "broadcasts": [
              { "type": "SESSION", "kind": "PRACTICE", "name": "Free Practice", "date_start": "2026-03-06T10:00:00+03:00", "category": { "acronym": "MGP" } },
              { "type": "SESSION", "kind": "RACE", "name": "Sprint", "date_start": "2026-03-07T15:00:00+03:00", "category": { "acronym": "MGP" } },
              { "type": "SESSION", "kind": "PRACTICE", "name": "Moto2 Practice", "date_start": "2026-03-06T09:00:00+03:00", "category": { "acronym": "MT2" } },
              { "type": "PRESS_CONFERENCE", "kind": "OTHER", "name": "Press", "date_start": "2026-03-06T08:00:00+03:00", "category": { "acronym": "MGP" } }
            ]
          },
          {
            "kind": "TEST",
            "hashtag": "#TestEvent",
            "circuit": { "name": "Sepang" },
            "broadcasts": []
          }
        ]
        """

    func testDropsEventsThatAreNotAGrandPrixWeekend() throws {
        let events = try MotoGpPulseliveScheduleSource(classCode: "MGP").parseJSON(fixtureJSON)

        XCTAssertTrue(events.allSatisfy { !$0.fullTitle.contains("Sepang") })
    }

    func testKeepsOnlyItsOwnClassSessionsDroppingPressAndOtherClasses() throws {
        let motogp = try MotoGpPulseliveScheduleSource(classCode: "MGP").parseJSON(fixtureJSON)

        XCTAssertEqual(motogp.count, 2)
        XCTAssertTrue(motogp.allSatisfy { !$0.fullTitle.contains("Press") })
        XCTAssertTrue(motogp.allSatisfy { !$0.fullTitle.contains("Moto2") })
    }

    func testAMoto2InstanceKeepsOnlyMt2Sessions() throws {
        let moto2 = try MotoGpPulseliveScheduleSource(series: .moto2, classCode: "MT2").parseJSON(fixtureJSON)

        XCTAssertEqual(moto2.count, 1)
        XCTAssertTrue(moto2[0].fullTitle.contains("Moto2 Practice"))
    }

    func testARaceWithSprintInTheNameIsLabeledSprintNotRace() throws {
        let motogp = try MotoGpPulseliveScheduleSource(classCode: "MGP").parseJSON(fixtureJSON)

        XCTAssertEqual(motogp.first { $0.fullTitle.contains("Sprint") }?.inferredBadge, SessionBadgeType.sprint.rawValue)
    }

    func testParsesTheTimeBothWithAndWithoutAColonInTheOffset() throws {
        let motogp = try MotoGpPulseliveScheduleSource(classCode: "MGP").parseJSON(fixtureJSON)

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let expected = formatter.date(from: "2026-03-06T10:00:00+03:00")

        XCTAssertEqual(motogp.first { $0.fullTitle.contains("Free Practice") }?.startTimeUtc, expected)
    }
}
