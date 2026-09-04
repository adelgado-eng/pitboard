package com.pitboard.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.notifications.NotificationScheduler
import com.pitboard.app.widget.RaceWidget
import com.pitboard.app.widget.StandingsWidget
import java.util.concurrent.TimeUnit

/**
 * Última pieza del rompecabezas: sin esto, un evento que ya pasó solo desaparecería del
 * widget cuando abrieras la app, y los recordatorios (paso 32) solo se programarían tras
 * una importación manual. Con este worker corriendo en segundo plano, ambas cosas se
 * mantienen frescas solas.
 *
 * 30/08/2026 (2): este worker YA NO vuelve a leer los archivos .ics importados. Lo hacía
 * cada 30 minutos, y como el .ics es una copia local que no cambia, era trabajo inútil con
 * un riesgo real de vaciar un calendario entero (ver IcsImportRepository). Lo que sí sigue
 * haciendo — y es para lo único que hace falta que corra — es repintar los widgets y
 * reprogramar los avisos con la hora actual.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getInstance(applicationContext)

            // Repinta TODOS los widgets PitBoard que haya en el home, para que el filtro
            // de "eventos pasados" (EventDao.observeUpcoming, paso 13) se aplique con la
            // hora actual sin que el usuario tenga que abrir la app
            RaceWidget.instance.updateAll(applicationContext)
            // 04/09/2026: mismo motivo que RaceWidget — este ciclo cada 30 min es la red de
            // seguridad que le faltaba al widget de Clasificación si su primera actualización
            // se pierde por la gestión de batería de Samsung (ver StandingsSyncWorker).
            StandingsWidget.instance.updateAll(applicationContext)

            // Y reprograma los recordatorios de los eventos que siguen por venir (ver
            // ExistingWorkPolicy.REPLACE en NotificationScheduler, paso 32)
            val notificationScheduler = NotificationScheduler(
                context = applicationContext,
                eventDao = database.eventDao(),
                appSettingsRepository = AppSettingsRepository(applicationContext)
            )
            notificationScheduler.rescheduleAllUpcoming()

            Result.success()
        } catch (e: Exception) {
            // Reintenta más tarde (WorkManager aplica backoff exponencial) en vez de
            // dejar el widget y los recordatorios desactualizados hasta el próximo ciclo
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pitboard_periodic_sync"

        /**
         * Se llama una única vez desde PitBoardApplication.onCreate (paso 29).
         * KEEP hace que si ya hay un trabajo programado no se reinicie el contador
         * cada vez que el proceso de la app arranca.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
