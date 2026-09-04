import Foundation

/// Equivalente de `DateTimeFormatters.kt`. `formatSyncTimestamp` no se porta — en Android
/// ya no la usa nadie (era del antiguo calendario `.ics` importado a mano, eliminado antes
/// de esta migración); `CategoryStandingsScreen.kt` tiene su propio `formatLastUpdated`
/// local con `SimpleDateFormat`, que aquí sí se porta como `formatLastUpdated`.
public enum DateTimeFormatters {

    private static func formatter(_ pattern: String, timeZone: TimeZone? = nil) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "es_ES")
        f.dateFormat = pattern
        if let timeZone { f.timeZone = timeZone }
        return f
    }

    private static let eventDateFormatter = formatter("EEE d MMM · HH:mm")
    private static let timeOnlyFormatter = formatter("HH:mm")
    private static let eventDateFormatterLong = formatter("EEEE d 'de' MMMM 'de' yyyy, HH:mm")
    private static let lastUpdatedFormatter = formatter("d MMM, HH:mm")

    /// Usa la zona horaria del DISPOSITIVO (comportamiento por defecto de `DateFormatter`).
    public static func formatEventDateTime(_ date: Date) -> String {
        eventDateFormatter.string(from: date)
    }

    /// Solo la hora, en la zona horaria local del dispositivo — usado en las notificaciones.
    public static func formatTimeOnly(_ date: Date) -> String {
        timeOnlyFormatter.string(from: date)
    }

    /// Fecha y hora completas (día de la semana, día, mes y año) en la zona del dispositivo.
    public static func formatEventDateTimeLong(_ date: Date) -> String {
        capitalizeFirst(eventDateFormatterLong.string(from: date))
    }

    /// Igual que `formatEventDateTimeLong` pero en la zona indicada (ej. la del circuito,
    /// "America/New_York") — nil si el id de zona no es válido, para no mostrar una hora
    /// inventada.
    public static func formatEventDateTime(_ date: Date, inZone zoneId: String) -> String? {
        guard let timeZone = TimeZone(identifier: zoneId) else { return nil }
        let f = formatter("EEEE d 'de' MMMM 'de' yyyy, HH:mm", timeZone: timeZone)
        return capitalizeFirst(f.string(from: date))
    }

    public static func formatLastUpdated(_ date: Date) -> String {
        lastUpdatedFormatter.string(from: date)
    }

    private static func capitalizeFirst(_ s: String) -> String {
        guard let first = s.first else { return s }
        return first.uppercased() + s.dropFirst()
    }
}

/// Los puntos vienen como `Double` (necesario para NASCAR/IndyCar), pero se muestran sin
/// decimales cuando son un número entero — que es lo habitual. Equivalente exacto de
/// `formatPoints()` en `StandingsScreen.kt`.
public func formatPoints(_ points: Double) -> String {
    if points.truncatingRemainder(dividingBy: 1) == 0 && abs(points) < 1e15 {
        return String(Int64(points))
    }
    return String(points)
}
