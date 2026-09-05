import SwiftUI
import SwiftData
import PitBoardKit

/// Lista de las 15 categorías con clasificación, con el líder de cada una como vista
/// previa — equivalente exacto de `StandingsScreen.kt` (`StandingsViewModel` + composable).
///
/// Sin ViewModel con `StateFlow`: `@Query` en la propia vista ya observa SwiftData
/// directamente y recompone sola cuando `StandingsRepository.syncAll()` escribe filas
/// nuevas — es el equivalente de `leaderByCategory` como `Flow` combinado en Android.
struct StandingsScreen: View {
    let onCategorySelected: (StandingsCategory) -> Void

    @Environment(AppSettingsRepository.self) private var settings
    @Environment(\.syncManager) private var syncManager
    @Environment(\.pitBoardColors) private var colors

    // 05/09/2026: el macro #Predicate no admite comparar contra el nombre completo del
    // enum ("key path cannot refer to enum case") NI contra el miembro implícito
    // ("Member access without an explicit base is not supported in this predicate") —
    // ambos detectados por el CI al compilar este target por primera vez. Solo acepta
    // comparar contra una variable capturada desde fuera del predicado (mismo patrón que
    // ya usa CategoryStandingsScreen.swift con `$0.category == category`), de ahí estas
    // constantes.
    // `static` porque estas constantes se usan dentro del valor por defecto de una
    // propiedad (`@Query(filter:)` en la propia declaración) — ahí `self` todavía no
    // existe, así que no se puede capturar una propiedad de instancia.
    private static let overallClass = StandingsClass.overall
    private static let driverType = StandingType.driver
    private static let teamType = StandingType.team
    private static let wecCategory = StandingsCategory.wec
    private static let elmsCategory = StandingsCategory.elms
    private static let imsaCategory = StandingsCategory.imsa
    private static let lemansCupCategory = StandingsCategory.lemansCup

    // Filtrado a OVERALL/DRIVER en el propio predicado — el líder de cada categoría es la
    // fila de posición más baja dentro de ese subconjunto (ver `leaderByCategory`).
    @Query(
        filter: #Predicate<StandingModel> { model in
            model.standingsClass == overallClass && model.type == driverType
        }
    )
    private var driverLeaders: [StandingModel]

    // ELMS/IMSA/WEC/Le Mans Cup nunca guardan filas overall/driver (solo team, por
    // clase) — sin esto esas 4 categorías se quedaban en "Sin datos todavía" para
    // siempre aunque sí tuvieran clasificación guardada (bug real reportado 04/09/2026).
    // Se piden todas las filas TEAM de esas 4 categorías y se filtra en `leaderByCategory`
    // a la clase principal de cada una (`StandingsCategory.primaryCarClass`).
    @Query(
        filter: #Predicate<StandingModel> { model in
            model.type == teamType &&
            (model.category == wecCategory ||
             model.category == elmsCategory ||
             model.category == imsaCategory ||
             model.category == lemansCupCategory)
        }
    )
    private var carBasedTeamRows: [StandingModel]

    @State private var isOnline = ConnectivityMonitor.shared.isOnline
    @State private var syncing = false
    @State private var syncReport: StandingsRepository.SyncResult?
    @State private var showOfflineAlert = false

    init(onCategorySelected: @escaping (StandingsCategory) -> Void) {
        self.onCategorySelected = onCategorySelected
    }

    /// El piloto (o, en las categorías "por coche", el EQUIPO en cabeza de su clase
    /// principal) en cabeza de cada categoría — equivalente de `leaderByCategory`. `nil`
    /// para una categoría sin ninguna fila todavía sincronizada.
    private var leaderByCategory: [StandingsCategory: StandingModel] {
        var result = Dictionary(grouping: driverLeaders, by: \.category)
            .compactMapValues { rows in rows.min(by: { $0.position < $1.position }) }

        let groupedCarRows = Dictionary(grouping: carBasedTeamRows, by: \.category)
        for (category, rows) in groupedCarRows {
            guard let primaryClass = category.primaryCarClass else { continue }
            if let leader = rows.filter({ $0.standingsClass == primaryClass }).min(by: { $0.position < $1.position }) {
                result[category] = leader
            }
        }
        return result
    }

    private var hasAnyCache: Bool { !leaderByCategory.isEmpty }

    var body: some View {
        Group {
            if !settings.standingsEnabled {
                EmptyStateView(
                    systemImage: "trophy.fill",
                    title: settings.t("standings_empty_disabled_title"),
                    message: settings.t("standings_empty_disabled_message")
                )
            } else if !isOnline && !hasAnyCache {
                EmptyStateView(
                    systemImage: "wifi.slash",
                    title: settings.t("events_empty_offline_title"),
                    message: settings.t("standings_empty_offline_message")
                )
            } else {
                VStack(spacing: 0) {
                    if !isOnline {
                        OfflineBanner(message: settings.t("offline_banner_message"))
                    }
                    List {
                        ForEach(StandingsCategory.allCases) { category in
                            CategoryRow(category: category, leader: leaderByCategory[category])
                                .contentShape(Rectangle())
                                .onTapGesture { onCategorySelected(category) }
                                // 05/09/2026 (Fase 2, accesibilidad): la fila se construye con
                                // .onTapGesture, no Button — sin esto VoiceOver la leía como
                                // varios textos sueltos sin ninguna acción anunciada.
                                .accessibilityElement(children: .combine)
                                .accessibilityAddTraits(.isButton)
                                .accessibilityIdentifier("standings.row.\(category.rawValue)")
                        }
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    }
                    .listStyle(.plain)
                }
            }
        }
        .navigationTitle(settings.t("standings_title"))
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if syncing {
                    ProgressView()
                } else {
                    Button {
                        isOnline = ConnectivityMonitor.shared.isOnline
                        if isOnline {
                            refreshNow()
                        } else {
                            showOfflineAlert = true
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityIdentifier("standings.refresh")
                    .accessibilityLabel(settings.t("cd_refresh"))
                }
            }
        }
        .alert(settings.t("standings_offline_alert_title"), isPresented: $showOfflineAlert) {
            Button(settings.t("standings_offline_alert_ok"), role: .cancel) {}
        } message: {
            Text(settings.t("standings_offline_alert_message"))
        }
        .sheet(isPresented: Binding(get: { syncReport != nil }, set: { if !$0 { syncReport = nil } })) {
            if let report = syncReport {
                SyncReportView(report: report)
            }
        }
    }

    /// Sincronización manual bajo demanda — llama a las 14+1 fuentes DIRECTAMENTE (no
    /// espera al ciclo de `BackgroundSyncManager`) para poder enseñar el resultado.
    private func refreshNow() {
        guard !syncing, let syncManager else { return }
        syncing = true
        Task {
            let result = await syncManager.syncStandingsNow()
            syncing = false
            syncReport = result
        }
    }
}

