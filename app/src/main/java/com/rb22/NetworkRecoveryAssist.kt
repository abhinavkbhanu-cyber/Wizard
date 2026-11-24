package com.rb22

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Safe reconnect / roaming assist. Observes default-network changes and reports
 * them to the foreground UI. It never toggles Wi-Fi or disconnects the user.
 */
class NetworkRecoveryAssist(
    context: Context,
    private val onNetworkEvent: (String, Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registered = false
    private var lastNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val changed = lastNetwork != null && lastNetwork != network
            lastNetwork = network
            onNetworkEvent(if (changed) "Network changed • roaming/reconnect detected" else "Network available", changed)
        }

        override fun onLost(network: Network) {
            if (lastNetwork == network) lastNetwork = null
            onNetworkEvent("Network temporarily unavailable • waiting for Android to reconnect", false)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            onNetworkEvent(
                when {
                    wifi && validated -> "Wi-Fi connected • internet validated"
                    wifi -> "Wi-Fi connected • waiting for internet validation"
                    validated -> "Network connected • internet validated"
                    else -> "Network connected • validation pending"
                },
                false
            )
        }
    }

    fun start() {
        if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            registered = true
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        registered = false
        lastNetwork = null
    }
}
