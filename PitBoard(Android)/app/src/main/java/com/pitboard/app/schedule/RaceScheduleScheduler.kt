package com.pitboard.app.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * A diferencia de las clasificaciones (semanales, solo cambian tras cada carrera), los
 * horarios de sesión de un fin de semana pueden reprogramarse por lluvia, TV, etc. hasta pocos
 * días antes — por eso aquí la sincronización es DIARIA en vez de semanal. Es funcionalidad
 * base de la app (ya no un interruptor opt-in como Clasificaciones), así que se programa sola
 * al arrancar (ver PitBoardApplication) con KEEP, igual que SyncWorker.
 */
object RaceScheduleScheduler {
    private const val UNIQUE_WORK_NAME = "pitboard_race_schedule_sync"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RaceScheduleSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Sincronización inmediata bajo demanda — primer arranque (BD vacía) o botón "Actualizar"
     *  en Eventos. Sin restricción de red: si no hay conexión, doWork() lo detecta y pide
     *  reintento (ver el mismo razonamiento en StandingsScheduler.syncNow). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RaceScheduleSyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
