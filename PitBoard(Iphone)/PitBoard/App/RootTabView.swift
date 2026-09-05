import SwiftUI
import SwiftData
import PitBoardKit

/// Pestañas de la barra inferior + arranque de la app — equivalente conjunto de
/// `PitBoardApp()` y `PitBoardBottomBar()` en `MainActivity.kt`. "Clasificaciones" solo
/// aparece si el interruptor de Ajustes está activado, igual que en Android.
///
/// A diferencia de Android (un único `NavHost` con todas las rutas), aquí cada pestaña
/// tiene su propio `NavigationStack` — es el patrón nativo de SwiftUI para `TabView`, y
/// evita tener que enrutar "standings/{category}" como una ruta con argumento de texto:
/// `CategoryStandingsScreen` se empuja con el propio valor tipado de `StandingsCategory`.
struct RootTabView: View {
    @State private var settings: AppSettingsRepository
    @State private var startupSyncDone = false
    @State private var standingsPath = NavigationPath()
    private let syncManager: BackgroundSyncManager

    init(settings: AppSettingsRepository, syncManager: BackgroundSyncManager) {
        _settings = State(initialValue: settings)
        self.syncManager = syncManager
    }

    var body: some View {
        Group {
            // Primer arranque de verdad: elegir idioma es lo PRIMERO de todo, antes incluso
            // que el permiso de notificaciones — pedido explícito ("al instalarla te pida
            // cuál quieres"). Se salta en modo test de UI (ver UITestSupport) para que
            // XCUITest siempre llegue a las 3 pestañas desde el primer frame.
            if settings.appLanguage == nil && !UITestSupport.isUITesting() {
                LanguagePickerScreen(onLanguageChosen: { settings.setAppLanguage($0) })
            } else if !startupSyncDone {
                StartupLoadingScreen()
            } else {
                TabView {
                    NavigationStack {
                        EventsScreen()
                    }
                    .tabItem { Label(settings.t("events_title"), systemImage: "flag.checkered") }

                    if settings.standingsEnabled {
                        NavigationStack(path: $standingsPath) {
                            StandingsScreen(onCategorySelected: { category in
                                standingsPath.append(category)
                            })
                            .navigationDestination(for: StandingsCategory.self) { category in
                                CategoryStandingsScreen(category: category)
                            }
                        }
                        .tabItem { Label(settings.t("standings_title"), systemImage: "trophy") }
                    }

                    NavigationStack {
                        SettingsScreen()
                    }
                    .tabItem { Label(settings.t("settings_title"), systemImage: "gearshape") }
                }
            }
        }
        .environment(settings)
        .environment(\.syncManager, syncManager)
        .task { await runStartupSync() }
    }

    /// Equivalente exacto del `LaunchedEffect(Unit)` de `PitBoardApp()` en MainActivity.kt:
    /// primer arranque = pide permiso de avisos + sincroniza Eventos y Clasificaciones con
    /// pantalla de carga (con margen de 12 s); resto de aperturas = se enseña al instante y
    /// solo se refresca Clasificaciones en segundo plano si el interruptor está activado.
    private func runStartupSync() async {
        // Modo test de UI (ver `UITestSupport`): nada de red, nada de diálogo real de
        // permisos (XCUITest no puede tocarlo de forma fiable) — datos fijos y
        // Clasificaciones activada para que las 3 pestañas y sus pantallas sean
        // recorribles desde el primer frame.
        if UITestSupport.isUITesting() {
            await UITestFixtures.seedIfNeeded()
            settings.setStandingsEnabled(true)
            settings.setHasCompletedFirstSync(true)
            startupSyncDone = true
            return
        }

        await requestNotificationPermissionIfNeeded()
        await seedSeriesConfigIfNeeded()

        let alreadyDidFirstSync = settings.hasCompletedFirstSync

        if !alreadyDidFirstSync {
            if ConnectivityMonitor.shared.isOnline {
                await withTimeout(seconds: 12) {
                    async let scheduleSync: Void = { _ = await syncManager.syncScheduleNow() }()
                    async let standingsSync: Void = { _ = await syncManager.syncStandingsNow() }()
                    _ = await (scheduleSync, standingsSync)
                }
            }
            // Se marca pase lo que pase (éxito, fallo o sin red) — no repetir la pantalla de
            // carga completa en cada apertura siguiente.
            settings.setHasCompletedFirstSync(true)
            startupSyncDone = true
        } else {
            startupSyncDone = true
            if settings.standingsEnabled && ConnectivityMonitor.shared.isOnline {
                Task { _ = await syncManager.syncStandingsNow() }
            }
        }
    }

    /// Primer arranque: pide el permiso de notificaciones antes de nada. Aceptar deja el
    /// interruptor "Activar avisos" de Ajustes encendido; rechazar lo deja apagado —
    /// equivalente de `NotificationPermissionOnboarding.kt`. Se guarda una marca para no
    /// volver a pedirlo en cada arranque; a partir de ahí, quien lo vuelve a pedir es el
    /// interruptor de Ajustes (ver SettingsScreen).
    private func requestNotificationPermissionIfNeeded() async {
        guard !settings.notificationPermissionRequested else { return }

        let granted = await NotificationPermission.requestAuthorization()
        settings.setNotificationPermissionRequested(true)
        settings.setNotificationsEnabled(granted)

        let scheduler = NotificationScheduler(settings: settings)
        await scheduler.rescheduleAllUpcoming()
    }

    /// Siembra el tag/color por defecto de las series si la tabla está vacía (primer
    /// arranque) — equivalente del bloque correspondiente en `PitBoardApplication.onCreate`.
    private func seedSeriesConfigIfNeeded() async {
        let context = ModelContext(AppDatabase.container)
        let count = (try? context.fetchCount(FetchDescriptor<SeriesConfigModel>())) ?? 0
        guard count == 0 else { return }

        for config in makeDefaultSeriesConfigs() { context.insert(config) }
        try? context.save()
    }
}

private struct StartupLoadingScreen: View {
    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()
            VStack(spacing: 20) {
                ZStack {
                    Circle()
                        .fill(colors.primary)
                        .frame(width: 88, height: 88)
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(colors.onPrimary)
                        // Decorativo: pantalla de carga, el texto "PitBoard" de abajo basta.
                        .accessibilityHidden(true)
                }
                Text("PitBoard")
                    .font(.title.bold())
                    .foregroundStyle(colors.onBackground)
                ProgressView()
                    .tint(colors.primary)
                Text(settings.t("startup_loading_message"))
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
    }
}
