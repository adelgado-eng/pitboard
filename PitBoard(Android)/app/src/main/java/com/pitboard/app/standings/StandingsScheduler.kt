package com.pitboard.app.standings

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object StandingsScheduler {

    /**
     * Programa la sincronización semanal para el próximo lunes a las 12:00 (hora local del
     * dispositivo) — es cuando normalmente ya se han disputado todas las carreras del fin
     * de semana. A partir de ahí se repite cada 7 días.
     *
     * REPLACE (no KEEP, a diferencia de SyncWorker) porque esto se puede llamar cada vez
     * que el usuario activa el interruptor de Ajustes — si ya había uno programado con un
     * desfase distinto, queremos que el nuevo cálculo del "próximo lunes" gane.
     */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<StandingsSyncWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextMondayNoon())
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StandingsSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(StandingsSyncWorker.UNIQUE_WORK_NAME)
    }

    /** Sincronización inmediata bajo demanda — para cuando el usuario activa el
     *  interruptor por primera vez y no quiere esperar hasta el lunes.
     *
     *  30/08/2026: SIN restricción de red. Antes pedía NetworkType.CONNECTED igual que el
     *  trabajo periódico, pero eso deja el trabajo colgado en "Enqueued" para siempre en
     *  entornos donde WorkManager no llega a marcar la red como conectada (visto en el
     *  emulador de Android Studio: tiene internet real —confirmado— pero el trabajo nunca
     *  pasaba a "Running" en el Background Task Inspector). doWork() ya hace su propia
     *  comprobación con ConnectivityHelper.isOnline() y devuelve Result.retry() si no hay
     *  red, así que la restricción aquí era redundante y, en la práctica, más frágil que
     *  esa comprobación manual. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<StandingsSyncWorker>().build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun delayUntilNextMondayNoon(): Duration {
        val now = ZonedDateTime.now()
        var next = now
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            .withHour(12).withMinute(0).withSecond(0).withNano(0)

        // Si ya es lunes pasadas las 12:00, el "próximo lunes" es el de la semana siguiente
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1)
        }

        return Duration.between(now, next)
    }
}