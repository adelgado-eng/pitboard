package com.pitboard.app.schedule

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.notifications.NotificationScheduler
import com.pitboard.app.standings.ConnectivityHelper
import com.pitboard.app.widget.RaceWidget

class RaceScheduleSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("RaceScheduleSync", "doWork: arrancando")

        if (!ConnectivityHelper.isOnline(applicationContext)) {
            Log.w("RaceScheduleSync", "doWork: sin conexión, reintentando más tarde")
            return Result.retry()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val repository = RaceScheduleRepository(database.eventDao())
        val result = repository.syncAll()

        Log.d(
            "RaceScheduleSync",
            "doWork: terminado — éxito=${result.succeeded} fallo=${result.failed}"
        )

        // Sesiones nuevas u horarios reprogramados: hay que reprogramar los avisos y repintar
        // el widget, igual que hace SyncWorker tras cada ciclo periódico.
        val notificationScheduler = NotificationScheduler(
            context = applicationContext,
            eventDao = database.eventDao(),
            appSettingsRepository = AppSettingsRepository(applicationContext)
        )
        notificationScheduler.rescheduleAllUpcoming()
        RaceWidget.instance.updateAll(applicationContext)

        // Éxito si AL MENOS una serie funcionó — no tiene sentido reintentar las 15 solo
        // porque, por ejemplo, Wikipedia esté caída puntualmente mientras F1 y NASCAR sí.
        return if (result.succeeded.isNotEmpty() || result.failed.isEmpty()) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
