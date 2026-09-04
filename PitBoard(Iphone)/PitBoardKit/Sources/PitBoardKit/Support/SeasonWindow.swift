import Foundation

/// Límite superior para "eventos de este año" — equivalente exacto de `SeasonWindow.kt`.
/// Existe porque un par de fuentes (GT World Challenge, F1 Academy) listan "los próximos
/// eventos" sin pedir un año concreto — si esa web publica pronto el primer evento de la
/// temporada siguiente, sin este límite se colaría en la lista antes de tiempo.
///
/// Se recalcula siempre a partir de `nowUtc`, nunca de un año fijo — al pasar al año
/// siguiente el límite se mueve solo.
public enum SeasonWindow {
    public static func endOfCurrentYearUtc(nowUtc: Date = Date()) -> Date {
        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = TimeZone(identifier: "UTC")!

        let year = utcCalendar.component(.year, from: nowUtc)
        var components = DateComponents()
        components.timeZone = TimeZone(identifier: "UTC")
        components.year = year
        components.month = 12
        components.day = 31
        components.hour = 23
        components.minute = 59
        components.second = 59

        return utcCalendar.date(from: components) ?? nowUtc
    }
}
