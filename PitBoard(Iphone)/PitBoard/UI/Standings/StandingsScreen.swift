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

    // Filtrado a OVERALL/DRIVER en el propio predicado — el líder de cada categoría es la
    // fila de posición más baja dentro de ese subconjunto (ver `leaderByCategory`).
    @Query(
        filter: #Predicate<StandingModel> { model in
            model.standingsClass == StandingsClass.overall && model.type == StandingType.driver
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
            model.type == StandingType.team &&
            (model.category == StandingsCategory.wec ||
             model.category == StandingsCategory.elms ||
             model.category == StandingsCategory.imsa ||
             model.category == StandingsCategory.lemansCup)
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
                    title: "Clasificaciones desactivadas",
                    message: "Actívalas en Ajustes para ver la clasificación de F1, MotoGP y más — necesita conexión a internet."
                )
            } else if !isOnline && !hasAnyCache {
                EmptyStateView(
                    systemImage: "wifi.slash",
                    title: "Necesitas conexión",
                    message: "Todavía no hay ninguna clasificación guardada. Conéctate a wifi o datos móviles al menos una vez."
                )
            } else {
                VStack(spacing: 0) {
                    if !isOnline {
                        OfflineBanner()
                    }
                    List {
                        ForEach(StandingsCategory.allCases) { category in
                            CategoryRow(category: category, leader: leaderByCategory[category])
                                .contentShape(Rectangle())
                                .onTapGesture { onCategorySelected(category) }
                                .accessibilityIdentifier("standings.row.\(category.rawValue)")
                        }
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    }
                    .listStyle(.plain)
                }
            }
        }
        .navigationTitle("Clasificaciones")
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
                }
            }
        }
        .alert("Sin conexión", isPresented: $showOfflineAlert) {
            Button("Vale", role: .cancel) {}
        } message: {
            Text("No se puede actualizar ahora.")
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

            VStack(alignment: .leading, spacing: 2) {
                Text(category.displayName).font(.body)
                if let leader {
                    Text("Lidera \(leader.name) · \(formatPoints(leader.points)) pts")
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    Text("Sin datos todavía")
                        .font(.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(colors.onSurfaceVariant)
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

    private var failed: [StandingsRepository.CategoryOutcome] {
        report.outcomes.filter { !$0.ok }
    }

    var body: some View {
        NavigationStack {
            List {
                if failed.isEmpty {
                    Text("Todo actualizado correctamente.")
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
            .navigationTitle("Sincronización: \(report.succeeded.count) de \(report.outcomes.count) OK")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }
}
