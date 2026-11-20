package dev.abeta.kmp.core.resilience

import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff policy shared across network retries, offline mutation flushing, and telemetry batches.
 */
data class BackoffPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val multiplier: Double = DEFAULT_MULTIPLIER,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must be non-negative" }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
    }

    /**
     * Computes delay in milliseconds for zero-based [attempt], clamped by [maxDelayMillis].
     *
     * An optional [jitterFactor] in `[0.0, 1.0]` scales delay deterministically for reproducible tests.
     */
    fun delayMillisForAttempt(
        attempt: Int,
        jitterFactor: Double = 0.0,
    ): Long {
        if (attempt < 0 || initialDelayMillis == 0L) return 0L
        val rawDelay = initialDelayMillis.toDouble() * multiplier.pow(attempt.toDouble())
        val capped = min(rawDelay, maxDelayMillis.toDouble())
        val boundedJitter = jitterFactor.coerceIn(0.0, 1.0)
        return (capped * (1.0 - boundedJitter * DEFAULT_JITTER_SPAN)).toLong().coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 3
        const val DEFAULT_INITIAL_DELAY_MILLIS: Long = 250L
        const val DEFAULT_MAX_DELAY_MILLIS: Long = 30_000L
        const val DEFAULT_MULTIPLIER: Double = 2.0
        private const val DEFAULT_JITTER_SPAN: Double = 0.2
    }
}
