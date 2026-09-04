import Foundation
import UserNotifications
import UIKit

/// Todo lo relacionado con el permiso de notificaciones en un único sitio — equivalente
/// de `NotificationPermission.kt`. iOS simplifica bastante el caso de Android: no hay
/// distinción por versión de SO (todas las versiones soportadas piden el permiso en
/// tiempo de ejecución) y `authorizationStatus` ya dice directamente si está bloqueado
/// (`.denied`) sin la comprobación indirecta de "rationale" que hace falta en Android.
public enum NotificationPermission {

    /// Pide el permiso al sistema — solo tiene efecto la primera vez; en llamadas
    /// posteriores el sistema devuelve el estado ya decidido sin mostrar ningún diálogo.
    public static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    public static func currentStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    public static func isGranted() async -> Bool {
        let status = await currentStatus()
        return status == .authorized || status == .provisional
    }

    /// true cuando el sistema ya no volverá a mostrar el diálogo — equivalente de
    /// `isBlockedBySystem` en Android; en iOS esto es simplemente `.denied` (ya sea "dijo
    /// que no" la primera vez, o lo desactivó luego desde Ajustes).
    public static func isBlockedBySystem() async -> Bool {
        await currentStatus() == .denied
    }

    /// Abre la pantalla de notificaciones de la app dentro de Ajustes del sistema.
    @MainActor
    public static func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
