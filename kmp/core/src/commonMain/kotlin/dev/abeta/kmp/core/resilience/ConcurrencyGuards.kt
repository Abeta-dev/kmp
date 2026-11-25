package dev.abeta.kmp.core.resilience

import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Deduplicates concurrent in-flight suspend operations sharing the same [key], ensuring only
 * a single execution runs at a time per key while all callers await its result.
 */
class SingleFlight<K, V> {
    private sealed interface FlightOutcome<out V> {
        data class Succeeded<V>(
            val value: V,
        ) : FlightOutcome<V>

        data class Failed(
            val throwable: Throwable,
        ) : FlightOutcome<Nothing>

        data object LeaderCancelled : FlightOutcome<Nothing>
    }

    private val mutex = Mutex()
    private val active = mutableMapOf<K, CompletableDeferred<FlightOutcome<V>>>()

    /**
     * Joins any active operation for [key] or starts [block] as the leader and completes all waiters.
     * If the leader is cancelled while live waiters exist, the key is cleaned up non-cancellably and
     * waiters cleanly retry rather than being cancelled or poison-locked.
     */
    suspend fun execute(
        key: K,
        block: suspend () -> V,
    ): V {
        while (true) {
            val (owned, isLeader) = acquireOrJoin(key)
            if (!isLeader) {
                when (val outcome = owned.await()) {
                    is FlightOutcome.Succeeded -> return outcome.value
                    is FlightOutcome.Failed -> throw outcome.throwable
                    FlightOutcome.LeaderCancelled -> continue
                }
            }
            return runAsLeader(key = key, owned = owned, block = block)
        }
    }

    private suspend fun acquireOrJoin(key: K): Pair<CompletableDeferred<FlightOutcome<V>>, Boolean> =
        mutex.withLock {
            val current = active[key]
            if (current != null) {
                current to false
            } else {
                val created = CompletableDeferred<FlightOutcome<V>>()
                active[key] = created
                created to true
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runAsLeader(
        key: K,
        owned: CompletableDeferred<FlightOutcome<V>>,
        block: suspend () -> V,
    ): V {
        var outcome: FlightOutcome<V> = FlightOutcome.LeaderCancelled
        return try {
            val result = block()
            outcome = FlightOutcome.Succeeded(result)
            result
        } catch (cancelled: CancellationException) {
            outcome = FlightOutcome.LeaderCancelled
            throw cancelled
        } catch (throwable: Throwable) {
            outcome = FlightOutcome.Failed(throwable)
            throw throwable
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (active[key] === owned) {
                        active.remove(key)
                    }
                }
                owned.complete(outcome)
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

    /** Returns `true` and atomically updates the timestamp if [cooldownMillis] has elapsed since the previous event. */
    fun tryAcquire(): Boolean {
        while (true) {
            val now = clock.nowEpochMillis()
            val previous = lastAcceptedAt.value
            if (previous != null && (now - previous) < cooldownMillis) {
                return false
            }
            if (lastAcceptedAt.compareAndSet(previous, now)) {
                return true
            }
        }
    }

    /** Resets the guard so the next event is accepted immediately. */
    fun reset() {
        lastAcceptedAt.value = null
    }

    companion object {
        const val DEFAULT_COOLDOWN_MILLIS: Long = 400L
    }
}
