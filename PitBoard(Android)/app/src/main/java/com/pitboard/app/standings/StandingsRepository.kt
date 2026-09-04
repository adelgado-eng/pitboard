package com.pitboard.app.standings

import android.util.Log
import com.pitboard.app.standings.sources.AcoCarDriversSource
import com.pitboard.app.standings.sources.ElmsDriversSource
import com.pitboard.app.standings.sources.ElmsStandingsSource
import com.pitboard.app.standings.sources.F1AcademyStandingsSource
import com.pitboard.app.standings.sources.F1StandingsSource
import com.pitboard.app.standings.sources.F2StandingsSource
import com.pitboard.app.standings.sources.F3StandingsSource
import com.pitboard.app.standings.sources.FormulaEStandingsSource
import com.pitboard.app.standings.sources.ImsaStandingsSource
import com.pitboard.app.standings.sources.IndyCarStandingsSource
import com.pitboard.app.standings.sources.LeMansCupStandingsSource
import com.pitboard.app.standings.sources.Moto2StandingsSource
import com.pitboard.app.standings.sources.Moto3StandingsSource
import com.pitboard.app.standings.sources.MotoGpStandingsSource
import com.pitboard.app.standings.sources.NascarStandingsSource
import com.pitboard.app.standings.sources.PorscheSupercupStandingsSource
import com.pitboard.app.standings.sources.WecStandingsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StandingsRepository(
    private val dao: StandingDao,
    private val carDriverDao: CarDriverDao,
    private val sources: List<StandingsSource> = listOf(
        F1StandingsSource(),
        MotoGpStandingsSource(),
        Moto2StandingsSource(),
        Moto3StandingsSource(),
        NascarStandingsSource(),
        IndyCarStandingsSource(),
        F1AcademyStandingsSource(),
        PorscheSupercupStandingsSource(),
        ElmsStandingsSource(),
        F2StandingsSource(),
        F3StandingsSource(),
        WecStandingsSource(),
        LeMansCupStandingsSource(),
        FormulaEStandingsSource()
    ),
    private val elmsDriversSource: ElmsDriversSource = ElmsDriversSource(),
    private val imsaStandingsSource: ImsaStandingsSource = ImsaStandingsSource(),
    // WEC y Le Mans Cup comparten la misma fuente de pilotos por coche (ver
    // AcoCarDriversSource) — a diferencia de IMSA, aquí el logo de equipo ya viene en la
    // propia tabla de clasificación, así que estas dos SÍ encajan en el patrón de
    // ElmsDriversSource (fuente de pilotos aparte, no una fusionada con la clasificación).
    private val wecDriversSource: AcoCarDriversSource = AcoCarDriversSource(
        category = StandingsCategory.WEC,
        listingUrl = "https://www.fiawec.com/en/page/grid",
        classMatchers = listOf(
            StandingsClass.HYPERCAR to { t: String -> t.contains("Hypercar", ignoreCase = true) },
            StandingsClass.LMGT3 to { t: String -> t.contains("LMGT3", ignoreCase = true) }
        )
    ),
    private val leMansCupDriversSource: AcoCarDriversSource = AcoCarDriversSource(
        category = StandingsCategory.LEMANS_CUP,
        listingUrl = "https://www.lemanscup.com/en/car/${java.time.Year.now().value}",
        classMatchers = listOf(
            // Pro/Am antes que el genérico LMP3, mismo motivo que en LeMansCupStandingsSource.
            StandingsClass.LMP3_PRO_AM to { t: String -> t.contains("LMP3", ignoreCase = true) && t.contains("Pro", ignoreCase = true) },
            StandingsClass.LMP3 to { t: String -> t.contains("LMP3", ignoreCase = true) },
            StandingsClass.GT3 to { t: String -> t.contains("GT3", ignoreCase = true) }
        )
    )
) {

    fun observe(category: StandingsCategory, standingsClass: StandingsClass, type: StandingType): Flow<List<StandingEntity>> =
        dao.observe(category, standingsClass, type)

    fun observeLastUpdated(category: StandingsCategory): Flow<Long?> =
        dao.observeLastUpdated(category)

    /** Los pilotos de un coche de ELMS, IMSA, WEC o Le Mans Cup (ver ElmsDriversSource,
     *  ImsaStandingsSource, AcoCarDriversSource) — vacío si esa fuente todavía no ha
     *  sincronizado nada para este coche. */
    fun observeCarDrivers(category: StandingsCategory, standingsClass: StandingsClass, carNumber: String): Flow<List<CarDriverEntity>> =
        carDriverDao.driversForCar(category, standingsClass, carNumber)

    suspend fun getLastUpdatedOverall(): Long? = dao.getLastUpdatedOverall()

    /**
     * Sincroniza las 13 fuentes de StandingsSource EN PARALELO y de forma independiente:
     * si una falla (web caída, cambio de diseño, sin conexión puntual...), las demás se
     * guardan igual. Esta es justo la lección del bug de SyncWorker.kt (un calendario
     * roto bloqueaba la resincronización de TODOS los demás) — aquí no se repite.
     *
     * Si una fuente falla o devuelve una lista vacía, su caché anterior en Room se deja
     * intacta (nunca se sustituye por "nada") — así una categoría con problemas pasajeros
     * sigue mostrando los últimos datos buenos en vez de quedarse en blanco.
     *
     * 30/08/2026 — EL HILO (causa raíz de "0 ok, 7 fallidas"). Las fuentes hacen la
     * petición con OkHttp de forma BLOQUEANTE (`newCall(...).execute()`), y `fetch()` no
     * cambiaba de dispatcher por su cuenta. Desde StandingsViewModel.refreshNow() esto se
     * llamaba en `viewModelScope`, que es Dispatchers.Main: `coroutineScope { async { } }`
     * HEREDA el dispatcher de quien lo llama, así que las corrutinas se ejecutaban en el
     * hilo principal y Android las mataba a todas con NetworkOnMainThreadException —
     * excepción que el `runCatching` de aquí atrapaba y convertía en "fallo" sin más. No
     * era ni un problema de red ni de las webs: era el hilo. Ahora todo el trabajo va a
     * Dispatchers.IO, tanto la envoltura (`withContext`) como cada rama (`async(IO)`, por
     * si en el futuro alguien llama a syncAll() desde otro contexto).
     */
    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val nowUtc = System.currentTimeMillis()

        val (standingsResults, imsaOutcome) = coroutineScope {
            val standingsDeferred = sources.map { source ->
                async(Dispatchers.IO) { source.category to runCatching { source.fetch(nowUtc) } }
            }
            // Pilotos por coche de ELMS/IMSA: fuentes y tabla aparte de las de arriba,
            // pero lanzadas en el mismo coroutineScope para que un fallo aquí (web caída,
            // cambio de maquetación) no retrase ni tumbe la sync de las demás categorías
            // — mismo aislamiento de fallos que el resto.
            val elmsDriversDeferred = async(Dispatchers.IO) { runCatching { elmsDriversSource.fetch(nowUtc) } }
            val wecDriversDeferred = async(Dispatchers.IO) { runCatching { wecDriversSource.fetch(nowUtc) } }
            val leMansCupDriversDeferred = async(Dispatchers.IO) { runCatching { leMansCupDriversSource.fetch(nowUtc) } }
            // IMSA no encaja en el bucle genérico de arriba: a diferencia de las demás
            // fuentes, su clasificación (StandingEntity) y sus pilotos por coche
            // (CarDriverEntity) salen de la MISMA visita a cada página de equipo — ver
            // KDoc de ImsaStandingsSource sobre por qué no se separó en dos fuentes.
            val imsaDeferred = async(Dispatchers.IO) { runCatching { imsaStandingsSource.fetch(nowUtc) } }

            val standingsResults = standingsDeferred.awaitAll()

            elmsDriversDeferred.await().onSuccess { rows ->
                if (rows.isNotEmpty()) {
                    carDriverDao.replaceCategory(StandingsCategory.ELMS, rows)
                } else {
                    Log.w("StandingsSync", "ELMS drivers: la fuente respondió pero sin filas (0 resultados)")
                }
            }.onFailure { error ->
                Log.e("StandingsSync", "ELMS drivers: fallo al obtener datos", error)
            }

            wecDriversDeferred.await().onSuccess { rows ->
                if (rows.isNotEmpty()) {
                    carDriverDao.replaceCategory(StandingsCategory.WEC, rows)
                } else {
                    Log.w("StandingsSync", "WEC drivers: la fuente respondió pero sin filas (0 resultados)")
                }
            }.onFailure { error ->
                Log.e("StandingsSync", "WEC drivers: fallo al obtener datos", error)
            }

            leMansCupDriversDeferred.await().onSuccess { rows ->
                if (rows.isNotEmpty()) {
                    carDriverDao.replaceCategory(StandingsCategory.LEMANS_CUP, rows)
                } else {
                    Log.w("StandingsSync", "Le Mans Cup drivers: la fuente respondió pero sin filas (0 resultados)")
                }
            }.onFailure { error ->
                Log.e("StandingsSync", "Le Mans Cup drivers: fallo al obtener datos", error)
            }

            val imsaResult = imsaDeferred.await()
            val imsaOutcome = imsaResult.fold(
                onSuccess = { result ->
                    if (result.standings.isEmpty()) {
                        Log.w("StandingsSync", "IMSA: la fuente respondió pero sin filas (0 resultados)")
                        CategoryOutcome(
                            category = StandingsCategory.IMSA, ok = false, rowCount = 0,
                            detail = NO_DATA_FOUND_MESSAGE
                        )
                    } else {
                        dao.replaceCategory(StandingsCategory.IMSA, result.standings)
                        // Los pilotos son mejor esfuerzo (una página por coche, ver
                        // ImsaStandingsSource) — si vinieran vacíos no se toca la caché
                        // anterior de pilotos, pero la clasificación de coches sí se
                        // guarda igual (ya se comprobó que no está vacía arriba).
                        if (result.carDrivers.isNotEmpty()) {
                            carDriverDao.replaceCategory(StandingsCategory.IMSA, result.carDrivers)
                        }
                        CategoryOutcome(category = StandingsCategory.IMSA, ok = true, rowCount = result.standings.size, detail = null)
                    }
                },
                onFailure = { error ->
                    Log.e("StandingsSync", "IMSA: fallo al obtener datos", error)
                    CategoryOutcome(category = StandingsCategory.IMSA, ok = false, rowCount = 0, detail = friendlyReason(error))
                }
            )

            standingsResults to imsaOutcome
        }

        val outcomes = standingsResults.map { (category, result) ->
            // Antes esto tragaba cualquier excepción en silencio (runCatching sin log
            // arriba) — no había forma de saber por qué una categoría se quedaba sin
            // datos. Ahora queda en Logcat con la excepción real Y viaja hasta la UI
            // dentro de CategoryOutcome.detail (fase 2).
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.e("StandingsSync", "$category: fallo al obtener datos", error)
                return@map CategoryOutcome(
                    category = category,
                    ok = false,
                    rowCount = 0,
                    detail = friendlyReason(error)
                )
            }

            val rows = result.getOrNull().orEmpty()
            if (rows.isEmpty()) {
                Log.w("StandingsSync", "$category: la fuente respondió pero sin filas (0 resultados)")
                return@map CategoryOutcome(
                    category = category,
                    ok = false,
                    rowCount = 0,
                    detail = NO_DATA_FOUND_MESSAGE
                )
            }

            dao.replaceCategory(category, rows)
            CategoryOutcome(category = category, ok = true, rowCount = rows.size, detail = null)
        } + imsaOutcome

        SyncResult(outcomes)
    }

    /**
     * Resume una excepción en una línea legible para Logcat: el tipo más el mensaje, y hasta
     * dos causas encadenadas — el motivo de verdad suele estar en la causa
     * (`IllegalStateException: ... HTTP 403` envuelta, o `SocketTimeoutException` dentro de
     * un fallo de OkHttp), no en la excepción de arriba. SOLO para Log.e — nunca llega a la
     * UI (ver friendlyReason(), que es lo que sí ve el usuario).
     */
    private fun describe(error: Throwable): String =
        generateSequence(error) { it.cause }
            .take(3)
            .map { t ->
                val type = t::class.java.simpleName
                val message = t.message?.trim()?.takeIf { it.isNotBlank() }?.take(200)
                if (message == null) type else "$type: $message"
            }
            .joinToString(separator = "\n   ← causa: ")

    /**
     * Traduce la excepción real (código HTTP, timeout, sin DNS...) a un motivo corto que
     * cualquier usuario entienda, sin tecnicismos — el texto completo de la excepción (ver
     * describe()) va a Logcat para poder depurarlo, pero nunca se copia tal cual a la
     * pantalla (pedido explícito: un "IllegalStateException: HTTP 403" no le dice nada a
     * quien no programa).
     */
    private fun friendlyReason(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.toList()
        val httpCode = chain.firstNotNullOfOrNull { t ->
            Regex("HTTP (\\d{3})").find(t.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        }

        return when {
            chain.any { it is java.net.UnknownHostException } -> "Sin conexión a internet"
            chain.any { it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException } ->
                "La web ha tardado demasiado en responder"
            httpCode == 403 || httpCode == 429 -> "La web ha bloqueado la petición (inténtalo más tarde)"
            httpCode != null && httpCode >= 500 -> "La web está caída ahora mismo"
            httpCode != null -> "La web no ha respondido correctamente"
            chain.any { it is java.io.IOException } -> "No se pudo conectar con la web"
            else -> "No se pudo leer la información de esta fuente"
        }
    }

    /** Resultado de UNA categoría dentro de una sincronización. */
    data class CategoryOutcome(
        val category: StandingsCategory,
        val ok: Boolean,
        /** Filas guardadas en Room (0 si falló). */
        val rowCount: Int,
        /** Motivo del fallo, listo para mostrar. null si fue bien. */
        val detail: String?
    )

    data class SyncResult(
        /** Una entrada por fuente, en el mismo orden en que están declaradas. */
        val outcomes: List<CategoryOutcome>
    ) {
        val succeeded: List<StandingsCategory> get() = outcomes.filter { it.ok }.map { it.category }
        val failed: List<StandingsCategory> get() = outcomes.filterNot { it.ok }.map { it.category }
    }

    private companion object {
        const val NO_DATA_FOUND_MESSAGE = "No se encontró información en la web de origen"
    }
}
