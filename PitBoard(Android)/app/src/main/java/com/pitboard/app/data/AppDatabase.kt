package com.pitboard.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pitboard.app.standings.CarDriverDao
import com.pitboard.app.standings.CarDriverEntity
import com.pitboard.app.standings.StandingDao
import com.pitboard.app.standings.StandingEntity
import com.pitboard.app.standings.StandingsConverters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Punto único de acceso a la base de datos local. Esto es lo que resuelve tu requisito
 * de que la información "no se pierda al cerrar la app" — Room persiste en un archivo
 * SQLite en el almacenamiento interno de la app, sobrevive a cerrar la app, reiniciar
 * el móvil, etc. Solo se borra si el usuario desinstala la app o borra sus datos manualmente.
 */
@Database(
    entities = [
        SeriesConfigEntity::class,
        EventEntity::class,
        StandingEntity::class,
        CarDriverEntity::class
    ],
    version = 9,
    exportSchema = true // guarda el historial de esquema en app/schemas/ (activado en build.gradle.kts, paso 6)
)
@TypeConverters(StandingsConverters::class, RaceSeriesConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun seriesConfigDao(): SeriesConfigDao
    abstract fun eventDao(): EventDao
    abstract fun standingDao(): StandingDao
    abstract fun carDriverDao(): CarDriverDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN timeZoneId TEXT")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_events_calendarSourceId_seriesName ON events (calendarSourceId, seriesName)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Asegurar carga de librerías de SQLCipher en cada acceso
                try {
                    SQLiteDatabase.loadLibs(context)
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Error cargando librerías nativas", e)
                }

                val passphraseResult = DatabasePassphraseProvider.getOrCreatePassphrase(context.applicationContext)
                if (passphraseResult.isNewlyGenerated) {
                    // Cualquier BD que ya exista en disco fue cifrada con la passphrase
                    // hardcodeada anterior ("pitboard-secure-key-2026") y no se puede abrir
                    // con la nueva passphrase aleatoria — se borra y se recrea limpia en vez
                    // de intentar migrar el cifrado. Sin usuarios en producción todavía
                    // (v0.1.0, ver fallbackToDestructiveMigration más abajo), no hay pérdida
                    // real de datos: todo aquí es caché de eventos/clasificaciones públicas
                    // que se vuelve a sincronizar sola.
                    context.applicationContext.getDatabasePath("pitboard_v2.db").delete()
                }
                val factory = SupportFactory(passphraseResult.passphrase)

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitboard_v2.db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // v6 -> v7: se elimina el calendario manual (.ics importados a mano) a
                    // favor de la sincronización automática por serie (ver RaceSeries) —
                    // calendar_sources y categories desaparecen y events cambia de forma.
                    // v7 -> v8: nueva tabla elms_car_drivers (pilotos por coche de ELMS).
                    // v8 -> v9: elms_car_drivers se generaliza a car_drivers (+ columna
                    // category) e IMSA/F2/F3 se añaden como categorías nuevas.
                    // Sin usuarios en producción todavía (v0.1.0), así que no merece la pena
                    // escribir SQL de migración de datos que nadie necesita: se recrea la BD.
                    .fallbackToDestructiveMigration()
                    // SQLCipher para Android usa una única conexión por base de datos (no
                    // tiene el pool de conexiones que WAL necesita para leer/escribir en
                    // paralelo). Con el modo automático de Room, WAL se activa solo y puede
                    // dejar la app colgada indefinidamente cuando una escritura en segundo
                    // plano (SyncWorker / StandingsSyncWorker) coincide con una lectura de
                    // la UI (los Flow de Room). TRUNCATE es el modo clásico, compatible con
                    // el modelo de conexión única de SQLCipher.
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build().also { INSTANCE = it }
            }
        }
    }
}