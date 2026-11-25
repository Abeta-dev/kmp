package dev.abeta.kmp.state.session

private object WasmNoOpStateLock : StateLock {
    override fun lock() = Unit

    override fun unlock() = Unit
}

internal actual fun createStateLock(): StateLock = WasmNoOpStateLock
