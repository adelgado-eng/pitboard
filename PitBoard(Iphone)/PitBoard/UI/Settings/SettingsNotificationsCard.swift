import SwiftUI
import PitBoardKit

/// Tarjeta "Notificaciones" de Ajustes, con su editor de series y su selector de minutos
/// de antelación — extraída de `SettingsScreen.swift` (Fase 4 del diagnóstico) sin cambiar
/// ningún comportamiento.
struct SettingsNotificationsCard: View {
    @Binding var showCategoryPicker: Bool
    @Binding var showPermissionBlockedDialog: Bool

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.pitBoardColors) private var colors

    private var activeSeries: Set<RaceSeries> {
        Set(RaceSeries.allCases).subtracting(settings.notificationDisabledSeries)
    }

    var body: some View {
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
                            rescheduleReminders(settings: settings)
                        }
                    )
                    SessionTypeToggle(
                        label: settings.t("settings_notifications_practice"),
                        checked: settings.practiceNotificationsEnabled,
                        onToggle: { enabled in
                            settings.setPracticeNotificationsEnabled(enabled)
                            rescheduleReminders(settings: settings)
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
            rescheduleReminders(settings: settings)
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
            rescheduleReminders(settings: settings)
            return
        }

        if await NotificationPermission.isGranted() {
            settings.setNotificationsEnabled(true)
            rescheduleReminders(settings: settings)
        } else if await NotificationPermission.isBlockedBySystem() {
            showPermissionBlockedDialog = true
        } else {
            let granted = await NotificationPermission.requestAuthorization()
            await applyNotificationPermissionResult(settings: settings, granted: granted)
        }
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
/// quieren avisos. No es `private` porque `SettingsScreen` la usa desde su propio
/// `.sheet(...)`.
struct NotificationSeriesPicker: View {
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
