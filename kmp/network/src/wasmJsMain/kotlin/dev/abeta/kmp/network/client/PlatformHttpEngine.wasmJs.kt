package dev.abeta.kmp.network.client

import dev.abeta.kmp.network.config.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.compression.ContentEncodingConfig

/**
 * Browser `fetch` manages HTTP/2, HTTP/3, gzip, deflate, and Brotli (`br`) natively.
 */
internal actual val platformHandlesEncodingNatively: Boolean = true

internal actual fun createPlatformHttpClient(
    config: NetworkConfig,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(Js) {
        configure()
    }

internal actual fun ContentEncodingConfig.installPlatformBrotli(quality: Float?) {
    // Managed transparently by browser Fetch / Compression Streams.
}
