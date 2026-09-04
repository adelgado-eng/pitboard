import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): F1 es la serie más usada y su fuente de calendario
/// (Jolpica, sucesora de Ergast) es una API JSON estable — buen primer caso para fijar el
/// formato esperado con un test de regresión. El fixture reproduce a mano la forma real
/// de la respuesta de api.jolpi.ca/ergast/f1/current.json: un fin de semana normal (ronda
/// 1) y uno de sprint (ronda 2), las dos formas que el parser tiene que soportar.
final class JolpicaF1ScheduleSourceTests: XCTestCase {

    private let source = JolpicaF1ScheduleSource()

    private let fixtureJSON = """
        {
          "MRData": {
            "RaceTable": {
              "Races": [
                {
                  "round": "1",
                  "raceName": "Bahrain Grand Prix",
                  "Circuit": { "circuitName": "Bahrain International Circuit" },
                  "date": "2026-03-08",
                  "time": "15:00:00Z",
                  "FirstPractice": { "date": "2026-03-06", "time": "11:30:00Z" },
                  "SecondPractice": { "date": "2026-03-06", "time": "15:00:00Z" },
                  "ThirdPractice": { "date": "2026-03-07", "time": "11:30:00Z" },
                  "Qualifying": { "date": "2026-03-07", "time": "15:00:00Z" }
                },
                {
                  "round": "2",
                  "raceName": "Saudi Arabian Grand Prix",
                  "Circuit": { "circuitName": "Jeddah Corniche Circuit" },
                  "date": "2026-03-15",
                  "time": "17:00:00Z",
                  "FirstPractice": { "date": "2026-03-13", "time": "13:30:00Z" },
                  "SprintQualifying": { "date": "2026-03-13", "time": "17:30:00Z" },
                  "Sprint": { "date": "2026-03-14", "time": "13:00:00Z" },
                  "Qualifying": { "date": "2026-03-14", "time": "17:00:00Z" }
                }
              ]
            }
          }
        }
        """

    func testParsesANormalWeekendAndASprintWeekendWithoutLosingSessions() throws {
        let events = try source.parseJSON(fixtureJSON)

        XCTAssertEqual(events.count, 10) // 5 sesiones por ronda
    }

    func testANormalWeekendBringsFP1FP2FP3QualifyingAndRace() throws {
        let bahrain = try source.parseJSON(fixtureJSON).filter { $0.uid.hasPrefix("F1-R01-") }

        XCTAssertEqual(bahrain.count, 5)
        XCTAssertEqual(bahrain.first { $0.uid == "F1-R01-Carrera" }?.inferredBadge, SessionBadgeType.race.rawValue)
        XCTAssertEqual(bahrain.first { $0.uid == "F1-R01-Clasificación" }?.inferredBadge, SessionBadgeType.qualy.rawValue)
    }

    func testASprintWeekendBringsShootoutAndSprintInsteadOfFP2AndFP3() throws {
        let saudi = try source.parseJSON(fixtureJSON).filter { $0.uid.hasPrefix("F1-R02-") }

        XCTAssertEqual(saudi.count, 5)
        XCTAssertTrue(saudi.contains { $0.uid == "F1-R02-SprintShootout" && $0.inferredBadge == SessionBadgeType.qualy.rawValue })
        XCTAssertTrue(saudi.contains { $0.uid == "F1-R02-Sprint" && $0.inferredBadge == SessionBadgeType.sprint.rawValue })
        XCTAssertTrue(saudi.allSatisfy { !$0.fullTitle.contains("Libres 2") }) // sin FP2 en sprint
    }

    func testConvertsUtcDateAndTimeToADate() throws {
        let race = try source.parseJSON(fixtureJSON).first { $0.uid == "F1-R01-Carrera" }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        XCTAssertEqual(race?.startTimeUtc, formatter.date(from: "2026-03-08T15:00:00Z"))
    }

    func testAResponseWithoutRacesDoesNotFailReturnsEmptyList() throws {
        let empty = #"{ "MRData": { "RaceTable": { "Races": [] } } }"#

        XCTAssertTrue(try source.parseJSON(empty).isEmpty)
    }
}
