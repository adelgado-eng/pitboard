package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory
import java.time.Year

/**
 * Mismo sitio y plantilla que usaban NASCAR Cup, IndyCar, F1 Academy y ELMS (autosport.com).
 * La tabla de puntos trae 29 filas cuando la parrilla a tiempo completo son 22 — el resto
 * son pilotos test/wildcard (Crutchlow, Folger, Pirro, Savadori, entre otros) a 0 o pocos
 * puntos. Se filtran contra un artículo con la alineación 2026 confirmada (28/08/2026).
 *
 * HONESTO: esa página de referencia es un artículo de prensa, no una web oficial que se
 * vaya actualizando sola durante la temporada — si hay un cambio de piloto a mitad de año,
 * puede quedar desactualizada (ver RosterNameFilter para el comportamiento si falla).
 *
 * FOTOS DE PILOTO (30/08/2026, corregido). Antes se intentaba adivinar la página de
 * Wikipedia de cada piloto a partir de su nombre y leer su etiqueta og:image. Nunca
 * funcionó, y por eso MotoGP no mostraba ni una sola foto: autosport.com abrevia el nombre
 * de pila en su tabla de puntos ("J. Martin", "M. Marquez"), así que la URL que se
 * construía era en/wiki/J._Martin — un artículo que no existe, o peor, uno de otra persona
 * distinta. Además costaba una petición HTTP por piloto en cada sincronización.
 *
 * Ahora salen de un mapa fijo, RIDER_PHOTO_URLS, con la foto oficial de estudio de la
 * temporada 2026 de cada piloto — la misma que motogp.com usa en su propia ficha de piloto
 * y en su clasificación, servida por su CDN de imágenes. Cada piloto aparece con el mono y
 * los colores del equipo en el que corre ESTE año, y las 22 son de la misma sesión de fotos
 * oficial, así que la lista se ve homogénea en vez de una mezcla de fotos de carrera de
 * distintos años y equipos. Las 22 URLs comprobadas una a una (cargan, y es el piloto y el
 * equipo correctos) el 30/08/2026.
 *
 * Los 6 pilotos restantes de la tabla (Lecuona, P. Espargaro, Crutchlow, Folger, Pirro,
 * Savadori) se quedan a propósito sin entrada: son sustitutos/test y MotoGP no publica foto
 * oficial de estudio 2026 de ninguno de ellos, solo fotos de prensa sueltas que desentonarían
 * con el resto. Si el filtro de parrilla no los descarta, salen con el icono genérico — nunca
 * desaparecen de la clasificación.
 *
 * A. Fernández (Augusto, wildcard de Yamaha) SÍ tiene foto oficial 2026 en la API de MotoGP
 * (02/09/2026) — pero a diferencia de las 22 de arriba, es de cuerpo entero sin recortar: su
 * perfil no está en el listado público de pilotos (motogp.com/en/riders/motogp, donde sí
 * viven las 22 ya recortadas), solo en la API interna, y esa URL en concreto ignora
 * cualquier parámetro de recorte. Se usa igual (CategoryStandingsScreen ancla el recorte
 * arriba en vez de al centro para que no salga por el torso), a costa de una foto bastante
 * más pesada (~4 MB) que las demás.
 *
 * El logo de equipo sale de otro mapa fijo, TEAM_LOGO_URLS — las claves son el nombre EXACTO
 * tal como lo trae la tabla de equipos de autosport.com (comprobado a mano: "Aprilia Racing
 * Team", "Ducati Team", "Team VR46", "Team LCR", "Tech 3"... no siempre coincide con el
 * nombre comercial completo del equipo) — los 11 equipos de la parrilla 2026, cada URL
 * comprobada una a una (carga, es una imagen real y se ve bien sobre el círculo blanco de la
 * fila) el 30/08/2026.
 */
