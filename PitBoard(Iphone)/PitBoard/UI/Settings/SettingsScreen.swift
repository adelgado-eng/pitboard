import SwiftUI
import SwiftData
import UIKit
import PitBoardKit

/// Equivalente exacto de `SettingsScreen.kt` (`SettingsViewModel` + `SettingsScreen` +
/// `SettingsCard` + `SessionTypeToggle` + `NotificationSeriesPicker`). No hay un
/// "SettingsViewModel" aparte: `AppSettingsRepository` ya es `@Observable`, así que la
/// vista lee/escribe directamente sobre él — el único estado propio de la pantalla es el
/// de los dos diálogos y el `Task` de reprogramar avisos tras cada cambio.
struct SettingsScreen: View {
    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.syncManager) private var syncManager
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
                notificationsCard
                standingsCard
                appearanceCard
                timeZoneCard
                backgroundRefreshHelpCard

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
                    await applyNotificationPermissionResult(granted: true)
                } else {
                    if !granted { waitingForSystemSettings = false }
                    await syncWithSystemPermission(granted: granted)
                }
            }
        }
    }

    // MARK: - Tarjeta: Notificaciones

    @ViewBuilder
    private var notificationsCard: some View {
        SettingsCard(title: settings.t("settings_notifications_title"), systemImage: "bell.fill") {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(settings.t("settings_notifications_enable")).font(.body)
                    Text(settings.t("settings_notifications_subtitle"))
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { settings.notificationsEnabled },
                    set: { wantsEnabled in Task { await handleNotificationsToggle(wantsEnabled) } }
                ))
                .labelsHidden()
                .accessibilityIdentifier("settings.notificationsToggle")
                // 05/09/2026 (Fase 2, accesibilidad): Toggle("", ...) con label VACÍO — sin
                // esto VoiceOver no anunciaba nada al enfocarlo. .labelsHidden() solo oculta
                // lo visual, no sustituye la etiqueta de accesibilidad por sí solo.
                .accessibilityLabel(settings.t("settings_notifications_enable"))
            }

            if settings.notificationsEnabled {
                Divider().padding(.vertical, 8)

                Text(settings.t("settings_notifications_when")).font(.caption.weight(.semibold))
                HStack(spacing: 8) {
                    minutesChip(minutes: 15, label: "15m")
                    minutesChip(minutes: 30, label: "30m")
                    minutesChip(minutes: 60, label: "1h")
                }
                .padding(.top, 4)

                Text(settings.t("settings_notifications_which_sessions"))
                    .font(.caption.weight(.semibold))
                    .padding(.top, 12)
                VStack(spacing: 4) {
                    SessionTypeToggle(
                        label: settings.t("settings_notifications_competitive"),
                        checked: settings.competitiveNotificationsEnabled,
                        onToggle: { enabled in
                            settings.setCompetitiveNotificationsEnabled(enabled)
                            rescheduleReminders()
                        }
                    )
                    SessionTypeToggle(
                        label: settings.t("settings_notifications_practice"),
                        checked: settings.practiceNotificationsEnabled,
                        onToggle: { enabled in
                            settings.setPracticeNotificationsEnabled(enabled)
                            rescheduleReminders()
                        }
                    )
                }

                Divider().padding(.vertical, 8)

                Button {
                    showCategoryPicker = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(settings.t("settings_notifications_active_series")).font(.subheadline)
                            let count = activeSeries.count
                            Text(count == RaceSeries.allCases.count ? settings.t("settings_notifications_all_series") : String(format: settings.t("settings_notifications_series_count"), count))
                                .font(.caption)
                                .foregroundStyle(colors.primary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(colors.onSurfaceVariant).accessibilityHidden(true)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // .accessibilityHint (no .accessibilityLabel): el botón ya tiene texto propio
                // ("Series activas", "Todas"/"N series") que VoiceOver fusiona solo — un
                // accessibilityLabel aquí lo REEMPLAZARÍA en vez de añadirse, perdiendo esa
                // información. El hint solo aclara qué hace el toque.
                .accessibilityHint(settings.t("cd_edit_active_series"))
            }
        }
    }

    private func minutesChip(minutes: Int, label: String) -> some View {
        let selected = settings.notificationMinutesBefore == minutes
        return Button(label) {
            settings.setNotificationMinutesBefore(minutes)
            rescheduleReminders()
        }
        .buttonStyle(.bordered)
        .tint(selected ? colors.primary : colors.onSurfaceVariant)
        .background(selected ? colors.primaryContainer : .clear, in: Capsule())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// Misma máquina de decisión que el `onCheckedChange` de Android: apagar nunca pide
    /// permiso; ya concedido -> se activa directo; bloqueado por el sistema -> diálogo
    /// explicativo; si no, se pide el permiso.
    private func handleNotificationsToggle(_ wantsEnabled: Bool) async {
        guard wantsEnabled else {
            settings.setNotificationsEnabled(false)
            rescheduleReminders()
            return
        }

        if await NotificationPermission.isGranted() {
            settings.setNotificationsEnabled(true)
            rescheduleReminders()
        } else if await NotificationPermission.isBlockedBySystem() {
            showPermissionBlockedDialog = true
        } else {
            let granted = await NotificationPermission.requestAuthorization()
            await applyNotificationPermissionResult(granted: granted)
        }
    }

    /// Respuesta del diálogo del sistema: el interruptor sigue exactamente lo que el
    /// usuario haya contestado.
    private func applyNotificationPermissionResult(granted: Bool) async {
        settings.setNotificationPermissionRequested(true)
        settings.setNotificationsEnabled(granted)
        await NotificationScheduler(settings: settings).rescheduleAllUpcoming()
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
        rescheduleReminders()
    }

    private func rescheduleReminders() {
        Task { await NotificationScheduler(settings: settings).rescheduleAllUpcoming() }
    }

    // MARK: - Tarjeta: Clasificaciones

    @ViewBuilder
    private var standingsCard: some View {
        SettingsCard(title: settings.t("settings_standings_title"), systemImage: "trophy.fill") {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(settings.t("settings_standings_enable")).font(.body)
                    Text(settings.t("settings_standings_subtitle"))
                    .font(.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { settings.standingsEnabled },
                    set: { enabled in setStandingsEnabled(enabled) }
                ))
                .labelsHidden()
                .accessibilityIdentifier("settings.standingsToggle")
                .accessibilityLabel(settings.t("settings_standings_enable"))
            }
        }
    }

    /// Al activar, programa el ciclo semanal y lanza una sincronización inmediata — así
    /// el usuario no tiene que esperar hasta el lunes. Al desactivar, cancela la tarea
    /// programada.
    private func setStandingsEnabled(_ enabled: Bool) {
        settings.setStandingsEnabled(enabled)
        if enabled {
            syncManager?.scheduleWeeklyStandingsSync()
            Task { _ = await syncManager?.syncStandingsNow() }
        } else {
            syncManager?.cancelStandingsSync()
        }
    }

    // MARK: - Tarjeta: Apariencia

    @ViewBuilder
    private var appearanceCard: some View {
        SettingsCard(title: settings.t("settings_appearance_title"), systemImage: "paintpalette.fill") {
            Text(settings.t("settings_appearance_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: 8) {
                themeChip(theme: .light, label: settings.t("settings_theme_light"))
                themeChip(theme: .dark, label: settings.t("settings_theme_dark"))
                themeChip(theme: .system, label: settings.t("settings_theme_auto"))
            }
        }
    }

    private func themeChip(theme: AppTheme, label: String) -> some View {
        let selected = settings.appTheme == theme
        return Button(label) { settings.setAppTheme(theme) }
            .buttonStyle(.bordered)
            .tint(selected ? colors.primary : colors.onSurfaceVariant)
            .background(selected ? colors.primaryContainer : .clear, in: Capsule())
            .accessibilityIdentifier("settings.theme.\(theme.rawValue)")
            .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: - Tarjeta: Zona horaria

    @ViewBuilder
    private var timeZoneCard: some View {
        SettingsCard(title: settings.t("settings_timezone_title"), systemImage: "globe") {
            Text(settings.t("settings_timezone_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: 8) {
                timeModeChip(mode: .device, label: settings.t("settings_timezone_device"))
                timeModeChip(mode: .track, label: settings.t("settings_timezone_track"))
            }
        }
    }

    private func timeModeChip(mode: TimeDisplayMode, label: String) -> some View {
        let selected = settings.timeDisplayMode == mode
        return Button(label) { settings.setTimeDisplayMode(mode) }
            .buttonStyle(.bordered)
            .tint(selected ? colors.primary : colors.onSurfaceVariant)
            .background(selected ? colors.primaryContainer : .clear, in: Capsule())
            .accessibilityIdentifier("settings.timeDisplayMode.\(mode.rawValue)")
            .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: - Tarjeta: Ayuda (avisos/widget que llegan tarde)

    /// Equivalente iOS del enlace a dontkillmyapp.com de Android — ese sitio es específico
    /// de fabricantes Android (Samsung/Xiaomi/Huawei matando procesos en segundo plano), así
    /// que aquí no aplica tal cual. El problema equivalente en iOS es "Actualización en
    /// segundo plano" desactivada (a mano o por Modo de bajo consumo) para PitBoard — este
    /// botón lleva directo a esa pantalla de Ajustes del sistema.
    @ViewBuilder
    private var backgroundRefreshHelpCard: some View {
        SettingsCard(title: settings.t("settings_battery_help_title"), systemImage: "battery.25") {
            Text(settings.t("settings_battery_help_subtitle"))
                .font(.caption)
                .foregroundStyle(colors.onSurfaceVariant)
                .padding(.bottom, 4)

            Button(settings.t("settings_battery_help_button")) {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

// MARK: - Componentes auxiliares

private struct SettingsCard<Content: View>: View {
    let title: String
    let systemImage: String
    let content: () -> Content
    @Environment(\.pitBoardColors) private var colors

    init(title: String, systemImage: String, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.systemImage = systemImage
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .foregroundStyle(colors.primary)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.title3.weight(.bold))
            }
            .padding(.bottom, 16)

            VStack(alignment: .leading, spacing: 0) {
                content()
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: PitBoardShapes.large))
    }
}

private struct SessionTypeToggle: View {
    let label: String
    let checked: Bool
    let onToggle: (Bool) -> Void

    var body: some View {
        Button {
            onToggle(!checked)
        } label: {
            HStack {
                Text(label).font(.subheadline)
                Spacer()
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .foregroundStyle(checked ? .primary : .secondary)
                    // Decorativo: el estado real se anuncia con .isSelected más abajo, no
                    // hace falta que VoiceOver además nombre el símbolo del icono.
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
        // 05/09/2026 (Fase 2, accesibilidad): sin esto VoiceOver nunca anunciaba si este
        // checkbox propio estaba marcado o no.
        .accessibilityAddTraits(checked ? .isSelected : [])
    }
}

/// Equivalente de `NotificationSeriesPicker` — desactiva las series de las que no se
/// quieren avisos.
private struct NotificationSeriesPicker: View {
    let seriesConfigs: [SeriesConfigModel]
    let selected: Set<RaceSeries>
    let onToggle: (RaceSeries, Bool) -> Void
    let onDismiss: () -> Void

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    private var configByKey: [RaceSeries: SeriesConfigModel] {
        Dictionary(uniqueKeysWithValues: seriesConfigs.map { ($0.series, $0) })
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    Text(settings.t("settings_series_with_alerts_subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, 12)

                    ForEach(RaceSeries.allCases) { series in
                        let config = configByKey[series]
                        let tag = config?.tag ?? series.defaultTag
                        let colorHex = config?.colorHex ?? series.defaultColorHex
                        let isSelected = selected.contains(series)

                        Button {
                            onToggle(series, !isSelected)
                        } label: {
                            HStack {
                                let tagColor = ColorContrast.safeParseColor(colorHex)
                                Text(tag)
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(ColorContrast.readableTextColor(background: tagColor))
                                    .frame(width: 32, height: 32)
                                    .background(tagColor, in: RoundedRectangle(cornerRadius: PitBoardShapes.extraSmall))
                                Text(series.displayName)
                                    .font(.subheadline)
                                    .foregroundStyle(colors.onSurface)
                                Spacer()
                                // Puramente decorativo: el estado ya lo anuncia el
                                // .isSelected del Button de abajo — sin ocultarlo, VoiceOver
                                // leería un segundo interruptor "desactivado" contradictorio
                                // (allowsHitTesting no afecta a accesibilidad).
                                Toggle("", isOn: .constant(isSelected)).labelsHidden().allowsHitTesting(false)
                                    .accessibilityHidden(true)
                            }
                            .padding(12)
                            .background(
                                isSelected ? colors.primaryContainer.opacity(0.3) : .clear,
                                in: RoundedRectangle(cornerRadius: PitBoardShapes.small)
                            )
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                    }
                }
                .padding(20)
            }
            .navigationTitle(settings.t("settings_series_with_alerts_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(settings.t("settings_done"), action: onDismiss)
                }
            }
        }
    }
}
