import Foundation

/// driverdb.com — equivalente exacto de `PorscheSupercupStandingsSource.kt`. Envuelve
/// `DriverDbStandingsSource` con los logos de equipo de la propia página de equipos de
/// driverdb (driverdb.com/championships/porsche-supercup/2026/teams), cuyo nombre de
/// equipo no siempre coincide exactamente con el de la tabla de clasificación — las
/// claves de abajo usan el nombre TAL COMO SALE en la tabla, que es el que llega aquí.
///
/// HONESTO — pilotos: esta sigue siendo la categoría con peor cobertura de fotos de toda
/// la app (ver detalle en el Kotlin original) — la mayoría de los ~35 pilotos de la
/// parrilla no tienen ninguna foto verificable en las fuentes consultadas y salen con el
/// icono genérico.
public final class PorscheSupercupStandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: DriverDbStandingsSource

    public init() {
        inner = DriverDbStandingsSource(
            category: .porscheSupercup,
            slug: "porsche-supercup",
            teamLogoUrls: Self.teamLogoUrls
        )
    }

    public var category: StandingsCategory { inner.category }
    public func fetch(nowUtc: Date) async throws -> [StandingDraft] { try await inner.fetch(nowUtc: nowUtc) }

    private static let logoHost = "https://storage.googleapis.com/driverdb-media/teams/"

    private static let teamLogoUrls: [String: String] = [
        "schumacher clrt": logoHost + "344/seasons/55385/logo_1784539957.png",
        // "BWT Lechner Racing" en la página de equipos de driverdb.
        "lechner racing": logoHost + "489/seasons/55385/logo_1784539785.png",
        "martinet by almeras": logoHost + "1216/seasons/55385/logo_1784551581.png",
        "gp elite": logoHost + "491/seasons/55385/logo_1784540264.png",
        "proton competition": logoHost + "228/seasons/55385/logo_1784540047.png",
        // "Looping by CarTech" en la página de equipos de driverdb.
        "cartech motorsport": logoHost + "3676/seasons/55385/logo_1784542591.png",
        "dinamic motorsport": logoHost + "173/seasons/55385/logo_1784540514.png",
        "target competition": logoHost + "481/seasons/55385/logo_1784550976.png",
        "ombra racing": logoHost + "492/seasons/55385/logo_1784550354.png",
        "rgb racing team": logoHost + "3761/seasons/55385/logo_1784550729.png"
    ]
}
