package dev.abeta.kmp.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

private class AndroidConnectivityMonitor : ConnectivityMonitor {
    private val registered = AtomicBoolean(false)
    private var registeredManager: ConnectivityManager? = null
    private val _status = MutableStateFlow(readCurrentStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _status.value = readCurrentStatus()
            }

            override fun onLost(network: Network) {
                _status.value = readCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                _status.value = mapCapabilities(networkCapabilities)
            }
        }

    override fun start() {
        _status.value = readCurrentStatus()
        val manager = resolveManager() ?: return
        if (!registered.compareAndSet(false, true)) return
        val succeeded =
            runCatching {
                manager.registerDefaultNetworkCallback(callback)
                registeredManager = manager
            }.isSuccess
        if (!succeeded) {
            registered.set(false)
        }
    }

    override fun stop() {
        if (!registered.compareAndSet(true, false)) return
        val manager = registeredManager
        registeredManager = null
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    override fun refresh() {
        _status.value = readCurrentStatus()
    }

    private fun resolveManager(): ConnectivityManager? =
        runCatching {
            AbetaAndroidContext
                .applicationContextOrNull()
                ?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()

    private fun readCurrentStatus(): ConnectivityStatus {
        val manager = resolveManager() ?: return ConnectivityStatus.UNKNOWN
        return runCatching {
            val activeNetwork = manager.activeNetwork ?: return@runCatching ConnectivityStatus.OFFLINE
            val caps = manager.getNetworkCapabilities(activeNetwork) ?: return@runCatching ConnectivityStatus.OFFLINE
            mapCapabilities(caps)
        }.getOrDefault(ConnectivityStatus.UNKNOWN)
    }

    private fun mapCapabilities(capabilities: NetworkCapabilities): ConnectivityStatus =
        when {
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ConnectivityStatus.OFFLINE
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> ConnectivityStatus.METERED
            else -> ConnectivityStatus.ONLINE
        }
}

/** Creates an Android [ConnectivityMonitor] observing active network capabilities via [ConnectivityManager]. */
actual fun createPlatformConnectivityMonitor(): ConnectivityMonitor = AndroidConnectivityMonitor()
