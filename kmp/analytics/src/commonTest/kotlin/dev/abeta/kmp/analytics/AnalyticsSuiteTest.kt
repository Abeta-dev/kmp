package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsSuiteTest {
    @Test
    fun `sanitizing deduplicating and enriching trackers compose cleanly`() {
        val recorder = RecordingAnalyticsTracker()
        val clock = MutableClock(initialEpochMillis = 10_000L)
        val ids = SequentialUuidGenerator()

        val pipeline =
            EnrichingAnalyticsTracker(
                delegate =
                    SanitizingAnalyticsTracker(
                        delegate =
                            DeduplicatingAnalyticsTracker(
                                delegate = recorder,
                                clock = clock,
                                cooldownMillis = 5_000L,
                            ),
                    ),
                clock = clock,
                uuidGenerator = ids,
                initialIdentity = AnalyticsIdentity(anonymousId = "anon-1", sessionId = "sess-1"),
                initialGlobalProperties = mapOf("app_version" to "2.0.0"),
            )

        pipeline.trackScreen("home")
        pipeline.identify(userId = "user-9", traits = mapOf("plan" to "pro", "api_key" to "secret-xyz"))
        pipeline.track(
            AnalyticsEvent(
                name = "submit_form",
                properties = mapOf("email" to "ada@abeta.dev", "token" to "Bearer topsecret"),
            ),
        )

        // Impression deduplication drops duplicates within cooldownMillis and allows after expiry
        val banner = AnalyticsTrackingPayload(eventName = "hero_banner_shown", properties = mapOf("slot" to "top"))
        pipeline.trackImpression(banner)
        clock.advanceByMillis(2_000L)
        pipeline.trackImpression(banner)
        clock.advanceByMillis(3_500L)
        pipeline.trackImpression(banner)

        assertEquals(3, recorder.events.size)
        assertEquals(2, recorder.impressions.size)

        val submitEvent = recorder.events.last()
        assertEquals("home", submitEvent.screenName)
        assertEquals("user-9", submitEvent.userId)
        assertEquals("sess-1", submitEvent.sessionId)
        assertEquals("2.0.0", submitEvent.properties["app_version"])
        assertFalse(submitEvent.properties["token"].orEmpty().contains("topsecret"))

        val identifyTraits = recorder.identifyCalls.single().traits
        assertFalse(identifyTraits["api_key"].orEmpty().contains("secret-xyz"))

        pipeline.reset()
        assertEquals(1, recorder.resetCount)
    }

    @Test
    fun `buffered analytics tracker persists FIFO queue and flushes in batches`() =
        runTest {
            val kv = InMemoryKeyValueStore()
            val clock = MutableClock(1_000L)
            val sentBatches = mutableListOf<List<AnalyticsEvent>>()
            var shouldFail = true

            val tracker =
                BufferedAnalyticsTracker(
                    sink =
                        AnalyticsBatchSink { events ->
                            if (shouldFail) {
                                AppResult.Failure(NetworkError(message = "offline"))
                            } else {
                                sentBatches.add(events)
                                AppResult.Success(Unit)
                            }
                        },
                    backingStore = kv,
                    clock = clock,
                    batchSize = 2,
                    maxQueueSize = 10,
                )

            tracker.track(AnalyticsEvent(name = "ev1"))
            tracker.track(AnalyticsEvent(name = "ev2"))
            tracker.trackImpression(AnalyticsTrackingPayload("imp1"))
            assertEquals(3, tracker.queuedEvents.value.size)

            // Failed flush retains persisted queue
            assertTrue(tracker.flush() is AppResult.Failure)

            // Reinstantiate from persisted storage and flush successfully across 2 chunks (2 + 1)
            shouldFail = false
            val rehydrated =
                BufferedAnalyticsTracker(
                    sink =
                        AnalyticsBatchSink { events ->
                            sentBatches.add(events)
                            AppResult.Success(Unit)
                        },
                    backingStore = kv,
                    clock = clock,
                    batchSize = 2,
                )
            val flushResult = rehydrated.flush()
            assertEquals(AppResult.Success(3), flushResult)
            assertEquals(listOf(2, 1), sentBatches.map { it.size })
            assertTrue(rehydrated.queuedEvents.value.isEmpty())
        }
}