private struct CategoryRow: View {
    let category: StandingsCategory
    let leader: StandingModel?

    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        HStack(spacing: 12) {
            RemoteImage(urlString: category.logoUrl) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fit).padding(6)
                default:
                    Image(systemName: "trophy.fill").foregroundStyle(colors.primary)
                }
            }
            .frame(width: 52, height: 52)
            .background(Color.white, in: RoundedRectangle(cornerRadius: PitBoardShapes.small))
            // Decorativo: el nombre de la categoría, justo al lado, ya dice lo mismo.
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(category.displayName).font(.body)
                if let leader {
                    Text(String(format: settings.t("standings_leading"), leader.name, formatPoints(leader.points)))
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    Text(settings.t("standings_no_data_yet"))
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(colors.onSurfaceVariant)
                .accessibilityHidden(true)
        }
        .padding(12)
        .background(colors.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: PitBoardShapes.medium))
    }
}

/// Resultado de la sincronización manual: solo el recuento en el título, y una línea por
/// categoría fallida con un motivo corto — equivalente de `SyncReportDialog`. Se usa un
/// `.sheet` en vez de `.alert` (que en iOS no admite bien una lista de contenido variable)
/// para que quepan las hasta 15 categorías si todas fallasen.
private struct SyncReportView: View {
    let report: StandingsRepository.SyncResult
    @Environment(\.dismiss) private var dismiss
    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    private var failed: [StandingsRepository.CategoryOutcome] {
        report.outcomes.filter { !$0.ok }
    }

    var body: some View {
        NavigationStack {
            List {
                if failed.isEmpty {
                    Text(settings.t("standings_sync_all_ok"))
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    ForEach(failed, id: \.category) { outcome in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(outcome.category.displayName)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(colors.error)
                            if let detail = outcome.detail {
                                Text(detail)
                                    .font(.footnote)
                                    .foregroundStyle(colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            .navigationTitle(String(format: settings.t("standings_sync_result"), report.succeeded.count, report.outcomes.count))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(settings.t("events_close")) { dismiss() }
                }
            }
        }
    }
}
