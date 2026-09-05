import SwiftUI

/// Aviso de "sin conexión, viendo la última actualización guardada" — equivalente exacto
/// de `OfflineBanner.kt`.
public struct OfflineBanner: View {
    public init() {}

    public var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash")
                .font(.footnote)
                // Decorativo: el texto de al lado ya dice "Sin conexión".
                .accessibilityHidden(true)
            Text("Sin conexión — mostrando la última actualización guardada")
                .font(.footnote)
        }
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(uiColor: .secondarySystemBackground))
    }
}
