package com.pitboard.app.standings

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.widget.StandingsWidget

class StandingsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("StandingsSync", "doWork: arrancando")

        if (!ConnectivityHelper.isOnline(applicationContext)) {
            // Sin conexión ahora mismo: no tiene sentido intentar las 7 fuentes. WorkManager
            // reintentará este mismo trabajo más tarde con su backoff habitual.
            Log.w("StandingsSync", "doWork: sin conexión según ConnectivityHelper.isOnline(), reintentando más tarde")
            return Result.retry()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val repository = StandingsRepository(database.standingDao(), database.carDriverDao())
        val result = repository.syncAll()

        Log.d(
            "StandingsSync",
            "doWork: terminado — éxito=${result.succeeded} fallo=${result.failed}"
        )

        // 04/09/2026: bug real reportado — sin esto, el widget de Clasificación no tenía
        // NINGÚN disparador propio de actualización (a diferencia de RaceWidget, que este
        // mismo patrón ya repinta desde RaceScheduleSyncWorker/SyncWorker/PitBoardApplication).
        // Si la primera actualización tras colocar el widget se pierde por la gestión de
        // batería de Samsung (proceso congelado antes de que provideGlance llegue a
        // terminar — ver el comentario de StandingsWidget.kt), sin este repintado aquí no
        // había ninguna otra ocasión en la que fuera a completarse sola.
        StandingsWidget.instance.updateAll(applicationContext)

        // Éxito si AL MENOS una fuente funcionó — no tiene sentido reintentar las 7 solo
        // porque, por ejemplo, driverdb.com esté caído puntualmente mientras F1 y MotoGP
        // sí se guardaron bien.
        return if (result.succeeded.isNotEmpty() || result.failed.isEmpty()) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "pitboard_standings_sync"
    }
}