package com.pitboard.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pitboard.app.MainActivity
import com.pitboard.app.PitBoardApplication
import com.pitboard.app.R
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.util.DateTimeFormatters

class EventReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        val title = inputData.getString(KEY_EVENT_TITLE) ?: return Result.failure()
        val minutesBefore = inputData.getInt(KEY_MINUTES_BEFORE, 60)
        val badge = inputData.getString(KEY_BADGE).orEmpty()
        val startTimeUtc = inputData.getLong(KEY_START_TIME_UTC, -1L)

        showNotification(eventId, title, minutesBefore, badge, startTimeUtc)
        return Result.success()
    }

    @SuppressLint("MissingPermission") // comprobado manualmente justo abajo
    private fun showNotification(eventId: Long, title: String, minutesBefore: Int, badge: String, startTimeUtc: Long) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            eventId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            PitBoardApplication.EVENT_REMINDER_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headlineFor(minutesBefore, badge, startTimeUtc))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(eventId.toInt(), notification)
    }

    // "1 hora" es el único caso que no se dice en minutos en español natural — el resto
    // (15, 30) se leen bien como "X minutos" directamente
    private fun headlineFor(minutesBefore: Int, badge: String, startTimeUtc: Long): String {
        val session = SessionBadgeType.label(badge).lowercase()
        val timeLabel = when (minutesBefore) {
            60 -> "1 hora"
            else -> "$minutesBefore minutos"
        }
        val base = when (badge) {
            SessionBadgeType.RACE -> "Carrera en $timeLabel"
            SessionBadgeType.QUALY -> "Calificación en $timeLabel"
            SessionBadgeType.SPRINT -> "Sprint en $timeLabel"
            SessionBadgeType.PRACTICE -> "Libres en $timeLabel"
            else -> "$session en $timeLabel"
        }
        // Hora exacta de inicio entre paréntesis, ej: "Carrera en 1 hora (15:00)"
        if (startTimeUtc <= 0L) return base
        return "$base (${DateTimeFormatters.formatTimeOnly(startTimeUtc)})"
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_EVENT_TITLE = "event_title"
        const val KEY_MINUTES_BEFORE = "minutes_before"
        const val KEY_BADGE = "event_badge"
        const val KEY_START_TIME_UTC = "start_time_utc"
    }
}