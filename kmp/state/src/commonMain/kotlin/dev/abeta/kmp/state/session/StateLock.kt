package dev.abeta.kmp.state.session

internal interface StateLock {
    fun lock()

    fun unlock()
}

internal inline fun <T> StateLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

internal expect fun createStateLock(): StateLock
