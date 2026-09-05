import SwiftUI
import SwiftData
import UIKit
import PitBoardKit

/// Clasificación de UNA categoría, con pestañas Pilotos/Equipos (o de clase de coche para
/// las categorías de resistencia) — equivalente exacto de `CategoryStandingsScreen.kt`.
/// Pantalla de solo lectura: sin ViewModel, `@Query` (sin filtrar por `category` — ver el
/// comentario sobre `allStandings` más abajo) + filtrado en Swift hace de sustituto de los
/// `Flow` de `StandingsRepository.observe`.
struct CategoryStandingsScreen: View {
    let category: StandingsCategory

    // 05/09/2026: mismo bug que StandingsRepository.replaceStandings y
    // RaceScheduleRepository.replaceSeries — un #Predicate comparando la propiedad
    // `category` (un enum propio, StandingsCategory) contra un valor capturado devuelve
    // SIEMPRE una lista vacía en esta versión de SwiftData (Xcode 16.4). Aquí hacía que
    // esta pantalla mostrase "Sin datos todavía" para TODAS las categorías sin excepción,
    // aunque sí hubiera filas guardadas — confirmado con el volcado del árbol de
    // accesibilidad en CI al fallar StandingsScreenUITests. Se trae todo y se filtra en
    // Swift (como en los dos sitios de arriba).
    @Query(sort: [SortDescriptor(\.position)]) private var allStandings: [StandingModel]
    @Query private var allCarDrivers: [CarDriverModel]

    @State private var mode: StandingType = .driver
    @State private var carClass: StandingsClass
    @State private var selectedCar: StandingModel?
    @State private var previewImage: ImagePreview?
    @Environment(AppSettingsRepository.self) private var settings

    init(category: StandingsCategory) {
        self.category = category
        _carClass = State(initialValue: CarBasedStandingsClasses.carBasedClasses[category]?.first?.0 ?? .overall)
    }

    private var categoryStandings: [StandingModel] {
        allStandings.filter { $0.category == category }
    }
    private var categoryCarDrivers: [CarDriverModel] {
        allCarDrivers.filter { $0.category == category }
    }

    private var carClasses: [(StandingsClass, String)]? { CarBasedStandingsClasses.carBasedClasses[category] }
    private var isCarBased: Bool { carClasses != nil }
    private var effectiveStandingsClass: StandingsClass { isCarBased ? carClass : .overall }

    private var carRows: [StandingModel] {
        categoryStandings.filter { $0.standingsClass == effectiveStandingsClass && $0.type == .team }
    }
    private var driverRows: [StandingModel] {
        categoryStandings.filter { $0.standingsClass == .overall && $0.type == .driver }
    }
    private var teamRows: [StandingModel] {
        categoryStandings.filter { $0.standingsClass == .overall && $0.type == .team }
    }

    private var lastUpdated: Date? {
        categoryStandings.map(\.updatedAtUtc).max()
    }

