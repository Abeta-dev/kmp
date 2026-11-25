package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.security.SensitiveDataRedactor
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.ports.AuthTokenProvider
import dev.abeta.kmp.network.ports.HeaderProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

private const val HTTP_SERVER_ERROR_START = 500
private const val HTTP_SERVER_ERROR_END = 599
private const val HEADER_CLIENT_APP_VERSION = "X-Client-App-Version"
private const val HEADER_CSRF_TOKEN = "X-CSRF-Token"
private const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
private const val MIME_EVENT_STREAM = "text/event-stream"

internal expect fun createPlatformHttpClient(
    config: NetworkConfig,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient

internal expect fun ContentEncodingConfig.installPlatformBrotli(quality: Float? = null)

internal expect val platformHandlesEncodingNatively: Boolean

private data class ParsedOrigin(
    val protocol: String,
    val host: String,
    val port: Int,
) {
    fun matches(url: URLBuilder): Boolean {
        val candidatePort = if (url.port == 0) url.protocol.defaultPort else url.port
        return url.protocol.name.equals(protocol, ignoreCase = true) &&
            url.host
                .trim()
                .trimEnd('.')
                .lowercase() == host &&
            candidatePort == port
    }

    fun matches(url: Url): Boolean {
        val candidatePort = if (url.port == 0) url.protocol.defaultPort else url.port
        return url.protocol.name.equals(protocol, ignoreCase = true) &&
            url.host
                .trim()
                .trimEnd('.')
                .lowercase() == host &&
            candidatePort == port
    }

    companion object {
        fun parse(baseUrl: String): ParsedOrigin {
            val parsed = Url(baseUrl.trim())
            val resolvedPort = if (parsed.port == 0) parsed.protocol.defaultPort else parsed.port
            return ParsedOrigin(
                protocol = parsed.protocol.name.lowercase(),
                host =
                    parsed.host
                        .trim()
                        .trimEnd('.')
                        .lowercase(),
                port = resolvedPort,
            )
        }
    }
}

/**
 * Factory creating configured Ktor [HttpClient] instances from [NetworkConfig] and DIP ports.
 *
 * Configures HTTP/2 ALPN negotiation (OkHttp on JVM/Android, Darwin NSURLSession on Apple, browser on Wasm),
 * Brotli/gzip/deflate compression, timeouts, idempotent retries, same-origin headers, and bearer auth.
 */
object HttpClientFactory {
    /** Default lenient JSON configuration used when no custom [Json] instance is supplied. */
    val defaultJson: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

    /**
     * Builds a new [HttpClient] configured from [config]. When [engine] is `null`, the
     * target platform's HTTP/2-capable production engine is instantiated.
     */
    fun create(
        config: NetworkConfig,
        authTokenProvider: AuthTokenProvider? = null,
        headerProvider: HeaderProvider? = null,
        json: Json = defaultJson,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        val baseOrigin = ParsedOrigin.parse(config.baseUrl)
        val usingCustomEngine = engine != null
        val configureBlock: HttpClientConfig<*>.() -> Unit = {
            configureCorePlugins(config, json, forcePluginCompression = usingCustomEngine)
            configureRetry(config)
            configureRequests(config, baseOrigin, headerProvider)
            configureAuth(authTokenProvider, baseOrigin)
        }

        return if (engine != null) {
            HttpClient(engine, configureBlock)
        } else {
            createPlatformHttpClient(config, configureBlock)
        }
    }

    private fun HttpClientConfig<*>.configureCorePlugins(
        config: NetworkConfig,
        json: Json,
        forcePluginCompression: Boolean,
    ) {
        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = config.timeouts.connectTimeoutMillis
            requestTimeoutMillis = config.timeouts.requestTimeoutMillis
            socketTimeoutMillis = config.timeouts.socketTimeoutMillis
        }

        val compression = config.compression
        val shouldInstallPlugin =
            (forcePluginCompression || !platformHandlesEncodingNatively) &&
                (compression.enableBrotli || compression.enableGzip || compression.enableDeflate)

        if (shouldInstallPlugin) {
            install(ContentEncoding) {
                if (compression.enableBrotli) {
                    installPlatformBrotli(quality = 1.0f)
                }
                if (compression.enableGzip) {
                    gzip(quality = 0.9f)
                }
                if (compression.enableDeflate) {
                    deflate(quality = 0.8f)
                }
            }
        }

        if (config.isDebug) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = Logger.SIMPLE
                sanitizeHeader { name -> isSensitiveLogHeader(name) }
            }
        }
    }

    private fun HttpClientConfig<*>.configureRetry(config: NetworkConfig) {
        if (config.retry.maxRetries <= 0) return
        install(HttpRequestRetry) {
            maxRetries = config.retry.maxRetries
            if (config.retry.retryOnServerErrors) {
                retryIf(maxRetries = config.retry.maxRetries) { request, response ->
                    isRetryEligibleRequest(request) &&
                        response.status.value in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END
                }
            }
            if (config.retry.retryOnExceptions) {
                retryOnExceptionIf(maxRetries = config.retry.maxRetries) { request, cause ->
                    cause !is CancellationException && isRetryEligibleBuilder(request)
                }
            }
            exponentialDelay(
                baseDelayMs = config.retry.initialDelayMillis,
                maxDelayMs = config.retry.maxDelayMillis,
            )
        }
    }

    private fun HttpClientConfig<*>.configureRequests(
        config: NetworkConfig,
        baseOrigin: ParsedOrigin,
        headerProvider: HeaderProvider?,
    ) {
        defaultRequest {
            url(config.baseUrl)
        }

        val staticHeaders =
            buildMap {
                putAll(config.defaultHeaders)
                val appVer = config.appVersion?.trim()
                if (!appVer.isNullOrEmpty() && !containsHeaderIgnoreCase(HEADER_CLIENT_APP_VERSION)) {
                    put(HEADER_CLIENT_APP_VERSION, appVer)
                }
            }

        if (staticHeaders.isNotEmpty() || headerProvider != null) {
            install(sameOriginHeadersPlugin(baseOrigin, staticHeaders, headerProvider))
        }
    }

    private fun HttpClientConfig<*>.configureAuth(
        authTokenProvider: AuthTokenProvider?,
        baseOrigin: ParsedOrigin,
    ) {
        if (authTokenProvider == null) return
        val refreshedOverride = MutableStateFlow<Pair<String, String>?>(null)
        install(Auth) {
            bearer {
                cacheTokens = false
                sendWithoutRequest { request -> baseOrigin.matches(request.url) }
                loadTokens {
                    val current = authTokenProvider.accessToken()
                    val pending = refreshedOverride.value
                    val resolved =
                        if (current != null && pending != null && current == pending.first) {
                            pending.second
                        } else {
                            if (pending != null) {
                                refreshedOverride.value = null
                            }
                            current
                        }
                    resolved?.let { token -> BearerTokens(accessToken = token, refreshToken = "") }
                }
                refreshTokens {
                    if (!baseOrigin.matches(response.call.request.url)) {
                        return@refreshTokens null
                    }
                    val refreshed = authTokenProvider.refreshToken()
                    val priorToken = oldTokens?.accessToken
                    if (refreshed != null) {
                        refreshedOverride.value = priorToken?.let { it to refreshed }
                        BearerTokens(accessToken = refreshed, refreshToken = "")
                    } else {
                        refreshedOverride.value = null
                        null
                    }
                }
            }
        }
    }

    private fun sameOriginHeadersPlugin(
        baseOrigin: ParsedOrigin,
        staticHeaders: Map<String, String>,
        headerProvider: HeaderProvider?,
    ) = createClientPlugin("AbetaSameOriginHeaders") {
        onRequest { request, _ ->
            if (!baseOrigin.matches(request.url)) return@onRequest
            staticHeaders.forEach { (name, value) ->
                if (!request.headers.containsIgnoreCase(name)) {
                    request.headers.append(name, value)
                }
            }
            headerProvider?.headers()?.forEach { (name, value) ->
                if (!request.headers.containsIgnoreCase(name)) {
                    request.headers.append(name, value)
                }
            }
        }
    }

    private fun isSensitiveLogHeader(name: String): Boolean =
        SensitiveDataRedactor.Default.isSensitiveKey(name) ||
            name.equals(HttpHeaders.Cookie, ignoreCase = true) ||
            name.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
            name.equals(HEADER_CSRF_TOKEN, ignoreCase = true) ||
            name.equals(HttpHeaders.Authorization, ignoreCase = true)

    private fun isRetryEligibleRequest(request: HttpRequest): Boolean {
        val accept = request.headers[HttpHeaders.Accept].orEmpty()
        if (accept.contains(MIME_EVENT_STREAM, ignoreCase = true)) return false
        return isIdempotentMethod(request.method) || request.headers[HEADER_IDEMPOTENCY_KEY] != null
    }

    private fun isRetryEligibleBuilder(request: HttpRequestBuilder): Boolean {
        val accept = request.headers[HttpHeaders.Accept].orEmpty()
        if (accept.contains(MIME_EVENT_STREAM, ignoreCase = true)) return false
        return isIdempotentMethod(request.method) || request.headers.containsIgnoreCase(HEADER_IDEMPOTENCY_KEY)
    }

    private fun isIdempotentMethod(method: HttpMethod): Boolean =
        method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options

    private fun HeadersBuilder.containsIgnoreCase(name: String): Boolean =
        names().any { existing -> existing.equals(name, ignoreCase = true) }
}
