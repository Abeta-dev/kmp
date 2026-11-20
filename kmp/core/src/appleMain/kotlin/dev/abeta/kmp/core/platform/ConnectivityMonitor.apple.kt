@file:OptIn(ExperimentalForeignApi::class)

package dev.abeta.kmp.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

private class AppleNetworkPathMonitor : ConnectivityMonitor {
    private val _status = MutableStateFlow(ConnectivityStatus.UNKNOWN)
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private var monitor: NSObject? = null

    override fun start() {
        if (monitor != null) return
        val created = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(created) { path: nw_path_t ->
            _status.value = mapPathStatus(path)
        }
        val queue = dispatch_queue_create("dev.abeta.kmp.network", null)
        nw_path_monitor_set_queue(created, queue)
        nw_path_monitor_start(created)
        monitor = created
    }

    override fun stop() {
        val current = monitor ?: return
        nw_path_monitor_cancel(current)
        monitor = null
    }

    private fun mapPathStatus(path: nw_path_t): ConnectivityStatus =
        if (path == null) {
            ConnectivityStatus.UNKNOWN
        } else if (nw_path_get_status(path) != nw_path_status_satisfied) {
            ConnectivityStatus.OFFLINE
        } else if (nw_path_is_expensive(path)) {
            ConnectivityStatus.METERED
        } else {
            ConnectivityStatus.ONLINE
        }
}

/** Creates an Apple `Network` framework (`NWPathMonitor`) [ConnectivityMonitor]. */
actual fun createPlatformConnectivityMonitor(): ConnectivityMonitor = AppleNetworkPathMonitor()
