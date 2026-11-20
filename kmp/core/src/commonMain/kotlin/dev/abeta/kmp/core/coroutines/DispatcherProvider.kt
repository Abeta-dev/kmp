package dev.abeta.kmp.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Platform-appropriate dispatcher for blocking or I/O-bound work. */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Abstraction over coroutine dispatchers to allow deterministic scheduler injection in tests.
 */
interface DispatcherProvider {
    /** Dispatcher confined to the UI / main thread. */
    val main: CoroutineDispatcher

    /** Dispatcher optimized for blocking I/O operations. */
    val io: CoroutineDispatcher

    /** Dispatcher optimized for CPU-bound work. */
    val default: CoroutineDispatcher
}

/**
 * Explicit [DispatcherProvider] implementation useful in tests, previews, and custom scopes.
 */
data class CustomDispatcherProvider(
    override val main: CoroutineDispatcher,
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
) : DispatcherProvider {
    companion object {
        /** Returns a provider routing all execution through a single [dispatcher]. */
        fun single(dispatcher: CoroutineDispatcher): DispatcherProvider =
            CustomDispatcherProvider(
                main = dispatcher,
                io = dispatcher,
                default = dispatcher,
            )
    }
}

/**
 * Default [DispatcherProvider] backed by standard `kotlinx.coroutines` dispatchers.
 *
 * Properties resolve lazily so instantiating this provider in headless JVM tests
 * does not fail unless [main] is explicitly accessed without a main dispatcher.
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher
        get() = Dispatchers.Main

    override val io: CoroutineDispatcher
        get() = ioDispatcher

    @Suppress("InjectDispatcher")
    override val default: CoroutineDispatcher
        get() = Dispatchers.Default
}
