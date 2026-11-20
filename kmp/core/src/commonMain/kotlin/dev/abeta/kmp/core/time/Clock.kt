package dev.abeta.kmp.core.time

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Abstraction over wall-clock time so time-dependent components remain deterministic and testable.
 */
interface Clock {
    /** Returns the current UTC time in milliseconds since the Unix epoch. */
    fun nowEpochMillis(): Long
}

internal expect fun currentEpochMillis(): Long

/**
 * Returns the current time of this [Clock] as an [Instant].
 */
@OptIn(ExperimentalTime::class)
fun Clock.nowInstant(): Instant = Instant.fromEpochMilliseconds(nowEpochMillis())

/**
 * Returns the current UTC timestamp formatted as an ISO-8601 string.
 */
@OptIn(ExperimentalTime::class)
fun Clock.nowIsoUtc(): String = nowInstant().toString()

/**
 * Production [Clock] implementation backed by the host platform's system clock.
 */
class SystemClock : Clock {
    override fun nowEpochMillis(): Long = currentEpochMillis()
}

/**
 * Thread-safe, controllable [Clock] implementation for deterministic tests, demos, and simulations.
 */
class MutableClock(
    initialEpochMillis: Long = 0L,
) : Clock {
    private val currentMillis = MutableStateFlow(initialEpochMillis)

    override fun nowEpochMillis(): Long = currentMillis.value

    /** Updates the current epoch timestamp to [epochMillis]. */
    fun setEpochMillis(epochMillis: Long) {
        currentMillis.value = epochMillis
    }

    /** Advances the clock forward by [deltaMillis] milliseconds. */
    fun advanceByMillis(deltaMillis: Long) {
        require(deltaMillis >= 0L) { "Cannot advance clock backwards by $deltaMillis ms" }
        currentMillis.update { current -> current + deltaMillis }
    }
}

/**
 * Lightweight elapsed-time measurement helper backed by a [Clock].
 */
class ElapsedTimer(
    private val clock: Clock = SystemClock(),
) {
    private var startMillis: Long = clock.nowEpochMillis()

    /** Resets the timer baseline to the current clock value. */
    fun reset() {
        startMillis = clock.nowEpochMillis()
    }

    /** Returns non-negative elapsed milliseconds since construction or the last [reset]. */
    fun elapsedMillis(): Long = (clock.nowEpochMillis() - startMillis).coerceAtLeast(0L)
}

/**
 * Executes [block] and returns its result paired with the elapsed execution duration in milliseconds.
 */
inline fun <T> Clock.measureTimed(block: () -> T): Pair<T, Long> {
    val start = nowEpochMillis()
    val value = block()
    val elapsed = (nowEpochMillis() - start).coerceAtLeast(0L)
    return value to elapsed
}
