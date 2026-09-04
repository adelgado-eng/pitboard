package com.pitboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.standings.CarDriverEntity
import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingType
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsClass
import com.pitboard.app.standings.StandingsRepository
import com.pitboard.app.ui.theme.PodiumColors
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Orden pedido explícitamente para ELMS: LMP2, LMP2 Pro/Am, LMP3 y GT3 (28/08/2026,
// coincide con el orden real de las pestañas en europeanlemansseries.com) — nunca
// pilotos/equipos como el resto, porque ElmsStandingsSource solo guarda equipos por clase.
private val ELMS_CLASSES = listOf(
    StandingsClass.LMP2 to "LMP2",
    StandingsClass.LMP2_PRO_AM to "LMP2 Pro/Am",
    StandingsClass.LMP3 to "LMP3",
    StandingsClass.LMGT3 to "GT3"
)

// Mismo orden que las pestañas de imsa.com/weathertech/standings/ (GTP es la clase por
// defecto de esa web) — igual que ELMS, siempre como filas de coche/equipo.
private val IMSA_CLASSES = listOf(
    StandingsClass.GTP to "GTP",
    StandingsClass.LMP2 to "LMP2",
    StandingsClass.GTD_PRO to "GTD Pro",
    StandingsClass.GTD to "GTD"
)

// Orden real de las pestañas en fiawec.com (Hypercar es la clase "principal").
private val WEC_CLASSES = listOf(
    StandingsClass.HYPERCAR to "Hypercar",
    StandingsClass.LMGT3 to "LMGT3"
)

// Mismo orden que las secciones de lemanscup.com/en/page/classification.
private val LEMANS_CUP_CLASSES = listOf(
    StandingsClass.LMP3 to "LMP3",
    StandingsClass.LMP3_PRO_AM to "LMP3 Pro/Am",
    StandingsClass.GT3 to "GT3"
)

/** Categorías "por coche" (ELMS, IMSA, WEC, Le Mans Cup): sus filas de equipo representan
 *  un coche concreto, clicable para ver sus pilotos (ver CarDriversSheet) — el resto de
 *  categorías son de un piloto por coche y no tienen esta noción. */
private val CAR_BASED_CLASSES: Map<StandingsCategory, List<Pair<StandingsClass, String>>> = mapOf(
    StandingsCategory.ELMS to ELMS_CLASSES,
    StandingsCategory.IMSA to IMSA_CLASSES,
    StandingsCategory.WEC to WEC_CLASSES,
    StandingsCategory.LEMANS_CUP to LEMANS_CUP_CLASSES
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryStandingsScreen(category: StandingsCategory, onBack: () -> Unit) {
    val context = LocalContext.current
    // Pantalla de solo lectura: no hace falta un ViewModel con factory para pasarle
    // "category" — un repositorio recordado y sus Flow ya son suficiente aquí.
    val repository = remember {
        StandingsRepository(
            AppDatabase.getInstance(context).standingDao(),
            AppDatabase.getInstance(context).carDriverDao()
        )
    }

    val carClasses = CAR_BASED_CLASSES[category]
    val isCarBased = carClasses != null
    // Coche de ELMS/IMSA tocado en la lista, para el desplegable de sus pilotos — null = cerrado.
    var selectedCar by remember { mutableStateOf<StandingEntity?>(null) }
    // Foto/logo tocado, para la vista previa grande — null = cerrada. Compartido entre la
    // lista principal y CarDriversSheet (03/09/2026, pedido explícito). "isLogo" distingue
    // FOTO DE PILOTO (personas, se puede recortar sin perder lo importante — cara arriba) de
    // LOGO DE EQUIPO (recortarlo pierde parte del logo/nombre, ver ImagePreviewDialog): la
    // mayoría de logos de WEC/ELMS/etc son rectángulos anchos, así que forzar Crop dentro de
    // un cuadrado los recortaba muchísimo por los lados — bug real reportado el 03/09/2026.
    var previewImage by remember { mutableStateOf<ImagePreview?>(null) }

    var mode by remember { mutableStateOf(StandingType.DRIVER) }
    // ELMS e IMSA no separan pilotos/equipos: son varias clases que corren a la vez (ver
    // ElmsStandingsSource.kt/ImsaStandingsSource.kt), siempre como filas de coche.
    var carClass by remember { mutableStateOf(carClasses?.firstOrNull()?.first ?: StandingsClass.OVERALL) }
    val standingsClass = if (isCarBased) carClass else StandingsClass.OVERALL
    val effectiveMode = if (isCarBased) StandingType.TEAM else mode

    // Filas de coche/equipo (ELMS/IMSA/WEC/Le Mans Cup) — un único listado, como siempre.
    val rows by remember(standingsClass, effectiveMode) { repository.observe(category, standingsClass, effectiveMode) }
        .collectAsState(initial = emptyList())

    // 03/09/2026 (pedido explícito): para el resto de categorías (piloto por coche), Pilotos
    // y Equipos pasan a ser dos páginas de un HorizontalPager en vez de solo un FilterChip —
    // deslizar hacia un lado o el otro cambia de una a otra con transición, y los propios
    // chips siguen funcionando (mueven el pager a la página correspondiente). Para eso hacen
    // falta las DOS listas a la vez (no solo la del modo activo), una por página.
    val driverRows by remember(category, standingsClass) {
        if (isCarBased) flowOf(emptyList()) else repository.observe(category, standingsClass, StandingType.DRIVER)
    }.collectAsState(initial = emptyList())
    val teamRows by remember(category, standingsClass) {
        if (!isCarBased && category.hasTeamStandings) {
            repository.observe(category, standingsClass, StandingType.TEAM)
        } else {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val pagerState = rememberPagerState(pageCount = { if (category.hasTeamStandings) 2 else 1 })
    // Deslizar el pager actualiza "mode" (para que el FilterChip activo siga en pantalla)...
    LaunchedEffect(pagerState.currentPage, isCarBased) {
        if (!isCarBased) {
            mode = if (pagerState.currentPage == 0) StandingType.DRIVER else StandingType.TEAM
        }
    }
    // ...y tocar un FilterChip mueve el pager a esa página, con la misma transición animada.
    LaunchedEffect(mode, isCarBased) {
        if (!isCarBased) {
            val target = if (mode == StandingType.DRIVER) 0 else 1
            if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
        }
    }

    val lastUpdated by remember { repository.observeLastUpdated(category) }
        .collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Logo real de la categoría (URL externa, ver StandingsCategory.logoUrl,
                        // 28/08/2026 — mejor esfuerzo). Icono de trofeo si no carga.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                // Ya no es un círculo que recorta el logo — "pegatina" blanca
                                // redondeada que sigue su forma natural (28/08/2026).
                                .clip(RoundedCornerShape(10.dp))
                                // Fondo blanco fijo (no depende del tema) — mismo motivo que
                                // en la lista de categorías.
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = category.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(32.dp),
                                loading = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                error = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                        Text(category.displayName, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            lastUpdated?.let { timestamp ->
                Text(
                    "Actualizado: ${formatLastUpdated(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (carClasses != null) {
                    carClasses.forEach { (cls, label) ->
                        FilterChip(
                            selected = carClass == cls,
                            onClick = { carClass = cls },
                            label = { Text(label) }
                        )
                    }
                } else {
                    FilterChip(
                        selected = mode == StandingType.DRIVER,
                        onClick = { mode = StandingType.DRIVER },
                        label = { Text("Pilotos") }
                    )
                    if (category.hasTeamStandings) {
                        FilterChip(
                            selected = mode == StandingType.TEAM,
                            onClick = { mode = StandingType.TEAM },
                            label = { Text("Equipos") }
                        )
                    }
                }
            }

            if (isCarBased) {
                StandingsList(
                    rows = rows,
                    isCarBased = true,
                    onCarClick = { selectedCar = it },
                    // Filas de coche = siempre logo de equipo (isLogo = true) — este bloque
                    // nunca muestra pilotos (ver isCarBased más arriba).
                    onImageClick = { url, name -> previewImage = ImagePreview(url, name, isLogo = true) }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageRows = if (page == 0) driverRows else teamRows
                    StandingsList(
                        rows = pageRows,
                        isCarBased = false,
                        onCarClick = {},
                        // page 0 = Pilotos (foto de persona), page 1 = Equipos (logo).
                        onImageClick = { url, name -> previewImage = ImagePreview(url, name, isLogo = page == 1) }
                    )
                }
            }
        }
    }

    selectedCar?.let { car ->
        // El número de coche sale de row.name quitando el "#" — así es como
        // ElmsStandingsSource/ImsaStandingsSource ya lo construyen (name = "#$carNumber"),
        // no hace falta guardarlo aparte en StandingEntity solo para este caso.
        val carNumber = car.name.removePrefix("#")
        val drivers by remember(car.entrantKey) {
            repository.observeCarDrivers(category, car.standingsClass, carNumber)
        }.collectAsState(initial = emptyList())

        CarDriversSheet(
            car = car,
            drivers = drivers,
            onDismiss = { selectedCar = null },
            // Siempre pilotos (personas), nunca logo — ver ImagePreview.isLogo.
            onDriverImageClick = { url, name -> previewImage = ImagePreview(url, name, isLogo = false) }
        )
    }

    previewImage?.let { (url, label, isLogo) ->
        ImagePreviewDialog(imageUrl = url, label = label, isLogo = isLogo, onDismiss = { previewImage = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarDriversSheet(
    car: StandingEntity,
    drivers: List<CarDriverEntity>,
    onDismiss: () -> Unit,
    onDriverImageClick: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                car.name.ifBlank { car.team },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (car.team.isNotBlank()) {
                Text(
                    car.team,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (drivers.isEmpty()) {
                Text(
                    "Sin datos de pilotos todavía para este coche",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                    drivers.forEach { driver ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .let { m ->
                                        driver.photoUrl?.let { url ->
                                            m.clickable { onDriverImageClick(url, driver.name) }
                                        } ?: m
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (driver.photoUrl != null) {
                                    AsyncImage(
                                        model = driver.photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.TopCenter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                driver.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Lista de una página del HorizontalPager (Pilotos o Equipos) o del listado único de las
 *  categorías por coche — extraído para no duplicar el estado vacío ni el LazyColumn entre
 *  ambos casos (ver CategoryStandingsScreen). */
@Composable
private fun StandingsList(
    rows: List<StandingEntity>,
    isCarBased: Boolean,
    onCarClick: (StandingEntity) -> Unit,
    onImageClick: (String, String) -> Unit
) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Sin datos todavía para esta categoría",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(rows, key = { _, row -> row.entrantKey }) { index, row ->
                StandingRow(
                    row = row,
                    // Solo los coches de ELMS/IMSA abren el desplegable de pilotos — el
                    // resto de categorías no tiene ese dato (ver ElmsDriversSource/
                    // ImsaStandingsSource).
                    onClick = if (isCarBased && row.type == StandingType.TEAM) {
                        { onCarClick(row) }
                    } else null,
                    onImageClick = row.photoUrl?.let { url -> { onImageClick(url, row.name) } }
                )
                if (index < rows.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingRow(row: StandingEntity, onClick: (() -> Unit)? = null, onImageClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 30/08/2026 (3): 1º oro, 2º plata, 3º bronce (ver PodiumColors) y del 4º en
        // adelante el blanco apagado del tema, a propósito menos intenso que el de los
        // nombres. Antes solo el 1º se distinguía, en azul de marca.
        val podiumColor = PodiumColors.forPosition(row.position)
        Text(
            text = row.position.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (podiumColor != null) FontWeight.ExtraBold else FontWeight.Bold,
            color = podiumColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )

        // Piloto: avatar circular con foto real si la fuente la trae (F1, MotoGP, NASCAR,
        // IndyCar, F1 Academy y Porsche Supercup), o un icono genérico si no hay. Equipo
        // (28/08/2026, 3): fondo circular blanco fijo (no depende del tema) — igual que el
        // logo de categoría, pero en círculo en vez de esquinas redondeadas — los logos de
        // formula1.com ya son transparentes, así que resaltan bien sobre el blanco.
        if (row.type == StandingType.TEAM) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .let { if (onImageClick != null) it.clickable(onClick = onImageClick) else it },
                contentAlignment = Alignment.Center
            ) {
                if (row.photoUrl != null) {
                    AsyncImage(
                        model = row.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .let { if (onImageClick != null) it.clickable(onClick = onImageClick) else it },
                contentAlignment = Alignment.Center
            ) {
                if (row.photoUrl != null) {
                    AsyncImage(
                        model = row.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // TopCenter en vez del Center por defecto: las fotos panorámicas
                        // (600x300) que trae la mayoría de pilotos no lo notan — el recorte de
                        // esas siempre fue lateral, no vertical — pero evita que una foto de
                        // cuerpo entero sin recortar (ver MotoGpStandingsSource, A. Fernández)
                        // se quede centrada por el torso en vez de por la cara.
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (row.team.isNotBlank()) {
                Text(
                    row.team,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            "${formatPoints(row.points)} pts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Vista previa tocada — URL, nombre a mostrar y si es un LOGO de equipo (true) o una FOTO
 *  de piloto (false). La distinción decide cómo encaja la imagen en el cuadrado (ver
 *  [ImagePreviewDialog]): recortar un logo le come parte del nombre/diseño, mientras que
 *  recortar una foto de persona (con la cara arriba) no pierde nada importante. */
private data class ImagePreview(val url: String, val label: String, val isLogo: Boolean)

/** Vista previa a pantalla completa de una foto/logo (03/09/2026, pedido explícito) — se
 *  abre al tocar el avatar de un piloto/equipo en [StandingRow] o [CarDriversSheet]. Es la
 *  MISMA url que el avatar pequeño, solo que aquí Coil la pide a un tamaño casi de pantalla
 *  completa en vez de 40dp/44dp, así que decodifica una versión mucho más nítida sin
 *  necesidad de guardar ni pedir ninguna otra imagen.
 *
 *  03/09/2026 (2): Crop a pelo recortaba MUCHO los logos de equipo (la mayoría son
 *  rectángulos anchos — un logo de WEC/ELMS/etc encajado a la fuerza en un cuadrado con Crop
 *  perdía gran parte del diseño por los lados, bug real reportado). Ahora depende de
 *  [isLogo]: los logos usan Fit (nunca se recorta nada, como mucho queda banda blanca a los
 *  lados) y las fotos de piloto siguen con Crop + recorte desde arriba (llenan el cuadrado
 *  entero, y como son personas, "arriba" garantiza que la cara nunca se pierda). */
@Composable
private fun ImagePreviewDialog(imageUrl: String, label: String, isLogo: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (isLogo) Alignment.Center else Alignment.TopCenter,
                    // Límite: como mucho un cuadrado del ancho de la pantalla menos un 5% de
                    // margen a cada lado (90% en total) — aspectRatio(1f) fuerza a que la
                    // altura sea la MISMA que ese 90% de ancho, nunca más, para que una foto
                    // muy alta (retrato) no se salga verticalmente de la pantalla.
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.White),
                    loading = {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                )
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

private fun formatLastUpdated(epochMillis: Long): String {
    val formatter = SimpleDateFormat("d MMM, HH:mm", Locale("es", "ES"))
    return formatter.format(Date(epochMillis))
}
