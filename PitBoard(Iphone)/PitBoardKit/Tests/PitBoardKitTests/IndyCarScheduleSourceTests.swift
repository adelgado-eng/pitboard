import XCTest
@testable import PitBoardKit

/// Fase 1 del diagnóstico (graphify): indycar.com/schedule marca las carreras ya
/// disputadas con la clase "completed" en la propia tarjeta — el fixture comprueba que
/// esas se descartan en vez de aparecer con una cuenta atrás sin sentido.
final class IndyCarScheduleSourceTests: XCTestCase {

    private let source = IndyCarScheduleSource()

    private let fixtureHTML = """
        <html><body>
        <div class="event-card completed">
          <div class="event-card-header-date">Aug 24</div>
          <div class="event-card-header-time">3:00 PM ET</div>
          <div class="event-card-title">Firestone Grand Prix</div>
          <div class="event-card-track-name">Nashville</div>
        </div>
        <div class="event-card">
          <div class="event-card-header-date">Sep 6</div>
          <div class="event-card-header-time">2:30 PM ET</div>
          <div class="event-card-title">Milwaukee Mile</div>
          <div class="event-card-track-name">Milwaukee</div>
        </div>
        </body></html>
        """

    func testDropsCardsOfRacesAlreadyRun() throws {
        let events = try source.parseHTML(fixtureHTML)

        XCTAssertEqual(events.count, 1)
        XCTAssertTrue(events[0].fullTitle.contains("Milwaukee Mile"))
        XCTAssertTrue(events.allSatisfy { !$0.fullTitle.contains("Firestone") })
    }

    func testResolvesDateAndTimeTheSameWayAsUsScheduleDateParsing() throws {
        let events = try source.parseHTML(fixtureHTML)
        let expected = UsScheduleDateParsing.toDate(dateText: "Sep 6", timeText: "2:30 PM ET")

        XCTAssertEqual(events[0].startTimeUtc, expected)
    }

    func testWithoutRecognizableCardsReturnsEmptyInsteadOfThrowing() throws {
        XCTAssertTrue(try source.parseHTML("<html><body><p>Sin calendario</p></body></html>").isEmpty)
    }
}
