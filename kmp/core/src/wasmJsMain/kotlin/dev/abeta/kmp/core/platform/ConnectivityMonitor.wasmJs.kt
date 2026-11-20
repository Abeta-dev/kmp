@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean') ? navigator.onLine : true")
private external fun jsIsNavigatorOnline(): Boolean

@JsFun(
    "(onOnline, onOffline) => { try { if (typeof window !== 'undefined' && window.addEventListener) { " +
        "window.addEventListener('online', onOnline); window.addEventListener('offline', onOffline); } } " +
        "catch (e) {} }",
)
private external fun jsRegisterOnlineListeners(
    onOnline: () -> Unit,
    onOffline: () -> Unit,
)

private class WasmConnectivityMonitor : ConnectivityMonitor {
    private val _status = MutableStateFlow(currentBrowserStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()
    private var started = false

    override fun start() {
        refresh()
        if (started) return
        started = true
        jsRegisterOnlineListeners(
            onOnline = { _status.value = ConnectivityStatus.ONLINE },
            onOffline = { _status.value = ConnectivityStatus.OFFLINE },
        )
    }

    override fun refresh() {
        _status.value = currentBrowserStatus()
    }

    private fun currentBrowserStatus(): ConnectivityStatus =
        if (jsIsNavigatorOnline()) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE
}

/** Creates a browser `navigator.onLine` [ConnectivityMonitor] for WasmJS targets. */
actual fun createPlatformConnectivityMonitor(): ConnectivityMonitor = WasmConnectivityMonitor()
