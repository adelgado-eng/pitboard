package com.pitboard.app.standings

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitboard.app.data.AppDatabase

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