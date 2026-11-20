package dev.abeta.kmp.network.client

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
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect fun createPlatformHttpClient(
    config: NetworkConfig,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient

internal expect fun ContentEncodingConfig.installPlatformBrotli(quality: Float? = null)

internal expect val platformHandlesEncodingNatively: Boolean

/**
 * Factory creating configured Ktor [HttpClient] instances from [NetworkConfig] and DIP ports.
 *
 * Configures HTTP/2 ALPN negotiation (OkHttp on JVM/Android, Darwin NSURLSession on Apple, browser on Wasm),
 * Brotli/gzip/deflate compression, timeouts, retries, dynamic headers, and bearer auth.
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
        val usingCustomEngine = engine != null
        val configureBlock: HttpClientConfig<*>.() -> Unit = {
            configureCorePlugins(config, json, forcePluginCompression = usingCustomEngine)
            configureRetry(config)
            configureRequests(config, headerProvider)
            configureAuth(authTokenProvider)
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
            }
        }
    }

    private fun HttpClientConfig<*>.configureRetry(config: NetworkConfig) {
        if (config.retry.maxRetries <= 0) return
        install(HttpRequestRetry) {
            maxRetries = config.retry.maxRetries
            if (config.retry.retryOnServerErrors) {
                retryOnServerErrors(maxRetries = config.retry.maxRetries)
            }
            if (config.retry.retryOnExceptions) {
                retryOnException(
                    maxRetries = config.retry.maxRetries,
                    retryOnTimeout = true,
                )
            }
            exponentialDelay(
                baseDelayMs = config.retry.initialDelayMillis,
                maxDelayMs = config.retry.maxDelayMillis,
            )
        }
    }

    private fun HttpClientConfig<*>.configureRequests(
        config: NetworkConfig,
        headerProvider: HeaderProvider?,
    ) {
        defaultRequest {
            url(config.baseUrl)
            config.defaultHeaders.forEach { (name, value) ->
                header(name, value)
            }
        }

        if (headerProvider != null) {
            install(dynamicHeadersPlugin(headerProvider))
        }
    }

    private fun HttpClientConfig<*>.configureAuth(authTokenProvider: AuthTokenProvider?) {
        if (authTokenProvider == null) return
        install(Auth) {
            bearer {
                loadTokens {
                    authTokenProvider.accessToken()?.let { token ->
                        BearerTokens(accessToken = token, refreshToken = "")
                    }
                }
                refreshTokens {
                    authTokenProvider.refreshToken()?.let { token ->
                        BearerTokens(accessToken = token, refreshToken = "")
                    }
                }
            }
        }
    }

    private fun dynamicHeadersPlugin(headerProvider: HeaderProvider) =
        createClientPlugin("AbetaDynamicHeaders") {
            onRequest { request, _ ->
                headerProvider.headers().forEach { (name, value) ->
                    if (!request.headers.contains(name)) {
                        request.headers.append(name, value)
                    }
                }
            }
        }
}
