import Foundation
import SwiftData
import UserNotifications

/// Equivalente exacto de `NotificationScheduler.kt`. Diferencia clave de plataforma: en
/// Android cada aviso pasa por un `Worker` de WorkManager que se despierta a la hora
/// exacta y construye la notificación en ese momento (`EventReminderWorker`). En iOS no
/// hace falta ese intermediario — `UNNotificationRequest` ya se entrega directamente al
/// sistema con su contenido completo y un disparador (`UNTimeIntervalNotificationTrigger`)
/// para la hora exacta; iOS se encarga de mostrarla sin volver a despertar la app. Por eso
/// aquí no existe un "EventReminderWorker" aparte.
public final class NotificationScheduler: @unchecked Sendable {

    private static let reminderIdPrefix = "event_reminder_"

    private let modelContainer: ModelContainer
    private let settings: AppSettingsRepository

    public init(modelContainer: ModelContainer = AppDatabase.container, settings: AppSettingsRepository) {
        self.modelContainer = modelContainer
        self.settings = settings
    }

    public func rescheduleAllUpcoming() async {
        await cancelAllPending()

        guard settings.notificationsEnabled else { return }

        let competitiveEnabled = settings.competitiveNotificationsEnabled
        let practiceEnabled = settings.practiceNotificationsEnabled
        let minutesBefore = settings.notificationMinutesBefore
        let disabledSeries = settings.notificationDisabledSeries

        let context = ModelContext(modelContainer)
        let nowUtc = Date()
        let endOfYear = SeasonWindow.endOfCurrentYearUtc(nowUtc: nowUtc)
        let descriptor = FetchDescriptor<EventModel>(
            predicate: #Predicate { $0.startTimeUtc >= nowUtc && $0.startTimeUtc <= endOfYear },
            sortBy: [SortDescriptor(\.startTimeUtc)]
        )
        guard let upcoming = try? context.fetch(descriptor) else { return }

        for event in upcoming {
            if disabledSeries.contains(event.series) { continue }

            let badge = event.inferredBadge
            let isCompetitive = badge == SessionBadgeType.race.rawValue
                || badge == SessionBadgeType.qualy.rawValue
                || badge == SessionBadgeType.sprint.rawValue
            let isPractice = badge == SessionBadgeType.practice.rawValue

            let allowed: Bool
            if isCompetitive {
                allowed = competitiveEnabled
            } else if isPractice {
                allowed = practiceEnabled
            } else {
                allowed = competitiveEnabled // por defecto igual que competitivas, igual que en Android
            }
            guard allowed else { continue }

            await scheduleReminder(for: event, minutesBefore: minutesBefore)
        }
    }

    private func scheduleReminder(for event: EventModel, minutesBefore: Int) async {
        let reminderTime = event.startTimeUtc.addingTimeInterval(-Double(minutesBefore) * 60)
        let interval = reminderTime.timeIntervalSinceNow
        guard interval > 0 else { return }

        let content = UNMutableNotificationContent()
        content.title = Self.headline(minutesBefore: minutesBefore, badge: event.inferredBadge, startTime: event.startTimeUtc)
        content.body = event.fullTitle
        content.sound = .default
        content.userInfo = ["eventUid": event.uid]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        let request = UNNotificationRequest(
            identifier: Self.reminderIdPrefix + event.uid,
            content: content,
            trigger: trigger
        )

        try? await UNUserNotificationCenter.current().add(request)
    }

    public func cancelAllPending() async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let ids = pending.map(\.identifier).filter { $0.hasPrefix(Self.reminderIdPrefix) }
        guard !ids.isEmpty else { return }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    // "1 hora" es el único caso que no se dice en minutos en español natural.
    private static func headline(minutesBefore: Int, badge: String, startTime: Date) -> String {
        let session = (SessionBadgeType(rawValue: badge)?.label ?? "").lowercased()
        let timeLabel = minutesBefore == 60 ? "1 hora" : "\(minutesBefore) minutos"
        let base: String
        switch badge {
        case SessionBadgeType.race.rawValue: base = "Carrera en \(timeLabel)"
        case SessionBadgeType.qualy.rawValue: base = "Calificación en \(timeLabel)"
        case SessionBadgeType.sprint.rawValue: base = "Sprint en \(timeLabel)"
        case SessionBadgeType.practice.rawValue: base = "Libres en \(timeLabel)"
        default: base = "\(session) en \(timeLabel)"
        }
        // Hora exacta de inicio entre paréntesis, ej: "Carrera en 1 hora (15:00)"
        return "\(base) (\(DateTimeFormatters.formatTimeOnly(startTime)))"
    }
}
