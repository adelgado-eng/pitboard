import Foundation

/// Mismo sitio y plantilla que usaban NASCAR Cup, IndyCar, F1 Academy y ELMS
/// (autosport.com) — equivalente exacto de `MotoGpStandingsSource.kt`. Envuelve
/// `MotorsportStandingsHTMLSource` con un mapa fijo de fotos de piloto (CDN oficial de
/// motogp.com, recorte cabeza/hombros vía parámetros de query) y de logos de equipo
/// (Wikimedia), más una página de referencia para filtrar pilotos test/wildcard que la
/// tabla de puntos incluye con 0/pocos puntos.
public final class MotoGpStandingsSource: StandingsSource, @unchecked Sendable {
    private let inner: MotorsportStandingsHTMLSource

    public init() {
        let year = Calendar(identifier: .gregorian).component(.year, from: Date())
        inner = MotorsportStandingsHTMLSource(
            category: .motoGp,
            driverUrl: "https://www.autosport.com/motogp/standings/\(year)/?type=Driver",
            teamUrl: "https://www.autosport.com/motogp/standings/\(year)/?type=Team",
            knownRosterUrl: "https://www.motorsportmagazine.com/articles/motorcycles/motogp/motogp-2026-rider-line-ups-complete-grid-for-next-season/",
            driverPhotoUrls: Self.riderPhotoUrls,
            teamLogoUrls: Self.teamLogoUrls
        )
    }

    public var category: StandingsCategory { inner.category }
    public func fetch(nowUtc: Date) async throws -> [StandingDraft] { try await inner.fetch(nowUtc: nowUtc) }

    /// CDN de imágenes de motogp.com — rutas opacas (fecha de subida + id), copiadas de
    /// la ficha de cada piloto en motogp.com/en/riders/motogp.
    private static let photoHost = "https://resources.motogp.pulselive.com/photo-resources/"

    /// La foto oficial es un plano de cuerpo completo (1920x2883) — estos parámetros de
    /// query, que entiende el propio CDN, devuelven un recorte 600x300 anclado arriba
    /// (cabeza y hombros) en vez del recorte central por defecto, que caía a la altura
    /// del pecho en el avatar circular de la fila.
    private static let headCrop = "?width=600&height=300&fit=crop"

