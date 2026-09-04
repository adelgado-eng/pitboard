package com.pitboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.AppTheme
import com.pitboard.app.schedule.RaceScheduleRepository
import com.pitboard.app.standings.ConnectivityHelper
import com.pitboard.app.standings.StandingsCategory
import com.pitboard.app.standings.StandingsRepository
import com.pitboard.app.i18n.AppLanguage
import com.pitboard.app.i18n.LocalAppLanguage
import com.pitboard.app.ui.CategoryStandingsScreen
import com.pitboard.app.ui.EventsScreen
import com.pitboard.app.ui.LanguagePickerScreen
import com.pitboard.app.ui.NotificationPermissionOnboarding
import com.pitboard.app.ui.SettingsScreen
import com.pitboard.app.ui.StandingsScreen
import com.pitboard.app.ui.theme.PitBoardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PitBoardApp()
        }
    }
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

/**
 * Pestañas de la barra inferior.
 *
 * 01/09/2026: "Calendarios" y "Categorías" desaparecen — el calendario ya no se importa a
 * mano (ver RaceSeries y com.pitboard.app.schedule), así que Eventos es ahora la pantalla de
 * inicio y ya no necesita esas dos pestañas de gestión.
 *
 * 30/08/2026 (1): "Clasificaciones" ya NO es fija — solo aparece si el interruptor de
 * Ajustes está activado. Mientras está apagado la app no sale a internet para nada, así
 * que la pestaña solo llevaba a una pantalla vacía que decía "actívalas en Ajustes";
 * ahora sencillamente no está en la barra.
 */
private fun bottomDestinations(standingsEnabled: Boolean): List<BottomDestination> = listOfNotNull(
    BottomDestination("events", "Eventos", Icons.Default.Event),
    BottomDestination("standings", "Clasificaciones", Icons.Default.EmojiEvents)
        .takeIf { standingsEnabled },
    BottomDestination("settings", "Ajustes", Icons.Default.Settings)
)

