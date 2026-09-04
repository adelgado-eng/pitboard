import XCTest
@testable import PitBoardKit

final class EventWeekendGrouperTests: XCTestCase {

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    /// Un viernes cualquiera, hallado con `Calendar.nextDate` (no memorizado a mano) para
    /// que el test no dependa de saber qué día de la semana cayó una fecha concreta.
    private func aFriday() -> Date {
        let anchor = utc.date(from: DateComponents(year: 2026, month: 1, day: 1))!
        return utc.nextDate(after: anchor, matching: DateComponents(weekday: 6), matchingPolicy: .nextTimePreservingSmallerComponents)!
    }

    func testEmptyEventsReturnsEmptyGroups() {
        let groups = EventWeekendGrouper.split([], zone: utc.timeZone)
        XCTAssertEqual(groups.weekendLabel, "")
        XCTAssertTrue(groups.weekendEvents.isEmpty)
        XCTAssertTrue(groups.laterEvents.isEmpty)
    }

    func testLabelIsHoyWhenFirstEventIsToday() {
        let now = utc.date(from: DateComponents(year: 2026, month: 3, day: 10, hour: 9))!
        let event = makeEvent(startTimeUtc: utc.date(byAdding: .hour, value: 3, to: now)!)

        let groups = EventWeekendGrouper.split([event], zone: utc.timeZone, now: now)

        XCTAssertEqual(groups.weekendLabel, "Hoy")
        XCTAssertEqual(groups.weekendEvents.map(\.uid), [event.uid])
    }

    func testLabelIsEsteFinDeSemanaWhenTodayFallsInsideIt() {
        let friday = aFriday()
        let now = utc.date(byAdding: .hour, value: 8, to: friday)! // "hoy" es el propio viernes
        let saturdayEvent = makeEvent(startTimeUtc: utc.date(byAdding: .day, value: 1, to: friday)!)

        let groups = EventWeekendGrouper.split([saturdayEvent], zone: utc.timeZone, now: now)

        XCTAssertEqual(groups.weekendLabel, "Este fin de semana")
    }

    func testLabelIsProximoFinDeSemanaWhenItsTheVeryNextOne() {
        let friday = aFriday()
        let monday = utc.date(byAdding: .day, value: -4, to: friday)! // lunes de esa misma semana
        let event = makeEvent(startTimeUtc: friday)

        let groups = EventWeekendGrouper.split([event], zone: utc.timeZone, now: monday)

        XCTAssertEqual(groups.weekendLabel, "Próximo fin de semana")
    }

    func testLabelIsProximaCitaWhenFurtherAway() {
        let friday = aFriday()
        let twoWeeksBefore = utc.date(byAdding: .day, value: -14, to: friday)!
        let event = makeEvent(startTimeUtc: friday)

        let groups = EventWeekendGrouper.split([event], zone: utc.timeZone, now: twoWeeksBefore)

        XCTAssertEqual(groups.weekendLabel, "Próxima cita")
    }

    func testEventsAfterSundaySplitIntoLater() {
        let friday = aFriday()
        let now = friday
        let saturdayEvent = makeEvent(uid: "sat", startTimeUtc: utc.date(byAdding: .day, value: 1, to: friday)!)
        let nextWeekEvent = makeEvent(uid: "next-week", startTimeUtc: utc.date(byAdding: .day, value: 8, to: friday)!)

        let groups = EventWeekendGrouper.split([saturdayEvent, nextWeekEvent], zone: utc.timeZone, now: now)

        XCTAssertEqual(groups.weekendEvents.map(\.uid), ["sat"])
        XCTAssertEqual(groups.laterEvents.map(\.uid), ["next-week"])
    }
}

/// `EventWeekendGrouper.split` trabaja sobre `[EventModel]` (`@Model`), no sobre
/// `EventDraft` — un `EventModel` se puede construir suelto para leer sus propiedades sin
/// necesidad de insertarlo en ningún `ModelContext`.
private func makeEvent(uid: String = UUID().uuidString, startTimeUtc: Date) -> EventModel {
    EventModel(series: .f1, uid: uid, fullTitle: "Formula 1 - GP de Ejemplo - Carrera", startTimeUtc: startTimeUtc, inferredBadge: SessionBadgeType.race.rawValue)
}
