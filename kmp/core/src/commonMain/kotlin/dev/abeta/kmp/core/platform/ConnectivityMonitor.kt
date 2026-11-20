package dev.abeta.kmp.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level network reachability classification observed from OS connectivity APIs.
 */
enum class ConnectivityStatus {
    ONLINE,
    OFFLINE,
    METERED,
    UNKNOWN,
}

/**
 * Reactive OS network reachability monitor port.
 */
interface ConnectivityMonitor {
    /** Live stream of [ConnectivityStatus] updates. */
    val status: StateFlow<ConnectivityStatus>

    /** Begins observing OS reachability transitions. */
    fun start() {}

    /** Releases OS reachability observers. */
    fun stop() {}

    /** Performs an on-demand connectivity refresh when supported by the platform. */
    fun refresh() {}

    /** Returns `true` unless the platform explicitly reports [ConnectivityStatus.OFFLINE]. */
    fun isOnline(): Boolean = status.value != ConnectivityStatus.OFFLINE
}

/**
 * Controllable [ConnectivityMonitor] implementation for deterministic tests and offline simulations.
 */
class MutableConnectivityMonitor(
    initialStatus: ConnectivityStatus = ConnectivityStatus.ONLINE,
) : ConnectivityMonitor {
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    /** Updates the simulated [ConnectivityStatus]. */
    fun setStatus(nextStatus: ConnectivityStatus) {
        _status.value = nextStatus
    }
}

/** Creates the platform-native [ConnectivityMonitor] for the current KMP target. */
expect fun createPlatformConnectivityMonitor(): ConnectivityMonitor
