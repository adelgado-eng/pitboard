package com.pitboard.app.standings.sources

import com.pitboard.app.standings.StandingsCategory

/**
 * nascar.com es JavaScript puro para el LISTADO de la tabla (sin tabla en el HTML crudo), así
 * que la tabla "de autoridad" sigue viniendo de espn.com/racing/standings (28/08/2026) —
 * tobychristie.com, que se usaba antes, resultó ser una instantánea congelada de una carrera
 * concreta de la temporada, no una página que se actualice sola.
 *
 * 02/09/2026 (2): las fotos ya NO salen de las páginas de perfil individuales de nascar.com
 * (`/drivers/{slug}/`) — probado con Ty Gibbs, Casey Mears y B.J. McLeod: la de Gibbs
 * redirige a un archivo de noticias (la foto salía siendo la miniatura de una noticia
 * cualquiera sobre otro piloto), la de Mears es una foto de 2017 sin actualizar, y la de
 * McLeod ni siquiera existe con ese slug (404). nascar.com no mantiene bien la ficha de
 * pilotos que no corren la temporada completa.
 *
 * En su lugar, la foto sale DIRECTAMENTE de la propia tabla de espn.com/racing/standings, sin
 * ninguna petición extra: cada fila ya trae un enlace a `/racing/driver/_/id/{id}/{slug}`, y
 * ese id compuesto con el CDN de fotos de ESPN (a.espncdn.com/combiner/i?img=/i/headshots/
 * rpm/players/full/{id}.png) da una foto de estudio fiable — comprobado a mano con los 3
 * pilotos de arriba, las 3 correctas y de calidad profesional.
 *
 * 04/09/2026: se sube el tamaño pedido al combiner de 350x350 a 500x500 — comprobado a mano
 * (Hamlin, Blaney, Reddick, Larson, Elliott...) que ESPN mantiene esta foto al día temporada
 * a temporada (uniforme/patrocinadores 2026 correctos), pero a 350px se veía pequeña/poco
 * nítida en el avatar grande de CategoryStandingsScreen y en la vista previa a pantalla
 * completa — mismo criterio que el ajuste de calidad de IndyCarStandingsSource.
 */
class NascarStandingsSource : OfficialRosterStandingsSource(
    category = StandingsCategory.NASCAR_CUP,
    rosterUrl = "https://www.espn.com/racing/standings",
    driverDbSlug = "nascar-sprint-cup-series",
    rosterPhotoUrlExtractor = { nameCell ->
        // La celda de nombre ya trae un único <a> (el mismo que usa el propio fetchRoster()
        // para sacar el nombre) con href tipo "/racing/driver/_/id/4531/ryan-blaney" — de ahí
        // se saca el id de ESPN sin necesidad de filtrar por atributo en el selector.
        nameCell.selectFirst("a")
            ?.attr("href")
            ?.let { href -> Regex("/id/(\\d+)/").find(href)?.groupValues?.get(1) }
            ?.let { espnId -> "https://a.espncdn.com/combiner/i?img=/i/headshots/rpm/players/full/$espnId.png&w=500&h=500" }
    }
)
