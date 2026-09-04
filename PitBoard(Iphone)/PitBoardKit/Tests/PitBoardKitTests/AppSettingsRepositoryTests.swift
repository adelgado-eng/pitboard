import XCTest
@testable import PitBoardKit

final class AppSettingsRepositoryTests: XCTestCase {

    /// Cada test usa un suite de `UserDefaults` propio y desechable — nunca toca
    /// `AppDatabase.appGroupId` (el App Group real) ni se cruza con otros tests.
    private func makeRepository() -> AppSettingsRepository {
        AppSettingsRepository(suiteName: "com.pitboard.app.tests.\(UUID().uuidString)")
    }

    func testDefaults() {
        let settings = makeRepository()
        XCTAssertTrue(settings.notificationsEnabled)
        XCTAssertFalse(settings.notificationPermissionRequested)
        XCTAssertEqual(settings.notificationMinutesBefore, AppSettingsRepository.defaultMinutesBefore)
        XCTAssertTrue(settings.competitiveNotificationsEnabled)
        XCTAssertFalse(settings.practiceNotificationsEnabled)
        // A diferencia de los avisos, Clasificaciones empieza DESACTIVADA a propósito
        // (requiere internet y es opt-in explícito) — ver AppSettingsRepository.kt original.
        XCTAssertFalse(settings.standingsEnabled)
        XCTAssertEqual(settings.appTheme, .system)
        XCTAssertTrue(settings.notificationDisabledSeries.isEmpty)
        XCTAssertTrue(settings.eventScreenActiveSeries.isEmpty)
        XCTAssertFalse(settings.hasCompletedFirstSync)
    }

    func testSettersPersistTheirValue() {
        let settings = makeRepository()

        settings.setNotificationsEnabled(false)
        XCTAssertFalse(settings.notificationsEnabled)

        settings.setNotificationMinutesBefore(30)
        XCTAssertEqual(settings.notificationMinutesBefore, 30)

        settings.setAppTheme(.dark)
        XCTAssertEqual(settings.appTheme, .dark)

        settings.setNotificationDisabledSeries([.f1, .motoGp])
        XCTAssertEqual(settings.notificationDisabledSeries, [.f1, .motoGp])

        settings.setHasCompletedFirstSync(true)
        XCTAssertTrue(settings.hasCompletedFirstSync)
    }

    func testInvalidStoredMinutesFallsBackToDefault() {
        let suiteName = "com.pitboard.app.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.set(45, forKey: "notification_minutes_before") // 45 no está en validMinutes (15/30/60)

        let settings = AppSettingsRepository(suiteName: suiteName)
        XCTAssertEqual(settings.notificationMinutesBefore, AppSettingsRepository.defaultMinutesBefore)
    }

    func testValuesSurviveANewInstanceOverTheSameSuite() {
        let suiteName = "com.pitboard.app.tests.\(UUID().uuidString)"
        AppSettingsRepository(suiteName: suiteName).setAppTheme(.light)

        let reloaded = AppSettingsRepository(suiteName: suiteName)
        XCTAssertEqual(reloaded.appTheme, .light)
    }
}
