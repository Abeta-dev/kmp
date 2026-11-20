package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.LogLevel
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock

/**
 * [AnalyticsTracker] that emits structured log entries using [AppLogger] and timestamps via [Clock].
 */
class LoggerAnalyticsTracker(
    private val logger: AppLogger,
    private val clock: Clock = SystemClock(),
    private val level: LogLevel = LogLevel.DEBUG,
    private val tag: String = DEFAULT_TAG,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        val timestamp = event.timestampEpochMillis ?: clock.nowEpochMillis()
        logger.log(
            level = level,
            tag = tag,
            message =
                "event=${event.name} category=${event.category} timestamp=$timestamp " +
                    "screen=${event.screenName.orEmpty()} user=${event.userId.orEmpty()} " +
                    "properties=${event.properties}",
        )
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        val timestamp = clock.nowEpochMillis()
        logger.log(
            level = level,
            tag = tag,
            message = "impression=${payload.eventName} timestamp=$timestamp properties=${payload.properties}",
        )
    }

    companion object {
        const val DEFAULT_TAG: String = "Analytics"
    }
}
