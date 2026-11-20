package dev.abeta.kmp.core.resilience

import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deduplicates concurrent in-flight suspend operations sharing the same [key], ensuring only
 * a single execution runs at a time per key while all callers await its result.
 */
class SingleFlight<K, V> {
    private val mutex = Mutex()
    private val active = mutableMapOf<K, CompletableDeferred<V>>()

    /**
     * Joins any active operation for [key] or starts [block] as the leader and completes all waiters.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(
        key: K,
        block: suspend () -> V,
    ): V {
        val joinTarget: CompletableDeferred<V>?
        val owned: CompletableDeferred<V>
        mutex.withLock {
            val current = active[key]
            if (current != null) {
                joinTarget = current
                owned = current
            } else {
                val created = CompletableDeferred<V>()
                active[key] = created
                joinTarget = null
                owned = created
            }
        }

        if (joinTarget != null) {
            return joinTarget.await()
        }

        return try {
            val result = block()
            owned.complete(result)
            result
        } catch (throwable: Throwable) {
            owned.completeExceptionally(throwable)
            throw throwable
        } finally {
            mutex.withLock {
                if (active[key] === owned) {
                    active.remove(key)
                }
            }
        }
    }
}

/**
 * Coroutine-backed debouncer that delays [block] until [waitMillis] elapses without newer calls.
 */
class Debouncer(
    private val scope: CoroutineScope,
    private val waitMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val mutex = Mutex()
    private var pendingJob: Job? = null

    /** Cancels any previous unexecuted submission and schedules [block] after [waitMillis]. */
    suspend fun submit(block: suspend () -> Unit) {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob =
                scope.launch {
                    if (waitMillis > 0L) {
                        delay(waitMillis)
                    }
                    block()
                }
        }
    }

    /** Cancels any pending scheduled execution. */
    suspend fun cancel() {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = null
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 300L
    }
}

/**
 * Cooldown guard preventing accidental duplicate UI taps or duplicate action triggers within [cooldownMillis].
 */
class ClickBackpressureGuard(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: Clock = SystemClock(),
) {
    private val lastAcceptedAt = MutableStateFlow<Long?>(null)

    /** Returns `true` and updates the timestamp if [cooldownMillis] has elapsed since the previous accepted event. */
    fun tryAcquire(): Boolean {
        val now = clock.nowEpochMillis()
        val previous = lastAcceptedAt.value
        val allowed = previous == null || (now - previous) >= cooldownMillis
        if (allowed) {
            lastAcceptedAt.value = now
        }
        return allowed
    }

    /** Resets the guard so the next event is accepted immediately. */
    fun reset() {
        lastAcceptedAt.value = null
    }

    companion object {
        const val DEFAULT_COOLDOWN_MILLIS: Long = 400L
    }
}