    var body: some View {
        VStack(spacing: 0) {
            if let lastUpdated {
                Text(String(format: settings.t("standings_last_updated"), DateTimeFormatters.formatLastUpdated(lastUpdated)))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if let carClasses {
                        ForEach(carClasses, id: \.0) { cls, label in
                            FilterChipView(title: label, selected: carClass == cls) { carClass = cls }
                        }
                    } else {
                        FilterChipView(title: settings.t("standings_drivers"), selected: mode == .driver) { mode = .driver }
                        if category.hasTeamStandings {
                            FilterChipView(title: settings.t("standings_teams"), selected: mode == .team) { mode = .team }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            }

            if isCarBased {
                StandingsListView(
                    rows: carRows,
                    isCarBased: true,
                    onCarClick: { selectedCar = $0 },
                    onImageClick: { url, name in previewImage = ImagePreview(url: url, label: name, isLogo: true) }
                )
            } else {
                // Equivalente de HorizontalPager: dos páginas deslizables (Pilotos/Equipos)
                // con el mismo gesto que en Android; los FilterChip de arriba mueven la
                // página igual que en Compose.
                TabView(selection: $mode) {
                    StandingsListView(
                        rows: driverRows,
                        isCarBased: false,
                        onCarClick: { _ in },
                        onImageClick: { url, name in previewImage = ImagePreview(url: url, label: name, isLogo: false) }
                    )
                    .tag(StandingType.driver)

                    if category.hasTeamStandings {
                        StandingsListView(
                            rows: teamRows,
                            isCarBased: false,
                            onCarClick: { _ in },
                            onImageClick: { url, name in previewImage = ImagePreview(url: url, label: name, isLogo: true) }
                        )
                        .tag(StandingType.team)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
        }
        .toolbar {
            ToolbarItem(placement: .principal) {
                HStack(spacing: 8) {
                    RemoteImage(urlString: category.logoUrl) { phase in
                        switch phase {
                        case .success(let image): image.resizable().aspectRatio(contentMode: .fit).padding(4)
                        default: Image(systemName: "trophy.fill")
                        }
                    }
                    .frame(width: 32, height: 32)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 8))
                    // Decorativo: el título de la pantalla, justo al lado, ya dice lo mismo.
                    .accessibilityHidden(true)
                    Text(category.displayName).font(.headline)
                }
            }
        }
        .sheet(item: $selectedCar) { car in
            let carNumber = car.name.hasPrefix("#") ? String(car.name.dropFirst()) : car.name
            let drivers = categoryCarDrivers.filter { $0.standingsClass == car.standingsClass && $0.carNumber == carNumber }
            CarDriversSheetView(car: car, drivers: drivers) { url, name in
                previewImage = ImagePreview(url: url, label: name, isLogo: false)
            }
        }
        .fullScreenCover(item: $previewImage) { preview in
            ImagePreviewView(preview: preview)
        }
    }

}

/// Vista previa tocada — URL, nombre a mostrar y si es un LOGO de equipo (true) o una
/// FOTO de piloto (false); decide cómo encaja la imagen en `ImagePreviewView`.
private struct ImagePreview: Identifiable {
    let url: String
    let label: String
    let isLogo: Bool
    var id: String { url + "|" + label }
}

/// Lista de una página (Pilotos/Equipos) o del listado único de las categorías por coche
/// — equivalente de `StandingsList` en Kotlin.
private struct StandingsListView: View {
    let rows: [StandingModel]
    /// Solo los coches de ELMS/IMSA/WEC/Le Mans Cup abren el desplegable de pilotos — el
    /// resto de categorías no tiene ese dato (mismo parámetro `isCarBased` que en Kotlin).
    let isCarBased: Bool
    let onCarClick: (StandingModel) -> Void
    let onImageClick: (String, String) -> Void

    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        if rows.isEmpty {
            VStack {
                Spacer()
                Text(settings.t("standings_no_category_data"))
                    .font(.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                Spacer()
            }
            .frame(maxWidth: .infinity)
        } else {
            List {
                ForEach(rows, id: \.entrantKey) { row in
                    StandingRowView(
                        row: row,
                        onClick: (isCarBased && row.type == .team) ? { onCarClick(row) } : nil,
                        onImageClick: row.photoUrl != nil ? { onImageClick(row.photoUrl!, row.name) } : nil
                    )
                }
                .listRowSeparator(.visible)
            }
            .listStyle(.plain)
        }
    }
}

private struct StandingRowView: View {
    let row: StandingModel
    let onClick: (() -> Void)?
    let onImageClick: (() -> Void)?

    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        HStack(spacing: 0) {
            let podiumColor = PodiumColors.forPosition(row.position, surface: colors.surface)
            Text("\(row.position)")
                .font(.title3.weight(podiumColor != nil ? .heavy : .bold))
                .foregroundStyle(podiumColor ?? colors.onSurfaceVariant)
                .frame(width: 32, alignment: .leading)

            avatar
                .padding(.leading, 4)

            VStack(alignment: .leading, spacing: 2) {
                Text(row.name).font(.body.weight(.semibold))
                if !row.team.isEmpty {
                    Text(row.team).font(.caption).foregroundStyle(colors.primary)
                }
            }
            .padding(.leading, 12)

            Spacer()

            Text(String(format: settings.t("standings_points"), formatPoints(row.points)))
                .font(.title3.weight(.bold))
                .foregroundStyle(colors.primary)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture { onClick?() }
        // 05/09/2026 (Fase 2, accesibilidad): la fila se construye con .onTapGesture, no
        // Button — sin esto VoiceOver leía posición/nombre/equipo/puntos como 4 elementos
        // sueltos sin ninguna acción anunciada. El avatar, que tiene su PROPIO gesto (ver
        // más abajo), no se absorbe en esta fusión porque ya lleva su propio
        // accessibilityElement() — sigue siendo un elemento independiente y tocable aparte.
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(onClick != nil ? .isButton : [])
        // Identificador estable para que los UI tests localicen una fila concreta — el
        // label combinado de .accessibilityElement(children: .combine) no es fiable de
        // buscar por texto exacto (ver StandingsScreenUITests).
        .accessibilityIdentifier("standings.entrantRow.\(row.entrantKey)")
    }

    @ViewBuilder
    private var avatar: some View {
        Group {
            if row.type == .team {
                RemoteImage(urlString: row.photoUrl) { phase in
                    if case .success(let image) = phase {
                        image.resizable().aspectRatio(contentMode: .fit).padding(4)
                    } else {
                        Image(systemName: "person.fill").foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .background(Color.white, in: Circle())
            } else {
                RemoteImage(urlString: row.photoUrl) { phase in
                    if case .success(let image) = phase {
                        // Crop + recorte desde arriba: las fotos panorámicas de pilotos
                        // nunca pierden la cara, aunque sean de cuerpo entero.
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Image(systemName: "person.fill").foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .background(colors.surfaceVariant)
            }
        }
        .frame(width: 40, height: 40)
        .clipShape(Circle())
        .contentShape(Circle())
        .onTapGesture { onImageClick?() }
        // Boundary explícito para que la fila de arriba NO lo absorba en su .combine — y
        // etiqueta propia, ya que sin foto/logo real es solo un icono de silueta genérico.
        // Sin foto que ver (onImageClick nil), se oculta del todo en vez de dejar un
        // elemento tocable que no hace nada.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(settings.t("cd_view_photo"))
        .accessibilityAddTraits(onImageClick != nil ? .isButton : [])
        .accessibilityHidden(onImageClick == nil)
    }
}

private struct CarDriversSheetView: View {
    let car: StandingModel
    let drivers: [CarDriverModel]
    let onDriverImageClick: (String, String) -> Void

    @Environment(\.pitBoardColors) private var colors
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(car.name.isEmpty ? car.team : car.name)
                    .font(.title2.bold())
                if !car.team.isEmpty {
                    Text(car.team).font(.body).foregroundStyle(colors.primary)
                }

                if drivers.isEmpty {
                    Text(settings.t("standings_no_driver_data"))
                        .font(.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .padding(.vertical, 24)
                } else {
                    ForEach(drivers, id: \.entryKey) { driver in
                        HStack(spacing: 12) {
                            RemoteImage(urlString: driver.photoUrl) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Image(systemName: "person.fill").foregroundStyle(colors.onSurfaceVariant)
                                }
                            }
                            .frame(width: 44, height: 44)
                            .background(colors.surfaceVariant)
                            .clipShape(Circle())
                            .contentShape(Circle())
                            .onTapGesture {
                                if let url = driver.photoUrl { onDriverImageClick(url, driver.name) }
                            }
                            .accessibilityLabel(settings.t("cd_view_photo"))
                            .accessibilityAddTraits(driver.photoUrl != nil ? .isButton : [])
                            .accessibilityHidden(driver.photoUrl == nil)

                            Text(driver.name).font(.body.weight(.semibold))
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .padding(20)
        }
    }
}

/// Vista previa a pantalla completa de una foto/logo — equivalente de
/// `ImagePreviewDialog`. Los logos usan `.fit` (nunca se recorta nada); las fotos de
/// piloto usan `.fill` + recorte desde arriba (la cara nunca se pierde).
private struct ImagePreviewView: View {
    let preview: ImagePreview
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettingsRepository.self) private var settings

    var body: some View {
        ZStack {
            Color.black.opacity(0.92).ignoresSafeArea()
                .onTapGesture { dismiss() }

            VStack(spacing: 16) {
                RemoteImage(urlString: preview.url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: preview.isLogo ? .fit : .fill)
                    case .failure:
                        Image(systemName: "person.fill").font(.system(size: 64)).foregroundStyle(.white)
                    default:
                        ProgressView().tint(.white)
                    }
                }
                .frame(width: UIScreen.main.bounds.width * 0.9, height: UIScreen.main.bounds.width * 0.9)
                .clipped()
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: PitBoardShapes.large))

                Text(preview.label)
                    .font(.title3.bold())
                    .foregroundStyle(.white)
            }

            VStack {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").foregroundStyle(.white).padding()
                    }
                    .accessibilityLabel(settings.t("cd_close_preview"))
                }
                Spacer()
            }
        }
    }
}

private struct FilterChipView: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    @Environment(\.pitBoardColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? colors.primaryContainer : colors.surfaceVariant.opacity(0.5))
                .foregroundStyle(selected ? colors.onPrimaryContainer : colors.onSurfaceVariant)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