@Composable
fun PitBoardApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettingsRepository = remember { AppSettingsRepository(context) }
    val appTheme by appSettingsRepository.appTheme.collectAsState(initial = AppTheme.SYSTEM)
    // Interruptor de clasificaciones (Ajustes). El valor inicial "false" es el mismo que
    // devuelve AppSettingsRepository por defecto, así que la pestaña no parpadea al
    // arrancar: si está activada, aparece en cuanto DataStore emite el valor guardado.
    val standingsEnabled by appSettingsRepository.standingsEnabled.collectAsState(initial = false)
    // null = todavía no ha pasado por el selector de idioma de primer arranque (ver más
    // abajo) — "initial = null" a propósito, NUNCA se asume español por defecto aquí: eso
    // dejaría ver la app un instante en español antes de que DataStore confirme que
    // realmente no hay idioma guardado.
    val appLanguage by appSettingsRepository.appLanguage.collectAsState(initial = null)

    CompositionLocalProvider(LocalAppLanguage provides (appLanguage ?: AppLanguage.SPANISH)) {

    // Primer arranque de verdad: elegir idioma es lo PRIMERO de todo, antes incluso que el
    // permiso de notificaciones — pedido explícito ("al instalarla te pida cuál quieres").
    if (appLanguage == null) {
        PitBoardTheme(appTheme = appTheme) {
            LanguagePickerScreen(onLanguageChosen = { chosen ->
                scope.launch { appSettingsRepository.setAppLanguage(chosen) }
            })
        }
        return@CompositionLocalProvider
    }

    // Primer arranque de la app: se pide el permiso de avisos antes de nada. El resultado
    // deja el interruptor de Ajustes activado (aceptado) o desactivado (rechazado).
    NotificationPermissionOnboarding(appSettingsRepository)

    // 03/09/2026 — SINCRONIZACIÓN DE ARRANQUE, corregida tras probarla: la PRIMERA vez
    // que se abre la app (instalación nueva) se actualizan Eventos Y Clasificaciones
    // antes de enseñar nada, con pantalla de carga — así una serie añadida a mitad de
    // sesión (ej. Fórmula E) no tiene que esperar al ciclo diario de WorkManager para
    // aparecer. Pero en el RESTO de aperturas ya NO se toca Eventos (queda con su ciclo
    // diario de siempre) y solo se refresca Clasificaciones, EN SEGUNDO PLANO sin
    // pantalla de carga, y solo si el interruptor de Ajustes está activado — pedido
    // explícito tras ver que sincronizar Eventos en cada apertura no aportaba nada y
    // sí gastaba datos de más. La marca de "ya hice la primera" vive en DataStore
    // (ver AppSettingsRepository.hasCompletedFirstSync), no en si la BD está vacía,
    // para que sea un evento que solo pasa UNA vez de verdad.
    var startupSyncDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val alreadyDidFirstSync = appSettingsRepository.hasCompletedFirstSyncNow()

        if (!alreadyDidFirstSync) {
            try {
                if (ConnectivityHelper.isOnline(context)) {
                    withTimeoutOrNull(STARTUP_SYNC_TIMEOUT_MS) {
                        val database = AppDatabase.getInstance(context)
                        coroutineScope {
                            val eventsDeferred = async(Dispatchers.IO) {
                                runCatching { RaceScheduleRepository(database.eventDao()).syncAll() }
                            }
                            val standingsDeferred = async(Dispatchers.IO) {
                                runCatching { StandingsRepository(database.standingDao(), database.carDriverDao()).syncAll() }
                            }
                            eventsDeferred.await()
                            standingsDeferred.await()
                        }
                    }
                }
            } catch (_: Exception) {
                // Silencioso — la app se abre igual con lo que ya hubiera en caché; el
                // botón "Actualizar" de cada pantalla sigue disponible a mano.
            }
            // Se marca pase lo que pase (éxito, fallo o sin red): si esta primera vez no
            // salió bien, no queremos repetir la pantalla de carga completa en cada
            // apertura siguiente — el ciclo diario/semanal en segundo plano ya insiste.
            appSettingsRepository.setHasCompletedFirstSync(true)
            startupSyncDone = true
        } else {
            // Aperturas normales: nunca se espera nada aquí, la app se enseña al
            // instante. Clasificaciones se refresca aparte, en segundo plano, solo si
            // el usuario la tiene activada.
            startupSyncDone = true
            if (appSettingsRepository.isStandingsEnabledNow() && ConnectivityHelper.isOnline(context)) {
                launch(Dispatchers.IO) {
                    val database = AppDatabase.getInstance(context)
                    runCatching { StandingsRepository(database.standingDao(), database.carDriverDao()).syncAll() }
                }
            }
        }
    }

    PitBoardTheme(appTheme = appTheme) {
        if (!startupSyncDone) {
            StartupLoadingScreen()
            return@PitBoardTheme
        }

        val navController = rememberNavController()

        // Si las clasificaciones se apagan mientras se está DENTRO de ellas, la pestaña
        // desaparece de la barra pero la pantalla seguiría en pantalla: se vuelve al inicio.
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        LaunchedEffect(standingsEnabled, currentRoute) {
            if (!standingsEnabled && currentRoute?.startsWith("standings") == true) {
                navController.navigate("events") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }

        Scaffold(
            bottomBar = { PitBoardBottomBar(navController, standingsEnabled) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "events",
                modifier = Modifier.padding(padding)
            ) {
                composable("events") {
                    EventsScreen()
                }
                composable("standings") {
                    StandingsScreen(
                        onCategoryClick = { category ->
                            navController.navigate("standings/${category.name}")
                        }
                    )
                }
                composable(
                    "standings/{category}",
                    arguments = listOf(navArgument("category") { type = NavType.StringType })
                ) { backStackEntry ->
                    val categoryName = backStackEntry.arguments?.getString("category")
                    val category = StandingsCategory.entries.firstOrNull { it.name == categoryName }
                        ?: StandingsCategory.F1
                    CategoryStandingsScreen(
                        category = category,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen()
                }
            }
        }
    }
    }
}

/** Milisegundos de margen (12 s) antes de dejar de esperar a la sincronización de
 *  arranque y enseñar la app igual (con lo que ya hubiera en caché) — una red muy lenta
 *  no debe dejar al usuario mirando la pantalla de carga indefinidamente. */
private const val STARTUP_SYNC_TIMEOUT_MS = 12_000L

@Composable
private fun StartupLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                "PitBoard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                "Actualizando calendario y clasificaciones…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PitBoardBottomBar(navController: NavHostController, standingsEnabled: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        bottomDestinations(standingsEnabled).forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) }
            )
        }
    }
}
