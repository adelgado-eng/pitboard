import Foundation

public struct EventWeekendGroups: Sendable {
    public var weekendLabel: String
    /// Misma etiqueta que `weekendLabel` pero como clave de `Strings` (ej.
    /// "events_weekend_today") en vez de texto fijo en español — `weekendLabel` se deja
    /// igual a propósito (los tests existentes y cualquier otro consumidor lo siguen
    /// leyendo tal cual) y esta clave nueva es solo para quien quiera traducirla (ver
    /// EventsScreen.swift). Vacía cuando `weekendLabel` también lo está (sin eventos).
    public var weekendLabelKey: String
    public var weekendEvents: [EventModel]
    public var laterEvents: [EventModel]
}

/// Separa eventos en el bloque del fin de semana más cercano que contenga eventos
/// (viernes–domingo) y el resto de eventos futuros — equivalente exacto de
/// `EventWeekendGrouper.kt`.
public enum EventWeekendGrouper {
    public static func split(_ events: [EventModel], zone: TimeZone = .current, now: Date = Date()) -> EventWeekendGroups {
        guard let first = events.first else {
            return EventWeekendGroups(weekendLabel: "", weekendLabelKey: "", weekendEvents: [], laterEvents: [])
        }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone

        let firstDate = calendar.startOfDay(for: first.startTimeUtc)
        // Calendar Gregoriano: weekday 1 = domingo ... 6 = viernes ... 7 = sábado.
        let firstWeekday = calendar.component(.weekday, from: firstDate)
        let daysBackToFriday = (firstWeekday - 6 + 7) % 7
        let daysForwardToSunday = (1 - firstWeekday + 7) % 7

        guard
            let friday = calendar.date(byAdding: .day, value: -daysBackToFriday, to: firstDate),
            let sunday = calendar.date(byAdding: .day, value: daysForwardToSunday, to: firstDate),
            let sundayEnd = calendar.date(byAdding: .day, value: 1, to: sunday).map({ $0.addingTimeInterval(-1) })
        else {
            return EventWeekendGroups(weekendLabel: "", weekendLabelKey: "", weekendEvents: [], laterEvents: [])
        }

        let today = calendar.startOfDay(for: now)
        let label: String
        let labelKey: String
        if firstDate == today {
            label = "Hoy"
            labelKey = "events_weekend_today"
        } else if today >= friday && today <= sunday {
            label = "Este fin de semana"
            labelKey = "events_weekend_this"
        } else if friday == nextFriday(strictlyAfter: today, calendar: calendar) {
            label = "Próximo fin de semana"
            labelKey = "events_weekend_next"
        } else {
            label = "Próxima cita"
            labelKey = "events_weekend_upcoming"
        }

        let weekend = events.filter { $0.startTimeUtc >= friday && $0.startTimeUtc <= sundayEnd }
        let later = events.filter { $0.startTimeUtc > sundayEnd }

        return EventWeekendGroups(weekendLabel: label, weekendLabelKey: labelKey, weekendEvents: weekend, laterEvents: later)
    }

    /// Equivalente de `TemporalAdjusters.next(DayOfWeek.FRIDAY)`: el próximo viernes
    /// ESTRICTAMENTE después de `date` — si `date` ya es viernes, salta a la semana
    /// siguiente (a diferencia de `nextOrSame`).
    private static func nextFriday(strictlyAfter date: Date, calendar: Calendar) -> Date {
        let weekday = calendar.component(.weekday, from: date)
        let daysForward = (6 - weekday + 7) % 7
        let offset = daysForward == 0 ? 7 : daysForward
        return calendar.date(byAdding: .day, value: offset, to: date) ?? date
    }
}
