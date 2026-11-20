package dev.abeta.kmp.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface
import java.net.SocketException

private class JvmNetworkInterfaceMonitor : ConnectivityMonitor {
    private val _status = MutableStateFlow(probeInterfaces())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    override fun start() {
        refresh()
    }

    override fun refresh() {
        _status.value = probeInterfaces()
    }

    private fun probeInterfaces(): ConnectivityStatus =
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val hasActive =
                interfaces?.asSequence().orEmpty().any { iface ->
                    iface.isUp && !iface.isLoopback
                }
            if (hasActive) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE
        } catch (_: SocketException) {
            ConnectivityStatus.UNKNOWN
        }
}

/** Creates a JVM [ConnectivityMonitor] inspecting active host [NetworkInterface]s. */
actual fun createPlatformConnectivityMonitor(): ConnectivityMonitor = JvmNetworkInterfaceMonitor()
