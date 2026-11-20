package dev.abeta.kmp.core

import app.cash.turbine.test
import dev.abeta.kmp.core.error.ConflictError
import dev.abeta.kmp.core.error.ErrorCategory
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.error.category
import dev.abeta.kmp.core.error.isRetryable
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.logging.CompositeAppLogger
import dev.abeta.kmp.core.logging.LogLevel
import dev.abeta.kmp.core.logging.RecordingAppLogger
import dev.abeta.kmp.core.logging.RedactingAppLogger
import dev.abeta.kmp.core.platform.currentPlatformInfo
import dev.abeta.kmp.core.resilience.BackoffPolicy
import dev.abeta.kmp.core.resilience.ClickBackpressureGuard
import dev.abeta.kmp.core.resilience.Debouncer
import dev.abeta.kmp.core.resilience.SingleFlight
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.combineResults
import dev.abeta.kmp.core.result.getOrDefault
import dev.abeta.kmp.core.result.getOrElse
import dev.abeta.kmp.core.result.mapError
import dev.abeta.kmp.core.result.recover
import dev.abeta.kmp.core.result.retryResult
import dev.abeta.kmp.core.result.toAppResult
import dev.abeta.kmp.core.result.toKotlinResult
import dev.abeta.kmp.core.result.zip
import dev.abeta.kmp.core.security.MaskingOptions
import dev.abeta.kmp.core.security.SensitiveDataRedactor
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.storage.NamespacedKeyValueStore
import dev.abeta.kmp.core.time.ElapsedTimer
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.core.time.nowIsoUtc
import dev.abeta.kmp.core.validation.FieldValidators
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorePrimitivesTest {
    @Test
    fun `error classification marks transient vs terminal errors accurately`() {
        assertTrue(TimeoutError(timeoutMillis = 5000L, message = "timed out").isRetryable)
        assertTrue(RateLimitError(retryAfterMillis = 1000L, message = "too fast").isRetryable)
        assertTrue(OfflineError().isRetryable)
        assertTrue(NetworkError(statusCode = 503, message = "unavailable").isRetryable)
        assertFalse(NetworkError(statusCode = 400, message = "bad request").isRetryable)
        assertFalse(ForbiddenError(message = "denied").isRetryable)
        assertFalse(ConflictError(message = "duplicate").isRetryable)

        assertEquals(ErrorCategory.TIMEOUT, TimeoutError(message = "t").category)
        assertEquals(ErrorCategory.RATE_LIMIT, RateLimitError(message = "r").category)
        assertEquals(ErrorCategory.AUTH, ForbiddenError(message = "f").category)
    }

    @Test
    fun `result combinators zip recover mapError and retry transient failures`() =
        runTest {
            val a: AppResult<Int> = AppResult.Success(10)
            val b: AppResult<Int> = AppResult.Success(20)
            val err: AppResult<Int> = AppResult.Failure(ValidationError(field = "age", message = "invalid"))

            assertEquals(AppResult.Success(30), a.zip(b) { x, y -> x + y })
            assertEquals(err, a.zip(err) { x, y -> x + y })
            assertEquals(AppResult.Success(99), err.recover { 99 })
            assertEquals(99, err.getOrDefault(99))
            assertEquals("invalid", err.getOrElse { it.message })
            assertEquals(
                "NEW_CODE",
                err.mapError { ValidationError(field = "age", message = it.message, code = "NEW_CODE") }.errorOrCode(),
            )
            assertEquals(AppResult.Success(listOf(10, 20)), combineResults(listOf(a, b)))
            assertTrue(Result.success("ok").toAppResult() is AppResult.Success)
            assertTrue(err.toKotlinResult().isFailure)

            var attempts = 0
            val recordedDelays = mutableListOf<Long>()
            val retried =
                retryResult(
                    policy = BackoffPolicy(maxRetries = 2, initialDelayMillis = 100L, maxDelayMillis = 500L),
                    delayBlock = { recordedDelays += it },
                ) {
                    attempts++
                    if (attempts < 3) {
                        AppResult.Failure(TimeoutError(message = "retry $attempts"))
                    } else {
                        AppResult.Success("done")
                    }
                }

            assertEquals(AppResult.Success("done"), retried)
            assertEquals(3, attempts)
            assertEquals(listOf(100L, 200L), recordedDelays)
        }

    private fun <T> AppResult<T>.errorOrCode(): String? = (this as? AppResult.Failure)?.error?.code

    @Test
    fun `clock timer uuid and platform info behave deterministically`() {
        val clock = MutableClock(initialEpochMillis = 1_700_000_000_000L)
        val timer = ElapsedTimer(clock)
        assertEquals("2023-11-14T22:13:20Z", clock.nowIsoUtc())
        clock.advanceByMillis(250L)
        assertEquals(250L, timer.elapsedMillis())
        timer.reset()
        assertEquals(0L, timer.elapsedMillis())

        val seqIds = SequentialUuidGenerator()
        assertEquals("00000000-0000-4000-8000-000000000001", seqIds.nextId())
        assertEquals("00000000-0000-4000-8000-000000000002", seqIds.nextId())

        val randomIds = RandomUuidGenerator()
        assertNotEquals(randomIds.nextId(), randomIds.nextId())
        assertTrue(currentPlatformInfo().name.isNotBlank())
    }

    @Test
    fun `redacting logger and composite logger protect secrets`() {
        val recA = RecordingAppLogger()
        val recB = RecordingAppLogger(minLevel = LogLevel.WARN)
        val logger = RedactingAppLogger(CompositeAppLogger(recA, recB))

        logger.info("Auth", "Header Bearer secret.jwt.token password=hunter2")
        logger.warn("Auth", "Retry token=abc123")

        assertEquals(2, recA.entries.size)
        assertEquals(1, recB.entries.size)
        assertFalse(recA.entries[0].message.contains("secret.jwt.token"))
        assertFalse(recA.entries[0].message.contains("hunter2"))
        assertFalse(recA.entries[1].message.contains("abc123"))

        assertEquals(
            "••••••7890",
            SensitiveDataRedactor.maskValue("1234567890", MaskingOptions(visibleSuffixLength = 4)),
        )
    }

    @Test
    fun `namespaced observable store isolates keys and emits changes`() =
        runTest {
            val root = InMemoryKeyValueStore()
            val featureStore = NamespacedKeyValueStore(root, "flags")
            val otherStore = NamespacedKeyValueStore(root, "session")

            featureStore.observeKey("dark_mode").test {
                assertNull(awaitItem())
                featureStore.putString("dark_mode", "true")
                assertEquals("true", awaitItem())
                otherStore.putString("dark_mode", "false")
                assertEquals("true", featureStore.getString("dark_mode"))
                assertEquals("false", otherStore.getString("dark_mode"))
                featureStore.clear()
                assertNull(awaitItem())
                assertEquals("false", otherStore.getString("dark_mode"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `single flight debouncer backpressure and validators enforce invariants`() =
        runTest {
            val singleFlight = SingleFlight<String, Int>()
            var invocations = 0
            val results =
                listOf(
                    async {
                        singleFlight.execute("k1") {
                            invocations++
                            delay(20L)
                            42
                        }
                    },
                    async {
                        singleFlight.execute("k1") {
                            invocations++
                            delay(20L)
                            42
                        }
                    },
                ).awaitAll()
            assertEquals(listOf(42, 42), results)
            assertEquals(1, invocations)

            val clock = MutableClock(1000L)
            val guard = ClickBackpressureGuard(cooldownMillis = 400L, clock = clock)
            assertTrue(guard.tryAcquire())
            assertFalse(guard.tryAcquire())
            clock.advanceByMillis(400L)
            assertTrue(guard.tryAcquire())

            var debouncedValue = 0
            val debouncer = Debouncer(scope = this, waitMillis = 100L)
            debouncer.submit { debouncedValue = 1 }
            debouncer.submit { debouncedValue = 2 }
            delay(150L)
            assertEquals(2, debouncedValue)

            assertNull(FieldValidators.validateEmail(value = "dev@abeta.dev"))
            assertNotNull(FieldValidators.validateEmail(value = "not-an-email"))
            assertNull(FieldValidators.validatePhone(value = "+14155552671"))
            assertNotNull(FieldValidators.validatePhone(value = "123"))
            assertTrue(FieldValidators.isValidUrl("https://abeta.dev/docs"))
        }
}