class MotoGpStandingsSource : MotorsportStandingsHtmlSource(
    category = StandingsCategory.MOTOGP,
    driverUrl = "https://www.autosport.com/motogp/standings/${Year.now().value}/?type=Driver",
    teamUrl = "https://www.autosport.com/motogp/standings/${Year.now().value}/?type=Team",
    knownRosterUrl = "https://www.motorsportmagazine.com/articles/motorcycles/motogp/motogp-2026-rider-line-ups-complete-grid-for-next-season/",
    driverPhotoUrls = RIDER_PHOTO_URLS,
    teamLogoUrls = TEAM_LOGO_URLS
) {
    companion object {
        /** CDN de imágenes de motogp.com. Las rutas son opacas (fecha de subida + id), no se
         *  pueden construir a partir del nombre del piloto: están copiadas de la ficha de cada
         *  piloto en motogp.com/en/riders/motogp. */
        private const val PHOTO_HOST = "https://resources.motogp.pulselive.com/photo-resources/"

        /** La foto oficial es un plano entero de cuerpo completo (1920x2883), pensada para la
         *  ficha del piloto. Metida tal cual en el avatar circular de 40dp de la fila, con
         *  ContentScale.Crop, el recorte cae a la altura del pecho y no se ve la cara. Estos
         *  parámetros los entiende el propio CDN y devuelven un recorte 600x300 anclado a la
         *  parte de arriba de la foto — es decir, cabeza y hombros — que ya centrado en el
         *  círculo se ve como un retrato normal. Comprobado con los 22 pilotos: en todos cae
         *  bien la cara, aunque la pose y la altura de cada uno no sean idénticas.
         *
         *  OJO: el otro dominio de fotos de MotoGP (photos.motogp.com, el que devuelve su API
         *  pública) sirve exactamente las mismas fotos pero IGNORA estos parámetros y devuelve
         *  siempre el original de cuerpo entero — por eso las URLs de aquí son las del CDN. */
        private const val HEAD_CROP = "?width=600&height=300&fit=crop"

        /** Foto oficial 2026 por piloto, con la clave "inicial + apellido" que genera
         *  photoKey() en MotorsportStandingsHtmlSource (ver allí por qué la clave es así y no
         *  el nombre tal cual). */
        private val RIDER_PHOTO_URLS: Map<String, String> = mapOf(
            "j zarco"           to PHOTO_HOST + "2026/02/05/49611a81-9931-4191-9820-068b73b54f99/y0R5f9H5.png" + HEAD_CROP, // Johann Zarco
            "t razgatlioglu"    to PHOTO_HOST + "2026/02/05/743b343d-2b20-40a7-8ae0-e4f5a273503d/5Zq5W4Wt.png" + HEAD_CROP, // Toprak Razgatlioglu
            "l marini"          to PHOTO_HOST + "2026/07/03/8faf6cb4-ed2c-446c-b897-723d305abf7e/S6m6LRHY.png" + HEAD_CROP, // Luca Marini
            "d moreira"         to PHOTO_HOST + "2026/07/03/d67fcafc-2497-4b80-9f66-8be488c5e629/i5riGt65.png" + HEAD_CROP, // Diogo Moreira
            "m vinales"         to PHOTO_HOST + "2026/07/03/caf42f15-85d6-4bd0-8f8e-8a726a2a4ccf/7QBpFmT4.png" + HEAD_CROP, // Maverick Viñales
            "f quartararo"      to PHOTO_HOST + "2026/02/05/73805511-aba7-4e37-9361-4e4b35da50fe/L72keLEc.png" + HEAD_CROP, // Fabio Quartararo
            "f morbidelli"      to PHOTO_HOST + "2026/07/03/d0660231-7f0f-4af3-b2bd-dbb3eae14686/srwszjyQ.png" + HEAD_CROP, // Franco Morbidelli
            "e bastianini"      to PHOTO_HOST + "2026/02/05/32fd7aeb-d765-45d8-9da3-cc3ca25689cf/7pX3VTcG.png" + HEAD_CROP, // Enea Bastianini
            "r fernandez"       to PHOTO_HOST + "2026/07/03/597deb8c-1eb1-41b2-87ea-557829e3564b/G8ukTN8w.png" + HEAD_CROP, // Raúl Fernández
            "b binder"          to PHOTO_HOST + "2026/05/15/bf875f3c-d9d0-4f8b-aa9a-124c7b9145b6/33-MGP-Brad-Binder-Rider-Official-x12-_DSC4264-1-.png" + HEAD_CROP, // Brad Binder
            "j mir"             to PHOTO_HOST + "2026/07/03/1237b6f0-80a6-4a2e-91ae-3cf252ce86fb/A9TKY6Q5.png" + HEAD_CROP, // Joan Mir
            "p acosta"          to PHOTO_HOST + "2026/07/03/7ddd1dca-4db1-430a-949b-a5b8c87aae8d/YaWdUVdE.png" + HEAD_CROP, // Pedro Acosta
            "a rins"            to PHOTO_HOST + "2026/07/03/b58f46cd-1c76-46ed-923b-94f03ddb1ce3/6zfxJvst.png" + HEAD_CROP, // Álex Rins
            "j miller"          to PHOTO_HOST + "2026/06/05/85d57a3c-8997-4753-9801-a99f72fe9289/43-MGP-Jack-Miller-Rider-Official-x12-_DSC4139.png" + HEAD_CROP, // Jack Miller
            "f di giannantonio" to PHOTO_HOST + "2026/07/03/4f29c6d0-38bb-45a7-8f1e-b90a7b4fd877/VEmGm1Zi.png" + HEAD_CROP, // Fabio Di Giannantonio
            "f aldeguer"        to PHOTO_HOST + "2026/07/03/ebaf3ac3-b2ba-4604-ab0c-def51824e575/6NErto4j.png" + HEAD_CROP, // Fermín Aldeguer
            "f bagnaia"         to PHOTO_HOST + "2026/02/05/9772f542-8f9b-4a1c-b7a3-a5fe8f041f75/IfzOWPi2.png" + HEAD_CROP, // Francesco Bagnaia
            "m bezzecchi"       to PHOTO_HOST + "2026/05/29/440d1ac6-83cd-4107-a831-efb8dd1eaa77/72-MGP-Marco-Bezzecchi-Rider-Official_DSC03346.png" + HEAD_CROP, // Marco Bezzecchi
            "a marquez"         to PHOTO_HOST + "2026/02/05/71b70d16-3d66-4374-abf0-e439f76a13aa/WezEeZAR.png" + HEAD_CROP, // Álex Márquez
            "a ogura"           to PHOTO_HOST + "2026/07/03/377fe619-38dd-4a10-a951-2ea39c142760/gKJEk1g6.png" + HEAD_CROP, // Ai Ogura
            "j martin"          to PHOTO_HOST + "2026/05/29/56a55c22-98dd-4b18-85a2-f2919337776c/89-MGP-Jorge-Martin-Rider-Official_DSC03201.png" + HEAD_CROP, // Jorge Martín
            "m marquez"         to PHOTO_HOST + "2026/07/03/b5f58e67-9b76-4e70-93c6-6672a6abc649/L0F4WbbF.png" + HEAD_CROP, // Marc Márquez
            // Sin HEAD_CROP: viene de photos.motogp.com (la API), no del CDN de fotos-resources
            // de arriba, y ese dominio ignora los parámetros de recorte (ver comentario de clase).
            "a fernandez"       to "https://photos.motogp.com/riders/e/b/eb7f90b1-9373-4089-b2f5-adbc234a3526/2026/profile/main-841308.png" // Augusto Fernández
        )

        private val TEAM_LOGO_URLS: Map<String, String> = mapOf(
            "aprilia racing team" to "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Aprilia_Racing_Logo.svg/1280px-Aprilia_Racing_Logo.svg.png",
            "trackhouse racing team" to "https://upload.wikimedia.org/wikipedia/en/d/d2/Trackhouse_Racing_Logo.png",
            "ducati team" to "https://upload.wikimedia.org/wikipedia/en/thumb/8/8b/Ducati_Corse_logo_%28new%29.svg/1280px-Ducati_Corse_logo_%28new%29.svg.png",
            "team vr46" to "https://upload.wikimedia.org/wikipedia/commons/1/13/Pertamina_Enduro_VR46_Racing_Team_-_logo.jpg",
            "red bull ktm factory racing" to "https://upload.wikimedia.org/wikipedia/en/a/a8/Red_Bull_KTM_Factory_Racing_logo.jpg",
            "gresini racing" to "https://upload.wikimedia.org/wikipedia/en/f/f9/Gresini_Racing_Logo_2017.png",
            "honda hrc" to "https://upload.wikimedia.org/wikipedia/en/1/14/Honda_HRC_Castrol_logo.png",
            "team lcr" to "https://upload.wikimedia.org/wikipedia/en/1/13/LCR_logo_2021.png",
            "tech 3" to "https://upload.wikimedia.org/wikipedia/en/2/26/Tech_3_logo.png",
            "yamaha factory racing" to "https://upload.wikimedia.org/wikipedia/en/6/64/Yamaha_motogp_team.png",
            "pramac racing" to "https://upload.wikimedia.org/wikipedia/en/1/1d/Prima_Pramac_Racing_logo.jpg"
        )
    }
}
