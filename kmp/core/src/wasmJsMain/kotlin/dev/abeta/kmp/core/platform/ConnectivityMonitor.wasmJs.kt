@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean') ? navigator.onLine : true")
private external fun jsIsNavigatorOnline(): Boolean

@JsFun(
    "(onOnline, onOffline) => { try { if (typeof window !== 'undefined' && window.addEventListener) { " +
        "window.addEventListener('online', onOnline); window.addEventListener('offline', onOffline); " +
        "return () => { try { window.removeEventListener('online', onOnline); " +
        "window.removeEventListener('offline', onOffline); } catch (e) {} }; } } catch (e) {} return null; }",
)
private external fun jsRegisterOnlineListeners(
    onOnline: () -> Unit,
    onOffline: () -> Unit,
): JsAny?

@JsFun("(unregister) => { try { if (unregister) { unregister(); } } catch (e) {} }")
private external fun jsUnregisterOnlineListeners(unregister: JsAny)

private class WasmConnectivityMonitor : ConnectivityMonitor {
    private val _status = MutableStateFlow(currentBrowserStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()
    private var unregisterHandle: JsAny? = null

    override fun start() {
        refresh()
        if (unregisterHandle != null) return
        unregisterHandle =
            jsRegisterOnlineListeners(
                onOnline = { _status.value = ConnectivityStatus.ONLINE },
                onOffline = { _status.value = ConnectivityStatus.OFFLINE },
            )
    }

    override fun stop() {
        val handle = unregisterHandle ?: return
        unregisterHandle = null
        jsUnregisterOnlineListeners(handle)
    }

    override fun refresh() {
        _status.value = currentBrowserStatus()
    }

    private fun currentBrowserStatus(): ConnectivityStatus =
        if (jsIsNavigatorOnline()) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE
}

/** Creates a browser `navigator.onLine` [ConnectivityMonitor] for WasmJS targets. */
actual fun createPlatformConnectivityMonitor(): ConnectivityMonitor = WasmConnectivityMonitor()
