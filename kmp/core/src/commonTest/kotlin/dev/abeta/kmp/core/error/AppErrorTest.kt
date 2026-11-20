package dev.abeta.kmp.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AppErrorTest {
    @Test
    fun `default codes and optional fields are populated as expected`() {
        val cause = RuntimeException("root")

        val network: AppError = NetworkError(statusCode = 502, message = "Bad gateway", cause = cause)
        val validation: AppError = ValidationError(field = "email", message = "Invalid email")
        val serialization: AppError = SerializationError(message = "Malformed JSON")
        val notFound: AppError = NotFoundError(resource = "user/42", message = "Missing user")
        val unauthorized: AppError = UnauthorizedError(message = "Expired session")
        val unexpected: AppError = UnexpectedError(message = "Unknown failure")

        assertEquals(NetworkError.DEFAULT_CODE, network.code)
        assertEquals(502, assertIs<NetworkError>(network).statusCode)
        assertEquals(cause, network.cause)

        assertEquals(ValidationError.DEFAULT_CODE, validation.code)
        assertEquals("email", assertIs<ValidationError>(validation).field)

        assertEquals(SerializationError.DEFAULT_CODE, serialization.code)
        assertNull(serialization.cause)

        assertEquals(NotFoundError.DEFAULT_CODE, notFound.code)
        assertEquals("user/42", assertIs<NotFoundError>(notFound).resource)

        assertEquals(UnauthorizedError.DEFAULT_CODE, unauthorized.code)
        assertEquals(UnexpectedError.DEFAULT_CODE, unexpected.code)
    }

    @Test
    fun `custom error codes override defaults`() {
        val error = NetworkError(statusCode = 429, message = "Rate limited", code = "RATE_LIMITED")
        assertEquals("RATE_LIMITED", error.code)
        assertEquals(429, error.statusCode)
    }
}
