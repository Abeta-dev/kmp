package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.logging.RecordingAppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

    @Test
    fun `buffered tracker preserves concurrent events during flush and drops 4xx batches`() =
        runTest {
            val kv = InMemoryKeyValueStore()
            val clock = MutableClock(1_000L)
            val firstBatchInFlight = CompletableDeferred<Unit>()
            val releaseFirstBatch = CompletableDeferred<Unit>()
            val delivered = mutableListOf<String>()
            var batchIndex = 0

            val tracker =
                BufferedAnalyticsTracker(
                    sink =
                        AnalyticsBatchSink { events ->
                            batchIndex++
                            when (batchIndex) {
                                1 -> {
                                    firstBatchInFlight.complete(Unit)
                                    releaseFirstBatch.await()
                                    delivered.addAll(events.map { it.name })
                                    AppResult.Success(Unit)
                                }

                                2 -> {
                                    AppResult.Failure(ValidationError(field = "batch", message = "bad payload"))
                                }

                                else -> {
                                    delivered.addAll(events.map { it.name })
                                    AppResult.Success(Unit)
                                }
                            }
                        },
                    backingStore = kv,
                    clock = clock,
                    batchSize = 1,
                )

            tracker.track(AnalyticsEvent(name = "first"))
            tracker.track(AnalyticsEvent(name = "bad_second"))

            val flushDeferred = async(start = CoroutineStart.UNDISPATCHED) { tracker.flush() }
            firstBatchInFlight.await()

            tracker.track(AnalyticsEvent(name = "third_concurrent"))
            releaseFirstBatch.complete(Unit)

            val result = flushDeferred.await()
            assertEquals(AppResult.Success(2), result)
            assertEquals(listOf("first", "third_concurrent"), delivered)
            assertTrue(tracker.queuedEvents.value.isEmpty())
        }

    @Test
    fun `buffered tracker periodic flush survives unexpected sink errors`() =
        runTest {
            val clock = MutableClock(1_000L)
            var periodicAttempts = 0
            val resilientTracker =
                BufferedAnalyticsTracker(
                    sink =
                        AnalyticsBatchSink {
                            periodicAttempts++
                            if (periodicAttempts == 1) {
                                error("unexpected boom")
                            }
                            AppResult.Success(Unit)
                        },
                    backingStore = InMemoryKeyValueStore(),
                    clock = clock,
                    scope = backgroundScope,
                    batchSize = 10,
                    flushIntervalMillis = 50L,
                )
            resilientTracker.track(AnalyticsEvent(name = "periodic_ev"))
            resilientTracker.startPeriodicFlush()
            delay(130L)
            resilientTracker.stopPeriodicFlush()
            assertTrue(periodicAttempts >= 2)
            assertTrue(resilientTracker.queuedEvents.value.isEmpty())
        }

    @Test
    fun `dedup tracker caps entries and enriching tracker avoids duplicate user_identify`() {
        val clock = MutableClock(10_000L)
        val recorder = RecordingAnalyticsTracker()
        val dedup =
            DeduplicatingAnalyticsTracker(
                delegate = recorder,
                clock = clock,
                cooldownMillis = 1_000L,
                maxTrackedEntries = 2,
            )

        dedup.trackImpression(AnalyticsTrackingPayload("a"))
        dedup.trackImpression(AnalyticsTrackingPayload("b"))
        dedup.trackImpression(AnalyticsTrackingPayload("c")) // evicts "a" due to cap = 2
        dedup.trackImpression(AnalyticsTrackingPayload("a")) // accepted again because "a" was evicted
        assertEquals(4, recorder.impressions.size)

        val buffered =
            BufferedAnalyticsTracker(
                sink = AnalyticsBatchSink { AppResult.Success(Unit) },
                backingStore = InMemoryKeyValueStore(),
                clock = clock,
            )
        val logger = RecordingAppLogger()
        val loggerTracker = LoggerAnalyticsTracker(logger = logger, clock = clock)
        val compositeEnriched =
            EnrichingAnalyticsTracker(
                delegate = SanitizingAnalyticsTracker(CompositeAnalyticsTracker(buffered, loggerTracker)),
                clock = clock,
                uuidGenerator = SequentialUuidGenerator(),
            )

        compositeEnriched.identify(userId = "u-55", traits = mapOf("role" to "buyer"))
        assertEquals(1, buffered.queuedEvents.value.count { it.name == "user_identify" })
        assertEquals(1, logger.entries.count { it.message.contains("event=user_identify") })
    }
}
