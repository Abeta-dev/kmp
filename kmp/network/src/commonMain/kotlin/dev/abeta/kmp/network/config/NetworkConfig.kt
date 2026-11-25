package dev.abeta.kmp.network.config

/**
 * Preferred HTTP wire protocol behavior for multiplatform Ktor engines.
 */
enum class HttpProtocolMode {
    /** Negotiate HTTP/2 (or HTTP/3 on Apple Darwin / Web) via ALPN with HTTP/1.1 fallback. */
    AUTO_HTTP2,

    /** Restrict engine negotiation to HTTP/1.1 only. */
    HTTP_1_1_ONLY,
}

/**
 * Response decompression and `Accept-Encoding` negotiation preferences.
 */
data class CompressionPolicy(
    val enableBrotli: Boolean = true,
    val enableGzip: Boolean = true,
    val enableDeflate: Boolean = true,
)

/**
 * Top-level configuration for [dev.abeta.kmp.network.client.HttpTransport].
 *
 * @property baseUrl Base URL applied to relative request paths (e.g. `"https://api.example.com"`).
 * @property isDebug Enables verbose HTTP logging when `true`.
 * @property timeouts Timeout policy applied to connection, request, and socket operations.
 * @property retry Retry policy applied to transient transport and server failures.
 * @property protocolMode Preferred HTTP protocol version negotiation policy.
 * @property compression Content encoding (`br`, `gzip`, `deflate`) preferences.
 * @property defaultHeaders Static headers attached to every outgoing same-origin request.
 * @property certificatePins Optional map of hostname pattern to SHA-256 certificate pins (e.g. `"sha256/..."`).
 * @property appVersion Optional client application version attached as `X-Client-App-Version` on same-origin requests.
 */
data class NetworkConfig(
    val baseUrl: String,
    val isDebug: Boolean = false,
    val timeouts: TimeoutPolicy = TimeoutPolicy(),
    val retry: RetryPolicy = RetryPolicy(),
    val protocolMode: HttpProtocolMode = HttpProtocolMode.AUTO_HTTP2,
    val compression: CompressionPolicy = CompressionPolicy(),
    val defaultHeaders: Map<String, String> = emptyMap(),
    val certificatePins: Map<String, List<String>> = emptyMap(),
    val appVersion: String? = null,
) {
    init {
        require(baseUrl.isNotBlank()) { "NetworkConfig.baseUrl must not be blank." }
        certificatePins.forEach { (hostname, pins) ->
            require(hostname.isNotBlank()) { "NetworkConfig.certificatePins hostname must not be blank." }
            pins.forEach { pin ->
                require(pin.trim().startsWith(SHA256_PIN_PREFIX)) {
                    "Certificate pin for '$hostname' must start with '$SHA256_PIN_PREFIX' (got '$pin')."
                }
            }
        }
    }

    private companion object {
        const val SHA256_PIN_PREFIX = "sha256/"
    }
}

/**
 * Timeout thresholds (in milliseconds) for Ktor HTTP operations.
 */
data class TimeoutPolicy(
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    val socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MS,
) {
    init {
        require(connectTimeoutMillis > 0L) { "connectTimeoutMillis must be positive." }
        require(requestTimeoutMillis > 0L) { "requestTimeoutMillis must be positive." }
        require(socketTimeoutMillis > 0L) { "socketTimeoutMillis must be positive." }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 10_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS: Long = 30_000L
        const val DEFAULT_SOCKET_TIMEOUT_MS: Long = 30_000L
    }
}

/**
 * Exponential-backoff retry configuration for transient network failures.
 */
data class RetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MS,
    val retryOnServerErrors: Boolean = true,
    val retryOnExceptions: Boolean = true,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0." }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must be >= 0." }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis." }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 2
        const val DEFAULT_INITIAL_DELAY_MS: Long = 300L
        const val DEFAULT_MAX_DELAY_MS: Long = 5_000L
    }
}
