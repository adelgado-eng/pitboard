import SwiftUI

/// Aviso de "sin conexión, viendo la última actualización guardada" — equivalente exacto
/// de `OfflineBanner.kt`.
///
/// 05/09/2026 (Fase 3, i18n): el mensaje llega ya traducido desde quien lo usa (mismo
/// patrón que `EmptyStateView`) en vez de leer `AppSettingsRepository` aquí dentro — este
/// componente vive en PitBoardKit y así se queda desacoplado del idioma activo, más fácil
/// de testear.
public struct OfflineBanner: View {
    private let message: String

    public init(message: String) {
        self.message = message
    }

    public var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash")
                .font(.footnote)
                // Decorativo: el texto de al lado ya dice "Sin conexión".
                .accessibilityHidden(true)
            Text(message)
                .font(.footnote)
        }
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(uiColor: .secondarySystemBackground))
    }
}
