package dev.abeta.kmp.analytics

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingAnalyticsTrackerTest {
    @Test
    fun `records events and impressions in order and clears cleanly`() {
        val tracker = RecordingAnalyticsTracker()
        val event1 = AnalyticsEvent(name = "screen_view", properties = mapOf("screen" to "home"))
        val event2 = AnalyticsEvent(name = "button_click")
        val impression = AnalyticsTrackingPayload(eventName = "card_impression", properties = mapOf("id" to "c1"))

        tracker.track(event1)
        tracker.track(event2)
        tracker.trackImpression(impression)

        assertEquals(listOf(event1, event2), tracker.events)
        assertEquals(listOf(impression), tracker.impressions)

        tracker.clear()
        assertTrue(tracker.events.isEmpty())
        assertTrue(tracker.impressions.isEmpty())
    }

    @Test
    fun `analytics tracking payload serializes eventName as event_name`() {
        val payload =
            AnalyticsTrackingPayload(
                eventName = "hero_impression",
                properties = mapOf("position" to "0"),
            )
        val encoded = Json.encodeToString(payload)
        assertTrue(encoded.contains("\"event_name\":\"hero_impression\""))

        val decoded = Json.decodeFromString<AnalyticsTrackingPayload>(encoded)
        assertEquals(payload, decoded)
    }
}
