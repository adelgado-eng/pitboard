package com.pitboard.app.standings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ConnectivityHelper {

    /** true si la red activa tiene salida a internet real — se usa antes de intentar
     *  cualquier sincronización y para decidir qué mostrar en StandingsScreen cuando no
     *  hay caché previa.
     *
     *  30/08/2026: antes exigía TRANSPORT_WIFI o TRANSPORT_CELLULAR explícitamente. Los
     *  emuladores de Android Studio reportan su red como TRANSPORT_ETHERNET (comportamiento
     *  documentado de los AVD), así que esto daba isOnline()=false en el emulador pese a
     *  tener internet real — el sync nunca llegaba a ejecutarse (StandingsSyncWorker
     *  devolvía Result.retry() antes de llamar a syncAll()) y por eso no se veía ninguna
     *  foto ni dato nuevo. NET_CAPABILITY_VALIDATED confirma que el sistema ya comprobó
     *  que esa red llega a internet de verdad, sin fijarse en qué transporte concreto usa. */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}