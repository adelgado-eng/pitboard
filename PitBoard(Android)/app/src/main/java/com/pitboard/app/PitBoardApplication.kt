package com.pitboard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.pitboard.app.sync.SyncWorker
import net.sqlcipher.database.SQLiteDatabase
import okhttp3.OkHttpClient

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pitboard.app.data.AppDatabase
import com.pitboard.app.data.RaceSeries
import com.pitboard.app.data.SeriesConfigEntity
import com.pitboard.app.schedule.RaceScheduleScheduler
import com.pitboard.app.standings.StandingsScheduler
import com.pitboard.app.util.SeasonWindow
import com.pitboard.app.widget.RaceWidget
import com.pitboard.app.widget.StandingsWidget
import androidx.glance.appwidget.updateAll

/**
 * Antes esto vivía suelto en MainActivity.onCreate — lo movemos a una Application propia
 * porque el canal de notificaciones y la sincronización periódica deben existir desde el
 * primer instante en que el proceso arranca, no solo cuando el usuario abre la pantalla
 * principal (ej: si Android despierta la app en segundo plano para ejecutar SyncWorker,
 * MainActivity puede no haberse creado nunca en ese arranque).
 */
class PitBoardApplication : Application(), ImageLoaderFactory {

    /**
     * 02/09/2026: sin esto, todos los logos de equipo/piloto alojados en Wikimedia
     * (wikimedia.org) se veían en blanco — Coil usa un OkHttpClient con el User-Agent por
     * defecto de OkHttp ("okhttp/4.12.0"), y Wikimedia responde 403 a ese User-Agent en
     * concreto (comprobado a mano: con un User-Agent de navegador o de app carga bien, con el
     * de OkHttp sin más no). Aquí se fuerza uno propio para TODAS las imágenes de la app
     * (AsyncImage/SubcomposeAsyncImage), no solo las de MotoGP — F1AcademyStandingsSource
     * también usa Wikimedia para sus logos de equipo y tenía el mismo problema en silencio.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PitBoard/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            // 03/09/2026: los logos de equipo de Fórmula E son SVG (ver
            // FormulaEStandingsSource) — sin este decoder registrado, Coil los descarga bien
            // pero no sabe pintarlos y se quedan en el icono por defecto.
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        // Cargar librerías de SQLCipher antes de cualquier operación de BD
        SQLiteDatabase.loadLibs(this)
        
        createNotificationChannel()
        SyncWorker.schedulePeriodic(this)
        RaceScheduleScheduler.schedulePeriodic(this)

        // Primer arranque: siembra el tag/color por defecto de las 15 series (ver RaceSeries)
        // y, si la BD de eventos está vacía (instalación nueva o recién migrada desde la v6,
        // ver AppDatabase), lanza una sincronización inmediata para no dejar Eventos vacío
        // hasta el primer ciclo diario.
        //
        // 04/09/2026: Dispatchers.IO explícito en los 3 GlobalScope.launch de aquí abajo — bug
        // real reportado (los widgets se quedaban con el icono de carga fijo, sin ningún error
        // en el log). AppDatabase.getInstance() aquí desencripta la BD con SQLCipher, un trabajo
        // pesado que sin dispatcher explícito corre en Dispatchers.Default — el mismo dispatcher
        // que usa GlanceAppWidgetReceiver.onReceive() para procesar el broadcast que dispara la
        // primera composición del widget (ver GlanceAppWidgetReceiver.kt de la librería). Si el
        // pool compartido de Default está saturado por la desencriptación + las 2 sincronizaciones
        // de aquí abajo justo en el arranque, la corrutina del widget se queda sin hueco, expira
        // el timeout de 10s de goAsync() y Glance la cancela en silencio (CancellationException
        // que la propia librería traga sin loguear nada).
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(this@PitBoardApplication)
                if (database.seriesConfigDao().count() == 0) {
                    database.seriesConfigDao().insertAllIfNew(
                        RaceSeries.entries.map { SeriesConfigEntity(it, it.defaultTag, it.defaultColorHex) }
                    )
                }
                val nowUtc = System.currentTimeMillis()
                if (database.eventDao().getAllUpcoming(nowUtc, SeasonWindow.endOfCurrentYearUtc(nowUtc)).isEmpty()) {
                    RaceScheduleScheduler.syncNow(this@PitBoardApplication)
                }
            } catch (_: Exception) {
                // Silencioso — el ciclo diario programado arriba lo intentará de nuevo
            }
        }

        // 03/09/2026: precarga de Clasificaciones en segundo plano en el primer arranque —
        // pedido explícito para que, en cuanto el usuario active el interruptor en Ajustes
        // (sigue APAGADO por defecto a propósito, ver AppSettingsRepository.standingsEnabled),
        // los datos ya estén ahí en vez de esperar a la primera sincronización. Sin esto, la
        // clasificación de 13 fuentes tarda un rato la primera vez que se activa.
        //
        // Es justo StandingsScheduler.syncNow() SIN el .schedule() que sí se llama al activar
        // el interruptor a mano (ver SettingsViewModel.setStandingsEnabled): esto es un
        // "una sola vez" para tener algo que enseñar, no arranca el ciclo semanal — mientras
        // el interruptor siga apagado, no debe seguir pidiendo nada a internet por su cuenta.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(this@PitBoardApplication)
                if (database.standingDao().getLastUpdatedOverall() == null) {
                    StandingsScheduler.syncNow(this@PitBoardApplication)
                }
            } catch (_: Exception) {
                // Silencioso — si el usuario activa el interruptor más tarde, ese mismo botón
                // ya lanza su propia sincronización inmediata.
            }
        }

        // Estrategia Samsung: Forzar actualización de widgets al arrancar la app.
        // 04/09/2026: añadido StandingsWidget aquí también, igual que RaceWidget.
        GlobalScope.launch(Dispatchers.IO) {
            delay(2000)
            try {
                RaceWidget.instance.updateAll(this@PitBoardApplication)
                StandingsWidget.instance.updateAll(this@PitBoardApplication)
            } catch (e: Exception) {
                Log.e("PitBoardWidgets", "Fallo forzando actualizacion", e)
            }
        }
    }

    private fun createNotificationChannel() {
        // NotificationChannel solo existe desde Android 8 (API 26) — en versiones
        // anteriores las notificaciones no lo necesitan, así que el check es obligatorio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EVENT_REMINDER_CHANNEL_ID,
                "Recordatorios de eventos",
                NotificationManager.IMPORTANCE_HIGH // heads-up: quieres verlo aunque no mires el móvil
            ).apply {
                description = "Aviso 1 hora antes de cada carrera o sesión"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val EVENT_REMINDER_CHANNEL_ID = "event_reminders"
    }
}
