package com.pitboard.app.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Todo lo relacionado con el permiso POST_NOTIFICATIONS en un unico sitio, porque ahora
 * se pide desde DOS lugares distintos (el primer arranque de la app y el interruptor de
 * Ajustes) y la logica de "puedo ensenar el dialogo del sistema o ya esta bloqueado?"
 * es sutil y no debe duplicarse.
 *
 * Como se comporta Android 13+ (API 33):
 *   1a denegacion -> shouldShowRequestPermissionRationale = true  -> el dialogo se puede volver a mostrar
 *   2a denegacion -> shouldShowRequestPermissionRationale = false -> el sistema YA NO muestra nada:
 *                    launch() vuelve al instante con "denegado" sin que el usuario vea el dialogo.
 * En ese segundo caso la unica salida es mandar al usuario a los ajustes del sistema.
 */
object NotificationPermission {

    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    /** Solo desde Android 13 (API 33) el permiso se pide en tiempo de ejecucion. */
    val requiresRuntimeRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Puede la app mostrar notificaciones ahora mismo? */
    fun isGranted(context: Context): Boolean =
        if (requiresRuntimeRequest) {
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            // Antes de Android 13 no hay permiso que pedir: basta con que el usuario no
            // haya silenciado la app desde los ajustes del sistema.
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * true cuando el sistema ya no volvera a mostrar el dialogo (denegado "para siempre").
     * Solo tiene sentido consultarlo si ya se pidio el permiso alguna vez, por eso
     * [alreadyAsked] lo aporta quien llama (se guarda en AppSettingsRepository).
     */
    fun isBlockedBySystem(context: Context, alreadyAsked: Boolean): Boolean {
        if (isGranted(context)) return false
        if (!requiresRuntimeRequest) return true // aqui solo se puede arreglar desde ajustes
        if (!alreadyAsked) return false
        val activity = context.findActivity() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)
    }

    /** Abre la pantalla de notificaciones de la app dentro de los ajustes del sistema. */
    fun openSystemNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            // Algunas capas de fabricante no traen esa pantalla: caemos a la ficha de la app
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
