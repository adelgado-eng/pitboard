import SwiftUI

/// Estado "aquí no hay nada" reutilizable — equivalente exacto de `EmptyState.kt`. Icono +
/// título + mensaje, con un slot opcional para una acción (ej. "Quitar filtro").
public struct EmptyStateView<Action: View>: View {
    private let systemImage: String
    private let title: String
    private let message: String
    private let action: Action

    public init(
        systemImage: String,
        title: String,
        message: String,
        @ViewBuilder action: () -> Action
    ) {
        self.systemImage = systemImage
        self.title = title
        self.message = message
        self.action = action()
    }

    public var body: some View {
        VStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundStyle(.secondary)
                .padding(.bottom, 4)
                // Decorativo: el título y el mensaje de abajo ya explican el estado.
                .accessibilityHidden(true)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            action
                .padding(.top, 8)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

public extension EmptyStateView where Action == EmptyView {
    init(systemImage: String, title: String, message: String) {
        self.init(systemImage: systemImage, title: title, message: message) { EmptyView() }
    }
}
