import PitBoardKit

/// Una fila de `SeriesConfigModel` por cada `RaceSeries`, con sus valores de fábrica
/// (`defaultTag`/`defaultColorHex`) — extraído a una función libre porque la misma lista
/// se sembraba por duplicado en `RootTabView.seedSeriesConfigIfNeeded()` (arranque real,
/// solo si la tabla está vacía) y en `UITestFixtures.seedIfNeeded()` (modo test, siempre).
/// Las instancias devueltas no están insertadas en ningún `ModelContext` todavía — quien
/// llama decide dónde y cuándo insertarlas.
func makeDefaultSeriesConfigs() -> [SeriesConfigModel] {
    RaceSeries.allCases.map { series in
        SeriesConfigModel(series: series, tag: series.defaultTag, colorHex: series.defaultColorHex)
    }
}
