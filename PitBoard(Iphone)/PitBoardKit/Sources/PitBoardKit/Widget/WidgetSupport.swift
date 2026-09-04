import Foundation

/// Constantes y lógica pura compartidas por la extensión de widget — equivalente de las
/// partes de `WidgetPrefsRepository.kt` que sobreviven a la migración a `AppIntentConfiguration`
/// (ver `RaceWidgetConfigurationIntent.swift`): en iOS 17+ la configuración por-instancia de
/// un widget la persiste el propio sistema a partir de los `@Parameter` del `AppIntent` —
/// no hace falta un `WidgetPrefsRepository.save/load` a mano como en Glance (Android no
/// tiene configuración nativa por widget, por eso `RaceWidgetConfigActivity` existía como
/// una Activity completa a medida; aquí no hace falta).
public enum WidgetPrefsConstants {
    public static let defaultEventCount = 10
    public static let defaultWordCount = 4
    /// Igual convenio que Android: valor "sin límite" para el selector "Todos".
    public static let noLimit = 9_999
    public static let defaultBackgroundColorHex = "#131519"
    /// Opacidad fija del fondo del widget — igual que `FIXED_BACKGROUND_OPACITY` en Android.
    public static let backgroundOpacity: Double = 0.72
}

/// Snapshot mínimo de tag+color de una serie, para que la entry del widget sea `Sendable`
/// sin arrastrar un `@Model` de SwiftData fuera de su `ModelContext`.
public struct SeriesTagColor: Sendable, Hashable {
    public var tag: String
    public var colorHex: String

    public init(tag: String, colorHex: String) {
        self.tag = tag
        self.colorHex = colorHex
    }
}

/// Recorta el título completo a `wordLimit` palabras, quitando antes el nombre de la
/// sesión (Carrera/Clasificación/Sprint/Libres, en varios idiomas) y el nombre de la
/// serie si ya está repetido al principio — equivalente exacto de `eventDisplayName()` en
/// `RaceWidget.kt`.
public func widgetEventDisplayName(fullTitle: String, seriesDisplayName: String, wordLimit: Int) -> String {
    let sessionKeywords = [
        "carrera", "race", "calificacion", "calificación", "clasificacion", "clasificación",
        "qualifying", "qualy", "sprint", "libre", "entrenamiento", "practice", "warm", "shootout"
    ]

    let parts = fullTitle
        .components(separatedBy: " - ")
        .map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { part in !sessionKeywords.contains { part.lowercased().contains($0) } }

    let body: String
    if parts.count > 1, parts[0].caseInsensitiveCompare(seriesDisplayName) == .orderedSame {
        body = parts.dropFirst().joined(separator: " - ")
    } else {
        body = parts.joined(separator: " - ")
    }

    let source = body.isEmpty ? fullTitle : body
    let words = source.split(separator: " ").map(String.init)
    if words.count <= wordLimit { return words.joined(separator: " ") }
    return words.prefix(wordLimit).joined(separator: " ") + "…"
}

/// Hora de inicio en la zona horaria del CIRCUITO (no la del dispositivo) — nil si no hay
/// zona guardada, o si coincide con la del dispositivo (no aporta nada mostrarla dos
/// veces). Equivalente exacto de `trackTimeLabel()`.
public func widgetTrackTimeLabel(startTimeUtc: Date, timeZoneId: String?) -> String? {
    guard let timeZoneId, let timeZone = TimeZone(identifier: timeZoneId) else { return nil }
    guard TimeZone.current.identifier != timeZone.identifier else { return nil }

    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = timeZone
    let components = calendar.dateComponents([.hour, .minute], from: startTimeUtc)
    guard let hour = components.hour, let minute = components.minute else { return nil }
    return String(format: "%02d:%02d", hour, minute)
}

/// Días naturales hasta `date` (puede ser negativo si ya pasó) — equivalente de `daysUntil()`.
public func widgetDaysUntil(_ date: Date, calendar: Calendar = .current) -> Int {
    let today = calendar.startOfDay(for: Date())
    let target = calendar.startOfDay(for: date)
    return calendar.dateComponents([.day], from: today, to: target).day ?? 0
}
