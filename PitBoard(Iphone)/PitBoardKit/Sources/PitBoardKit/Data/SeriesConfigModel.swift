import Foundation
import SwiftData

/// Personalización del usuario para una serie (tag corto + color) — equivalente exacto de
/// `SeriesConfigEntity.kt`. Una fila por serie con sus valores por defecto
/// (`RaceSeries.defaultTag`/`defaultColorHex`) hasta que el usuario los cambie desde el
/// editor de series.
@Model
public final class SeriesConfigModel {
    @Attribute(.unique) public var series: RaceSeries
    public var tag: String
    public var colorHex: String

    public init(series: RaceSeries, tag: String, colorHex: String) {
        self.series = series
        self.tag = tag
        self.colorHex = colorHex
    }
}
