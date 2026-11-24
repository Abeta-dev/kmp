package dev.abeta.kmp.network.ports

import dev.abeta.kmp.network.client.HttpMethodType

/**
 * Port providing bearer authentication tokens without coupling the network module
 * to storage or auth-flow implementations.
 */
interface AuthTokenProvider {
    /** Returns the current access token, or `null` when unauthenticated. */
    suspend fun accessToken(): String?

    /** Refreshes and returns a new access token after an HTTP 401, or `null` if refresh fails. */
    suspend fun refreshToken(): String?
}

/**
 * Port providing CSRF protection tokens for state-changing HTTP requests.
 */
interface CsrfTokenProvider {
    /** HTTP header name carrying the CSRF token (defaults to [DEFAULT_CSRF_HEADER]). */
    val headerName: String
        get() = DEFAULT_CSRF_HEADER

    /** HTTP methods requiring CSRF protection (defaults to [DEFAULT_PROTECTED_METHODS]). */
    val protectedMethods: Set<HttpMethodType>
        get() = DEFAULT_PROTECTED_METHODS

    /** Returns the current CSRF token, or `null` when unavailable. */
    suspend fun currentToken(): String?

    /** Refreshes and returns a new CSRF token after an HTTP 403 rejection, or `null` if refresh fails. */
    suspend fun refreshToken(): String? = currentToken()

    companion object {
        const val DEFAULT_CSRF_HEADER: String = "X-CSRF-Token"
        val DEFAULT_PROTECTED_METHODS: Set<HttpMethodType> =
            setOf(
                HttpMethodType.POST,
                HttpMethodType.PUT,
                HttpMethodType.PATCH,
                HttpMethodType.DELETE,
            )
    }
}

/**
 * Port supplying dynamic per-request headers (e.g. device id, locale, session id).
 */
interface HeaderProvider {
    /** Returns headers to merge into an outgoing request. */
    suspend fun headers(): Map<String, String>
}

/**
 * Port receiving structured telemetry events when an HTTP call finishes.
 */
fun interface NetworkTelemetrySink {
    /** Invoked upon completion (success or failure) of an HTTP request. */
    fun onRequestComplete(metric: NetworkMetric)
}

/**
 * Immutable telemetry snapshot for a single HTTP request execution.
 */
data class NetworkMetric(
    val method: String,
    val url: String,
    val statusCode: Int?,
    val durationMillis: Long,
    val isSuccess: Boolean,
    val errorType: String? = null,
)
