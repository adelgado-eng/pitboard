import Foundation

/// Fecha+hora compartida por las fuentes que scrapean webs en inglés de EE. UU. (IndyCar,
/// NASCAR vía ESPN) — equivalente exacto de `UsScheduleDateParsing.kt`. Dan el día sin
/// año ("Sep 6") y la hora en horario del Este ("2:30 PM ET"), que es como estas webs
/// muestran los horarios de emisión sin importar dónde esté el circuito.
public enum UsScheduleDateParsing {
    private static let eastern = TimeZone(identifier: "America/New_York")!

    private static let monthDayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "MMM d"
        formatter.timeZone = eastern
        return formatter
    }()

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h:mm a"
        formatter.timeZone = eastern
        return formatter
    }()

    private static var easternCalendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = eastern
        return calendar
    }()

    /// "Sep 6" -> la próxima fecha con ese día/mes (este año si aún no ha pasado, si no
    /// el que viene) — así una temporada que arranca en enero y esta ejecutándose en
    /// diciembre no coloca sus primeras carreras "hace un año".
    public static func resolveUpcomingMonthDay(_ text: String, today: Date = Date()) -> DateComponents? {
        guard let parsed = monthDayFormatter.date(from: text.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return nil
        }
        let parsedComponents = easternCalendar.dateComponents([.month, .day], from: parsed)
        guard let month = parsedComponents.month, let day = parsedComponents.day else { return nil }

        let todayComponents = easternCalendar.dateComponents([.year], from: today)
        guard let thisYear = todayComponents.year else { return nil }

        var candidate = DateComponents(year: thisYear, month: month, day: day)
        guard let candidateDate = easternCalendar.date(from: candidate) else { return nil }

        let cutoff = easternCalendar.date(byAdding: .day, value: -3, to: today) ?? today
        if candidateDate < cutoff {
            candidate.year = thisYear + 1
        }
        return candidate
    }

    /// "2:30 PM ET" / "2:30 PM" / "Noon ET" -> hora del día, ignorando cualquier sufijo
    /// de zona horaria. ESPN usa "Noon"/"Midnight" en vez de la hora numérica en algunas
    /// filas.
    public static func parseTimeOfDay(_ text: String) -> DateComponents? {
        var cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasSuffix("ET") {
            cleaned = String(cleaned.dropLast(2)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if cleaned.caseInsensitiveCompare("Noon") == .orderedSame {
            return DateComponents(hour: 12, minute: 0)
        }
        if cleaned.caseInsensitiveCompare("Midnight") == .orderedSame {
            return DateComponents(hour: 0, minute: 0)
        }
        guard let parsed = timeFormatter.date(from: cleaned) else { return nil }
        return easternCalendar.dateComponents([.hour, .minute], from: parsed)
    }

    /// Combina fecha (sin año, se resuelve al año que toque) + hora en horario del Este
    /// y devuelve un `Date`. Si no hay hora, se usa mediodía como marcador — mejor que
    /// nada, pero es una fecha sin hora real (ver comentario en cada fuente que lo use).
    public static func toDate(dateText: String, timeText: String?, today: Date = Date()) -> Date? {
        guard var dateComponents = resolveUpcomingMonthDay(dateText, today: today) else { return nil }
        let timeComponents = timeText.flatMap(parseTimeOfDay) ?? DateComponents(hour: 12, minute: 0)
        dateComponents.hour = timeComponents.hour
        dateComponents.minute = timeComponents.minute
        return easternCalendar.date(from: dateComponents)
    }

    public static func eastZoneId() -> String { eastern.identifier }
}
