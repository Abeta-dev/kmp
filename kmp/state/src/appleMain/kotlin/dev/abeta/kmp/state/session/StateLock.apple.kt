package dev.abeta.kmp.state.session

import platform.Foundation.NSRecursiveLock

private class AppleStateLock : StateLock {
    private val delegate = NSRecursiveLock()

    override fun lock() {
        delegate.lock()
    }

    override fun unlock() {
        delegate.unlock()
    }
}

internal actual fun createStateLock(): StateLock = AppleStateLock()
