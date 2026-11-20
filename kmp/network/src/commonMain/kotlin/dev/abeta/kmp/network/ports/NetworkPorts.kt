package dev.abeta.kmp.network.ports

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
