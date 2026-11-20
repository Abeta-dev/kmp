@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
@file:Suppress("MagicNumber")

package dev.abeta.kmp.network.client

import dev.abeta.kmp.network.config.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import io.ktor.client.plugins.compression.ContentEncodingConfig
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.io.encoding.Base64

/**
 * Apple `NSURLSession` natively negotiates HTTP/2, HTTP/3, gzip, deflate, and Brotli (`br`),
 * transparently decompressing response bodies without custom Ktor stream wrappers.
 */
internal actual val platformHandlesEncodingNatively: Boolean = true

internal expect fun DarwinClientEngineConfig.configureApplePinnedChallenge(certificatePins: Map<String, List<String>>)

internal actual fun createPlatformHttpClient(
    config: NetworkConfig,
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(Darwin) {
        if (config.certificatePins.isNotEmpty()) {
            engine {
                configureApplePinnedChallenge(config.certificatePins)
            }
        }
        configure()
    }

internal actual fun ContentEncodingConfig.installPlatformBrotli(quality: Float?) {
    // Handled natively by NSURLSession in Apple Darwin runtime.
}

internal fun certificateChainMatchesPins(
    certDers: List<ByteArray>,
    expectedPins: Set<String>,
): Boolean = certDers.any { der -> certificateMatchesPins(der, expectedPins) }

private fun certificateMatchesPins(
    certDer: ByteArray,
    expectedPins: Set<String>,
): Boolean {
    val spkiBytes = CertificatePinMatcher.extractSpkiDerOrNull(certDer)
    val spkiPin = spkiBytes?.let { "sha256/${sha256Base64(it)}" }
    val certPin = "sha256/${sha256Base64(certDer)}"
    return (spkiPin != null && spkiPin in expectedPins) || certPin in expectedPins
}

private fun sha256Base64(input: ByteArray): String =
    memScoped {
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { pinned ->
            CC_SHA256(pinned.addressOf(0), input.size.toUInt(), digest)
        }
        val rawBytes = digest.reinterpret<ByteVar>().readBytes(CC_SHA256_DIGEST_LENGTH)
        Base64.encode(rawBytes)
    }
