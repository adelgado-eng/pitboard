import Foundation

/// driverdb.com en vez de autosport.com — autosport no traía fotos para F1 Academy.
/// driverdb trae más filas que la parrilla confirmada de la temporada (el resto son
/// entradas sin confirmar/reserva a 0 puntos) — se filtran contra el directorio de
/// pilotas de motorsport.com (ver `RosterNameFilter`).
///
/// Logos y fotos: oficiales de f1academy.com (Racing-Series/Teams y Racing-Series/Drivers),
/// mismo CDN que F2/F3 — driverdb.com solo tiene foto real para una minoría de las
/// pilotas, así que `driverPhotoUrls` actúa de respaldo; quien no tenga entrada aquí
/// tampoco, se queda con el icono por defecto (nunca desaparece de la clasificación).
public final class F1AcademyStandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: DriverDbStandingsSource

    public init() {
        inner = DriverDbStandingsSource(
            category: .f1Academy,
            slug: "f1-academy",
            knownRosterUrl: "https://www.motorsport.com/f1-academy/drivers/",
            teamLogoUrls: Self.teamLogoUrls,
            driverPhotoUrls: Self.driverPhotoUrls
        )
    }

    public var category: StandingsCategory { inner.category }

    public func fetch(nowUtc: Date) async throws -> [StandingDraft] {
        try await inner.fetch(nowUtc: nowUtc)
    }

    private static let driverPhotoHost = "https://res.cloudinary.com/prod-f2f3/image/upload/"

    private static let teamLogoUrls: [String: String] = [
        "art grand prix": driverPhotoHost + "v1736943411/FA/Global/teams/logos/F1A_Teams_ART.png",
        "campos racing": driverPhotoHost + "v1736943446/FA/Global/teams/logos/F1A_Teams_Campos.png",
        "hitech grand prix": driverPhotoHost + "v1768219948/FA/Global/teams/logos/Hitech_Logo_2026.png",
        "mp motorsport": driverPhotoHost + "v1736943449/FA/Global/teams/logos/F1A_Teams_MP.png",
        "prema racing": driverPhotoHost + "v1736943450/FA/Global/teams/logos/F1A_Teams_Prema.png",
        "rodin motorsport": driverPhotoHost + "v1736943452/FA/Global/teams/logos/F1A_Teams_Rodin.png"
    ]

    /// Claves = nombre de la pilota normalizado tal como lo trae driverdb.com (ver
    /// `TextNormalizer.normalize`: minúsculas, sin tildes ni signos).
    private static let driverPhotoUrls: [String: String] = [
        "alisha palmowski": driverPhotoHost + "v1772642010/FA/Global/drivers/2026/Alisha_Palmowski_Profile.jpg",
        "emma felbermayr": driverPhotoHost + "v1772642018/FA/Global/drivers/2026/Emma_Felbermayr_Profile.jpg",
        "nina gademan": driverPhotoHost + "v1772642029/FA/Global/drivers/2026/Nina_Gademan_Profile.jpg",
        "alba larsen": driverPhotoHost + "v1772642008/FA/Global/drivers/2026/Alba_Larsen_Profile.jpg",
        "megan bruce": driverPhotoHost + "v1772642027/FA/Global/drivers/2026/Megan_Bruce_Profile.jpg",
        "payton westcott": driverPhotoHost + "v1773172129/FA/Global/drivers/2026/Payton_Profile.jpg",
        "ella lloyd": driverPhotoHost + "v1772642013/FA/Global/drivers/2026/Ella_Lloyd_Profile.jpg",
        "mathilda paatz": driverPhotoHost + "v1773172140/FA/Global/drivers/2026/Mathilda_Profile.jpg",
        "natalia granada ferrero": driverPhotoHost + "v1773263612/FA/Global/drivers/2026/Natalia_Granada_Profile.jpg",
        "rafaela ferreira": driverPhotoHost + "v1772642034/FA/Global/drivers/2026/Rafaela_Ferreira_Profile.jpg",
        "rachel robertson": driverPhotoHost + "v1772642031/FA/Global/drivers/2026/Rachel_Robertson_Profile.jpg",
        "lisa billard": driverPhotoHost + "v1772642025/FA/Global/drivers/2026/Lisa_Billard_Profile.jpg",
        "kaylee countryman": driverPhotoHost + "v1772642023/FA/Global/drivers/2026/Kaylee_Countryman_Profile.jpg",
        "ava dobson": driverPhotoHost + "v1772642012/FA/Global/drivers/2026/Ava_Dobson_Profile.jpg",
        "esmee kosterman": driverPhotoHost + "v1772642019/FA/Global/drivers/2026/Esmee_Kosterman_Profile.jpg",
        "ella stevens": driverPhotoHost + "v1772642015/FA/Global/drivers/2026/Ella_Stevens_Profile.jpg",
        "jade jacquet": driverPhotoHost + "v1772642021/FA/Global/drivers/2026/Jade_Jacquet_Profile.jpg",
        // "Zoe Florescu-Potolea" -> el guion desaparece al normalizar (no es letra/número
        // ni espacio), así que "Florescu-Potolea" se queda pegado en "florescupotolea".
        "zoe florescupotolea": driverPhotoHost + "f_auto/q_auto/v1786029316/FA/Global/drivers/2026/Zoe_Florescu_Profile.jpg"
    ]
}
