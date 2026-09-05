import SwiftUI
import SwiftData
import PitBoardKit

/// Equivalente exacto de `SettingsScreen.kt` (`SettingsViewModel` + `SettingsScreen` +
/// `SettingsCard` + `SessionTypeToggle` + `NotificationSeriesPicker`). No hay un
/// "SettingsViewModel" aparte: `AppSettingsRepository` ya es `@Observable`, así que la
/// vista lee/escribe directamente sobre él — el único estado propio de la pantalla es el
/// de los dos diálogos y el `Task` de reprogramar avisos tras cada cambio.
///
/// 05/09/2026 (Fase 4 del diagnóstico): este archivo concentraba las 5 tarjetas de Ajustes
/// (Notificaciones/Clasificaciones/Apariencia/Zona horaria/Ayuda) en un único sitio de 483
/// líneas. Cada tarjeta está ahora en su propio archivo (`SettingsNotificationsCard`,
/// `SettingsStandingsCard`, `SettingsAppearanceCard`, `SettingsTimeZoneCard`,
/// `SettingsBackgroundRefreshCard`), y el envoltorio visual reutilizado en
/// `SettingsCard.swift`. Pura extracción de código verbatim — ningún comportamiento
/// cambia, cada tarjeta lee `AppSettingsRepository` directamente por `@Environment`, igual
/// que hacía este archivo antes de dividirse. `applyNotificationPermissionResult` y
/// `rescheduleReminders` se comparten entre esta pantalla (el `.onChange(of: scenePhase)`
/// de más abajo) y `SettingsNotificationsCard`, así que se quedan como funciones libres en
/// vez de métodos privados.
struct SettingsScreen: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors
    @Environment(\.scenePhase) private var scenePhase

    @Query private var seriesConfigs: [SeriesConfigModel]

    @State private var showCategoryPicker = false
    @State private var showPermissionBlockedDialog = false
    /// Marca que hemos mandado al usuario a Ajustes del sistema queriendo activar los
    /// avisos — para encender el interruptor al volver si allí concedió el permiso.
    /// (En iOS no hace falta: `scenePhase == .active` ya vuelve a mirar el permiso real
    /// cada vez, así que esta marca solo evita un parpadeo mientras el usuario todavía no
    /// ha tocado nada en Ajustes del sistema.)
    @State private var waitingForSystemSettings = false

    private var activeSeries: Set<RaceSeries> {
        Set(RaceSeries.allCases).subtracting(settings.notificationDisabledSeries)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                SettingsNotificationsCard(
                    showCategoryPicker: $showCategoryPicker,
                    showPermissionBlockedDialog: $showPermissionBlockedDialog
                )
                SettingsStandingsCard()
                SettingsAppearanceCard()
                SettingsTimeZoneCard()
                SettingsBackgroundRefreshCard()

                Text(settings.t("settings_footer"))
                    .font(.caption2)
                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
            }
            .padding(20)
        }
        .background(colors.background)
        .navigationTitle(settings.t("settings_title"))
        .alert(settings.t("settings_notifications_permission_title"), isPresented: $showPermissionBlockedDialog) {
            Button(settings.t("settings_open_settings")) {
                waitingForSystemSettings = true
                NotificationPermission.openSystemSettings()
            }
            Button(settings.t("settings_not_now"), role: .cancel) {}
        } message: {
            Text(settings.t("settings_notifications_permission_body"))
        }
        .sheet(isPresented: $showCategoryPicker) {
            NotificationSeriesPicker(
                seriesConfigs: seriesConfigs,
                selected: activeSeries,
                onToggle: toggleNotificationSeries,
                onDismiss: { showCategoryPicker = false }
            )
        }
        // Al volver a primer plano (ej. desde Ajustes del sistema) se vuelve a mirar el
        // permiso real — equivalente de DisposableEffect + ON_RESUME en Android.
        .onChange(of: scenePhase) { _, newPhase in
            guard newPhase == .active else { return }
            Task {
                let granted = await NotificationPermission.isGranted()
                if granted && waitingForSystemSettings {
                    waitingForSystemSettings = false
                    await applyNotificationPermissionResult(settings: settings, granted: true)
                } else {
                    if !granted { waitingForSystemSettings = false }
                    await syncWithSystemPermission(granted: granted)
                }
            }
        }
    }

    /// Si el permiso se ha revocado desde Ajustes del sistema mientras la app estaba en
    /// segundo plano, el interruptor no puede quedarse "activado" mintiendo.
    private func syncWithSystemPermission(granted: Bool) async {
        guard !granted && settings.notificationsEnabled else { return }
        settings.setNotificationsEnabled(false)
        await NotificationScheduler(settings: settings).rescheduleAllUpcoming()
    }

    private func toggleNotificationSeries(_ series: RaceSeries, enabled: Bool) {
        var disabled = settings.notificationDisabledSeries
        if enabled { disabled.remove(series) } else { disabled.insert(series) }
        settings.setNotificationDisabledSeries(disabled)
        rescheduleReminders(settings: settings)
    }
}

/// Respuesta del diálogo del sistema (o del permiso pedido directamente): el interruptor
/// sigue exactamente lo que el usuario haya contestado. Función libre (no método) porque
/// la usan tanto `SettingsScreen` (`.onChange(of: scenePhase)`) como
/// `SettingsNotificationsCard`.
func applyNotificationPermissionResult(settings: AppSettingsRepository, granted: Bool) async {
    settings.setNotificationPermissionRequested(true)
    settings.setNotificationsEnabled(granted)
    await NotificationScheduler(settings: settings).rescheduleAllUpcoming()
}

/// Función libre por el mismo motivo que la de arriba: la usan `SettingsScreen`
/// (`toggleNotificationSeries`) y `SettingsNotificationsCard`.
func rescheduleReminders(settings: AppSettingsRepository) {
    Task { await NotificationScheduler(settings: settings).rescheduleAllUpcoming() }
}
