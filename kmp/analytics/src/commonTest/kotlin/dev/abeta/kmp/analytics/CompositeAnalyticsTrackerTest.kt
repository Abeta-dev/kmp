package dev.abeta.kmp.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeAnalyticsTrackerTest {
    @Test
    fun `forwards events and impressions to all child trackers`() {
        val first = RecordingAnalyticsTracker()
        val second = RecordingAnalyticsTracker()
        val composite: AnalyticsTracker = CompositeAnalyticsTracker(first, second)

        val event =
            AnalyticsEvent(
                name = "checkout_started",
                properties = mapOf("cart_size" to "3"),
                timestampEpochMillis = 1000L,
            )
        val impression =
            AnalyticsTrackingPayload(
                eventName = "promo_banner_viewed",
                properties = mapOf("slot" to "top"),
            )

        composite.track(event)
        composite.trackImpression(impression)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
        assertEquals(listOf(impression), first.impressions)
        assertEquals(listOf(impression), second.impressions)
    }
}
