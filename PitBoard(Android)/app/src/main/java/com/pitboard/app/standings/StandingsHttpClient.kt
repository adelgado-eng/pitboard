package com.pitboard.app.standings

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Compartidos por F1StandingsSource, MotoGpStandingsSource y NascarStandingsSource,
 * para no crear un pool de conexiones ni un Moshi nuevo en cada una.
 */
object StandingsHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // 30/08/2026 (fase 3, robustez de red). connectTimeout y readTimeout son por
            // OPERACIÓN, no por petición: readTimeout de 15 s significa "15 s sin recibir
            // NI UN BYTE", así que una web que va goteando datos muy despacio (o una
            // redirección tras otra) puede tener una sola petición viva indefinidamente sin
            // llegar a disparar ninguno de los dos. callTimeout sí es el tope duro de la
            // llamada completa — DNS + conexión + redirecciones + cuerpo entero — y es lo
            // que garantiza que una fuente atascada acabe fallando ella sola con un
            // InterruptedIOException (que la fase 2 enseña como "timeout") en vez de dejar
            // colgada la sincronización de las otras 6.
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

object StandingsMoshi {
    // Sin KotlinJsonAdapterFactory, Moshi no sabe construir clases de Kotlin como
    // JolpicaResponse en tiempo de ejecución (el proyecto no aplica el procesador de
    // anotaciones moshi-kotlin-codegen) — @JsonClass(generateAdapter = true) por sí solo
    // no genera nada sin ese procesador, así que cada intento de leer la respuesta de la
    // API de F1 lanzaba una excepción, silenciada por el runCatching de syncAll().
    val instance: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}