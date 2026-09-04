import SwiftUI
import WidgetKit
import PitBoardKit

/// Paleta interna del widget de Clasificación — equivalente de `WidgetPalette` en
/// `RaceWidgetView.swift`, con los 3 colores de podio añadidos (oro/plata/bronce, mismos
/// valores que `PodiumColors` en la app, pero constantes fijas: `PodiumColors.forPosition`
/// depende de `@Environment(\.pitBoardColors)`, no disponible tal cual en un widget).
private struct StandingsPalette {
    let chalk: Color
    let chalkDim: Color
    let cardBg: Color
    let gold: Color
    let silver: Color
    let bronze: Color

    static let dark = StandingsPalette(
        chalk: Color(hex: "#EEF0F2")!, chalkDim: Color(hex: "#9AA0AB")!, cardBg: Color(hex: "#1C1F26")!,
        gold: Color(hex: "#FFC933")!, silver: Color(hex: "#C8CEDA")!, bronze: Color(hex: "#D98E4F")!
    )
    static let light = StandingsPalette(
        chalk: Color(hex: "#181815")!, chalkDim: Color(hex: "#5F6570")!, cardBg: Color(hex: "#FFFFFF")!,
        gold: Color(hex: "#8A6A00")!, silver: Color(hex: "#60666F")!, bronze: Color(hex: "#8A4A17")!
    )

    func podiumColor(for position: Int) -> Color? {
        switch position {
        case 1: gold
        case 2: silver
        case 3: bronze
        default: nil
        }
    }
}

/// Vista raíz del widget de Clasificación — equivalente de `RaceWidgetView`, sin fotos de
/// piloto/logo de equipo a propósito (ver el comentario en `StandingsWidget.kt`, mismo
/// criterio en las dos plataformas: nada de imágenes remotas dentro de un widget).
struct StandingsWidgetView: View {
    let entry: StandingsWidgetEntry
    @Environment(\.widgetFamily) private var family

    private var palette: StandingsPalette { entry.useDark ? .dark : .light }

    var body: some View {
        let backgroundColor = (Color(hex: entry.backgroundColorHex) ?? palette.cardBg)
            .opacity(WidgetPrefsConstants.backgroundOpacity)

        ZStack {
            backgroundColor

            if entry.rows.isEmpty {
                Text(Strings.get("standings_no_data_yet", language: entry.appLanguage))
                    .font(.caption)
                    .foregroundStyle(palette.chalkDim)
            } else {
                switch family {
                case .systemSmall:
                    HeroLeaderView(category: entry.category, leader: entry.rows[0], palette: palette, language: entry.appLanguage)
                case .systemLarge, .systemExtraLarge:
                    StandingsListView(entry: entry, palette: palette)
                default:
                    MiniLeaderView(leader: entry.rows[0], palette: palette, language: entry.appLanguage)
                }
            }
        }
        .containerBackground(for: .widget) { backgroundColor }
    }
}

// MARK: - .systemSmall — solo el líder, centrado

private struct HeroLeaderView: View {
    let category: StandingsCategory
    let leader: StandingDraft
    let palette: StandingsPalette
    let language: AppLanguage

    var body: some View {
        VStack(spacing: 6) {
            Text("🏆").font(.system(size: 20))
            Text(category.displayName)
                .font(.system(size: 10))
                .foregroundStyle(palette.chalkDim)
                .lineLimit(1)
            Text(leader.name)
                .font(.footnote.weight(.bold))
                .foregroundStyle(palette.chalk)
                .lineLimit(1)
                .multilineTextAlignment(.center)
            Text(String(format: Strings.get("standings_points", language: language), formatPoints(leader.points)))
                .font(.system(size: 11))
                .foregroundStyle(palette.chalkDim)
        }
        .padding(12)
        .background(palette.cardBg, in: RoundedRectangle(cornerRadius: 16))
        .padding(4)
    }
}

// MARK: - .systemMedium — fila compacta con el líder

private struct MiniLeaderView: View {
    let leader: StandingDraft
    let palette: StandingsPalette
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 8) {
            Text("🏆").font(.system(size: 16))
            VStack(alignment: .leading, spacing: 1) {
                Text(leader.name)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(palette.chalk)
                    .lineLimit(1)
                if !leader.team.isEmpty {
                    Text(leader.team)
                        .font(.system(size: 10))
                        .foregroundStyle(palette.chalkDim)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 4)
            Text(String(format: Strings.get("standings_points", language: language), formatPoints(leader.points)))
                .font(.system(size: 11))
                .foregroundStyle(palette.chalkDim)
        }
        .padding(12)
    }
}

// MARK: - .systemLarge — lista completa

private struct StandingsListView: View {
    let entry: StandingsWidgetEntry
    let palette: StandingsPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🏆 \(entry.category.displayName)")
                .font(.caption.weight(.medium))
                .foregroundStyle(palette.chalk)
                .lineLimit(1)

            VStack(spacing: 0) {
                ForEach(Array(entry.rows.enumerated()), id: \.offset) { index, row in
                    StandingRowView(row: row, palette: palette, language: entry.appLanguage)
                    if index < entry.rows.count - 1 {
                        Divider().background(palette.chalkDim.opacity(0.2))
                    }
                }
            }
            .background(palette.cardBg, in: RoundedRectangle(cornerRadius: 20))
        }
        .padding(10)
    }
}

private struct StandingRowView: View {
    let row: StandingDraft
    let palette: StandingsPalette
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 8) {
            Text("\(row.position)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(palette.podiumColor(for: row.position) ?? palette.chalkDim)
                .frame(width: 22, alignment: .leading)

            VStack(alignment: .leading, spacing: 1) {
                Text(row.name)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(palette.chalk)
                    .lineLimit(1)
                if !row.team.isEmpty {
                    Text(row.team)
                        .font(.system(size: 10))
                        .foregroundStyle(palette.chalkDim)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 4)

            Text(String(format: Strings.get("standings_points", language: language), formatPoints(row.points)))
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(palette.chalk)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}
