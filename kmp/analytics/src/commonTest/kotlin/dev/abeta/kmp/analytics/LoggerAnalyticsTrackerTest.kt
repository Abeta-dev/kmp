package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.logging.LogLevel
import dev.abeta.kmp.core.logging.RecordingAppLogger
import dev.abeta.kmp.core.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggerAnalyticsTrackerTest {
    private class FixedClock(
        private val epochMillis: Long,
    ) : Clock {
        override fun nowEpochMillis(): Long = epochMillis
    }

    @Test
    fun `logs events using explicit timestamp or clock fallback and logs impressions`() {
        val logger = RecordingAppLogger()
        val clock = FixedClock(epochMillis = 555_000L)
        val tracker =
            LoggerAnalyticsTracker(
                logger = logger,
                clock = clock,
                level = LogLevel.INFO,
                tag = "TestAnalytics",
            )

        tracker.track(
            AnalyticsEvent(
                name = "purchase",
                properties = mapOf("currency" to "USD"),
                timestampEpochMillis = 123_456L,
            ),
        )
        tracker.track(
            AnalyticsEvent(
                name = "logout",
            ),
        )
        tracker.trackImpression(
            AnalyticsTrackingPayload(
                eventName = "feed_item_seen",
                properties = mapOf("index" to "2"),
            ),
        )

        assertEquals(3, logger.entries.size)

        val first = logger.entries[0]
        assertEquals(LogLevel.INFO, first.level)
        assertEquals("TestAnalytics", first.tag)
        assertTrue(first.message.contains("event=purchase"))
        assertTrue(first.message.contains("timestamp=123456"))
        assertTrue(first.message.contains("currency=USD"))

        val second = logger.entries[1]
        assertTrue(second.message.contains("event=logout"))
        assertTrue(second.message.contains("timestamp=555000"))

        val third = logger.entries[2]
        assertTrue(third.message.contains("impression=feed_item_seen"))
        assertTrue(third.message.contains("timestamp=555000"))
        assertTrue(third.message.contains("index=2"))
    }
}