    /// Foto oficial 2026 por piloto, clave "inicial + apellido" (ver `photoKey` en
    /// `MotorsportStandingsHTMLSource`).
    private static let riderPhotoUrls: [String: String] = [
        "j zarco": photoHost + "2026/02/05/49611a81-9931-4191-9820-068b73b54f99/y0R5f9H5.png" + headCrop,
        "t razgatlioglu": photoHost + "2026/02/05/743b343d-2b20-40a7-8ae0-e4f5a273503d/5Zq5W4Wt.png" + headCrop,
        "l marini": photoHost + "2026/07/03/8faf6cb4-ed2c-446c-b897-723d305abf7e/S6m6LRHY.png" + headCrop,
        "d moreira": photoHost + "2026/07/03/d67fcafc-2497-4b80-9f66-8be488c5e629/i5riGt65.png" + headCrop,
        "m vinales": photoHost + "2026/07/03/caf42f15-85d6-4bd0-8f8e-8a726a2a4ccf/7QBpFmT4.png" + headCrop,
        "f quartararo": photoHost + "2026/02/05/73805511-aba7-4e37-9361-4e4b35da50fe/L72keLEc.png" + headCrop,
        "f morbidelli": photoHost + "2026/07/03/d0660231-7f0f-4af3-b2bd-dbb3eae14686/srwszjyQ.png" + headCrop,
        "e bastianini": photoHost + "2026/02/05/32fd7aeb-d765-45d8-9da3-cc3ca25689cf/7pX3VTcG.png" + headCrop,
        "r fernandez": photoHost + "2026/07/03/597deb8c-1eb1-41b2-87ea-557829e3564b/G8ukTN8w.png" + headCrop,
        "b binder": photoHost + "2026/05/15/bf875f3c-d9d0-4f8b-aa9a-124c7b9145b6/33-MGP-Brad-Binder-Rider-Official-x12-_DSC4264-1-.png" + headCrop,
        "j mir": photoHost + "2026/07/03/1237b6f0-80a6-4a2e-91ae-3cf252ce86fb/A9TKY6Q5.png" + headCrop,
        "p acosta": photoHost + "2026/07/03/7ddd1dca-4db1-430a-949b-a5b8c87aae8d/YaWdUVdE.png" + headCrop,
        "a rins": photoHost + "2026/07/03/b58f46cd-1c76-46ed-923b-94f03ddb1ce3/6zfxJvst.png" + headCrop,
        "j miller": photoHost + "2026/06/05/85d57a3c-8997-4753-9801-a99f72fe9289/43-MGP-Jack-Miller-Rider-Official-x12-_DSC4139.png" + headCrop,
        "f di giannantonio": photoHost + "2026/07/03/4f29c6d0-38bb-45a7-8f1e-b90a7b4fd877/VEmGm1Zi.png" + headCrop,
        "f aldeguer": photoHost + "2026/07/03/ebaf3ac3-b2ba-4604-ab0c-def51824e575/6NErto4j.png" + headCrop,
        "f bagnaia": photoHost + "2026/02/05/9772f542-8f9b-4a1c-b7a3-a5fe8f041f75/IfzOWPi2.png" + headCrop,
        "m bezzecchi": photoHost + "2026/05/29/440d1ac6-83cd-4107-a831-efb8dd1eaa77/72-MGP-Marco-Bezzecchi-Rider-Official_DSC03346.png" + headCrop,
        "a marquez": photoHost + "2026/02/05/71b70d16-3d66-4374-abf0-e439f76a13aa/WezEeZAR.png" + headCrop,
        "a ogura": photoHost + "2026/07/03/377fe619-38dd-4a10-a951-2ea39c142760/gKJEk1g6.png" + headCrop,
        "j martin": photoHost + "2026/05/29/56a55c22-98dd-4b18-85a2-f2919337776c/89-MGP-Jorge-Martin-Rider-Official_DSC03201.png" + headCrop,
        "m marquez": photoHost + "2026/07/03/b5f58e67-9b76-4e70-93c6-6672a6abc649/L0F4WbbF.png" + headCrop,
        // Sin headCrop: viene de photos.motogp.com (la API), no del CDN de photo-resources
        // de arriba, y ese dominio ignora los parámetros de recorte.
        "a fernandez": "https://photos.motogp.com/riders/e/b/eb7f90b1-9373-4089-b2f5-adbc234a3526/2026/profile/main-841308.png"
    ]

    private static let teamLogoUrls: [String: String] = [
        "aprilia racing team": "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Aprilia_Racing_Logo.svg/1280px-Aprilia_Racing_Logo.svg.png",
        "trackhouse racing team": "https://upload.wikimedia.org/wikipedia/en/d/d2/Trackhouse_Racing_Logo.png",
        "ducati team": "https://upload.wikimedia.org/wikipedia/en/thumb/8/8b/Ducati_Corse_logo_%28new%29.svg/1280px-Ducati_Corse_logo_%28new%29.svg.png",
        "team vr46": "https://upload.wikimedia.org/wikipedia/commons/1/13/Pertamina_Enduro_VR46_Racing_Team_-_logo.jpg",
        "red bull ktm factory racing": "https://upload.wikimedia.org/wikipedia/en/a/a8/Red_Bull_KTM_Factory_Racing_logo.jpg",
        "gresini racing": "https://upload.wikimedia.org/wikipedia/en/f/f9/Gresini_Racing_Logo_2017.png",
        "honda hrc": "https://upload.wikimedia.org/wikipedia/en/1/14/Honda_HRC_Castrol_logo.png",
        "team lcr": "https://upload.wikimedia.org/wikipedia/en/1/13/LCR_logo_2021.png",
        "tech 3": "https://upload.wikimedia.org/wikipedia/en/2/26/Tech_3_logo.png",
        "yamaha factory racing": "https://upload.wikimedia.org/wikipedia/en/6/64/Yamaha_motogp_team.png",
        "pramac racing": "https://upload.wikimedia.org/wikipedia/en/1/1d/Prima_Pramac_Racing_logo.jpg"
    ]
}
