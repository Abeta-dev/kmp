package dev.abeta.kmp.state.session

import java.util.concurrent.locks.ReentrantLock

private class AndroidStateLock : StateLock {
    private val delegate = ReentrantLock()

    override fun lock() {
        delegate.lock()
    }

    override fun unlock() {
        delegate.unlock()
    }
}

internal actual fun createStateLock(): StateLock = AndroidStateLock()
