import Foundation
import SwiftData

/// Personalización del usuario para una serie (tag corto + color) — equivalente exacto de
/// `SeriesConfigEntity.kt`. Una fila por serie con sus valores por defecto
/// (`RaceSeries.defaultTag`/`defaultColorHex`) hasta que el usuario los cambie desde el
/// editor de series.
@Model
public final class SeriesConfigModel {
    // 05/09/2026: SwiftData solo admite tipos primitivos (String/Int/UUID/Bool/Double) en
    // una restricción @Attribute(.unique) — un enum propio como RaceSeries no vale, aunque
    // sea RawRepresentable, y hacía crashear la creación del ModelContainer entero (tanto
    // en tests como en la app real) con "Property type is not valid for unique
    // constraints." Se guarda el rawValue y se expone RaceSeries como propiedad computada.
    @Attribute(.unique) private var seriesRawValue: String
    public var tag: String
    public var colorHex: String

    public var series: RaceSeries {
        get { RaceSeries(rawValue: seriesRawValue) ?? .f1 }
        set { seriesRawValue = newValue.rawValue }
    }

    public init(series: RaceSeries, tag: String, colorHex: String) {
        self.seriesRawValue = series.rawValue
        self.tag = tag
        self.colorHex = colorHex
    }
}
