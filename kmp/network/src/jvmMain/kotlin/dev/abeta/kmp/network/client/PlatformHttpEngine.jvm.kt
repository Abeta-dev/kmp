package dev.abeta.kmp.network.client

import dev.abeta.kmp.network.config.HttpProtocolMode
import dev.abeta.kmp.network.config.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.util.ContentEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import okhttp3.CertificatePinner
import okhttp3.Protocol
import org.brotli.dec.BrotliInputStream
import kotlin.coroutines.CoroutineContext

internal actual val platformHandlesEncodingNatively: Boolean = false

internal fun buildCertificatePinnerOrNull(certificatePins: Map<String, List<String>>): CertificatePinner? {
    val nonEmptyEntries = certificatePins.filterValues { pins -> pins.isNotEmpty() }
    if (nonEmptyEntries.isEmpty()) return null
    val builder = CertificatePinner.Builder()
    nonEmptyEntries.forEach { (hostname, pins) ->
        pins.forEach { pin ->
            builder.add(hostname, pin)
        }
    }
    return builder.build()
}

internal actual fun createPlatformHttpClient(
    config: NetworkConfig,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                val protocols =
                    when (config.protocolMode) {
                        HttpProtocolMode.AUTO_HTTP2 -> listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
                        HttpProtocolMode.HTTP_1_1_ONLY -> listOf(Protocol.HTTP_1_1)
                    }
                protocols(protocols)
                buildCertificatePinnerOrNull(config.certificatePins)?.let { pinner ->
                    certificatePinner(pinner)
                }
            }
        }
        configure()
    }

internal actual fun ContentEncodingConfig.installPlatformBrotli(quality: Float?) {
    customEncoder(BrotliContentEncoder, quality)
}

/**
 * Ktor 3 [ContentEncoder] decoding HTTP `Content-Encoding: br` response streams via `org.brotli:dec`.
 */
object BrotliContentEncoder : ContentEncoder {
    override val name: String = "br"

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext,
    ): ByteReadChannel = BrotliInputStream(source.toInputStream()).toByteReadChannel(coroutineContext)

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext,
    ): ByteReadChannel = source

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext,
    ): ByteWriteChannel = source
}
