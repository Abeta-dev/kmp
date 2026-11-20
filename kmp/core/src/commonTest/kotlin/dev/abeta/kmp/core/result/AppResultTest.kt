package dev.abeta.kmp.core.result

import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.error.ValidationError
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppResultTest {
    @Test
    fun `map transforms Success and preserves Failure`() {
        val success: AppResult<Int> = AppResult.Success(21)
        val failure: AppResult<Int> = AppResult.Failure(ValidationError(field = "age", message = "Invalid"))

        assertEquals(AppResult.Success(42), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains Success and short-circuits Failure`() {
        val initial: AppResult<Int> = AppResult.Success(10)
        val chained =
            initial.flatMap { value ->
                if (value > 0) {
                    AppResult.Success(value.toString())
                } else {
                    AppResult.Failure(ValidationError(message = "Non-positive"))
                }
            }
        assertEquals(AppResult.Success("10"), chained)

        val failure: AppResult<Int> = AppResult.Failure(NetworkError(statusCode = 503, message = "Unavailable"))
        var invoked = false
        val failedChain =
            failure.flatMap {
                invoked = true
                AppResult.Success("unreachable")
            }
        assertEquals<AppResult<*>>(failure, failedChain)
        assertEquals(false, invoked)
    }

    @Test
    fun `fold invokes matching branch`() {
        val success: AppResult<String> = AppResult.Success("ok")
        val failure: AppResult<String> = AppResult.Failure(UnexpectedError(message = "boom"))

        assertEquals("value=ok", success.fold(onSuccess = { "value=$it" }, onFailure = { "err=${it.code}" }))
        assertEquals(
            "err=UNEXPECTED_ERROR",
            failure.fold(onSuccess = { "value=$it" }, onFailure = { "err=${it.code}" }),
        )
    }

    @Test
    fun `onSuccess and onFailure invoke side effects only for matching branch`() {
        var successSeen = 0
        var failureSeen = 0

        AppResult
            .Success(5)
            .onSuccess { successSeen += it }
            .onFailure { failureSeen += 1 }

        AppResult
            .Failure(UnexpectedError(message = "fail"))
            .onSuccess { successSeen += 100 }
            .onFailure { failureSeen += 1 }

        assertEquals(5, successSeen)
        assertEquals(1, failureSeen)
    }

    @Test
    fun `getOrNull and errorOrNull extract expected branches`() {
        val error = NetworkError(statusCode = 404, message = "Not found")
        val success: AppResult<String> = AppResult.Success("data")
        val failure: AppResult<String> = AppResult.Failure(error)

        assertEquals("data", success.getOrNull())
        assertNull(success.errorOrNull())

        assertNull(failure.getOrNull())
        assertEquals(error, failure.errorOrNull())
    }

    @Test
    fun `runCatchingResult wraps return value and maps non-cancellation throwables`() {
        val ok = runCatchingResult { 123 }
        assertEquals(AppResult.Success(123), ok)

        val cause = IllegalStateException("broken state")
        val failed = runCatchingResult { throw cause }
        val failure = assertIs<AppResult.Failure>(failed)
        val unexpected = assertIs<UnexpectedError>(failure.error)
        assertEquals("broken state", unexpected.message)
        assertEquals(cause, unexpected.cause)
    }

    @Test
    fun `runCatchingResult uses custom error mapper and rethrows CancellationException`() {
        val mapped =
            runCatchingResult(
                mapError = { ValidationError(field = "input", message = it.message.orEmpty(), cause = it) },
            ) {
                throw IllegalArgumentException("bad input")
            }
        val failure = assertIs<AppResult.Failure>(mapped)
        val validationError = assertIs<ValidationError>(failure.error)
        assertEquals("input", validationError.field)
        assertEquals("bad input", validationError.message)

        assertFailsWith<CancellationException> {
            runCatchingResult {
                throw CancellationException("cancelled")
            }
        }
        assertTrue(true)
    }
}
