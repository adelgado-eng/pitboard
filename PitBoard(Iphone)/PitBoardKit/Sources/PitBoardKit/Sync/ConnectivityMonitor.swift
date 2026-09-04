import Foundation
import Network

/// Equivalente de `ConnectivityHelper.kt`. Diferencia deliberada de plataforma: Android
/// consulta el estado ACTIVO en el momento exacto de la llamada
/// (`ConnectivityManager.getNetworkCapabilities`). `NWPathMonitor` de iOS está pensado
/// para vivir todo el tiempo que la app esté activa y entregar actualizaciones — así que
/// en vez de crear un monitor nuevo en cada comprobación (caro y nada idiomático), se
/// mantiene uno compartido y se lee su último valor conocido, que en la práctica está
/// disponible casi al instante tras el arranque.
public final class ConnectivityMonitor: @unchecked Sendable {
    public static let shared = ConnectivityMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.pitboard.app.connectivity")
    private let lock = NSLock()
    private var cachedIsOnline = true

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            self.lock.lock()
            self.cachedIsOnline = path.status == .satisfied
            self.lock.unlock()
        }
        monitor.start(queue: queue)
    }

    /// true si la red activa tiene salida a internet real (equivalente de
    /// `NET_CAPABILITY_VALIDATED` + `NET_CAPABILITY_INTERNET` en Android) — se usa antes
    /// de intentar cualquier sincronización manual y para decidir qué mostrar en
    /// Eventos/Clasificaciones cuando no hay caché previa.
    public var isOnline: Bool {
        lock.lock()
        defer { lock.unlock() }
        return cachedIsOnline
    }
}
