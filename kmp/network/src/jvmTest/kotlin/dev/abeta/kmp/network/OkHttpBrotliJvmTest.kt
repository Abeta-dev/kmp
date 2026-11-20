package dev.abeta.kmp.network

import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.client.buildCertificatePinnerOrNull
import dev.abeta.kmp.network.client.safeGet
import dev.abeta.kmp.network.config.CompressionPolicy
import dev.abeta.kmp.network.config.HttpProtocolMode
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Canonical RFC 7932 Brotli-encoded bytes of {"ok":true,"encoding":"br"}
@Suppress("MagicNumber")
private val SAMPLE_BROTLI_JSON_BYTES =
    byteArrayOf(
        15,
        13,
        128.toByte(),
        123,
        34,
        111,
        107,
        34,
        58,
        116,
        114,
        117,
        101,
        44,
        34,
        101,
        110,
        99,
        111,
        100,
        105,
        110,
        103,
        34,
        58,
        34,
        98,
        114,
        34,
        125,
        3,
    )

class OkHttpBrotliJvmTest {
    @Serializable
    private data class BrotliPayload(
        val ok: Boolean,
        val encoding: String,
    )

    @Test
    fun `jvm client advertises br and decodes RFC 7932 brotli compressed JSON response`() =
        runTest {
            var acceptEncodingHeader: String? = null
            val engine =
                MockEngine { request ->
                    acceptEncodingHeader = request.headers[HttpHeaders.AcceptEncoding]
                    respond(
                        content = SAMPLE_BROTLI_JSON_BYTES,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ContentEncoding to listOf("br"),
                            ),
                    )
                }

            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                    compression = CompressionPolicy(enableBrotli = true, enableGzip = true, enableDeflate = true),
                )
            val client = KtorHttpTransport(httpClient = HttpClientFactory.create(config = config, engine = engine))
            val result = client.safeGet<BrotliPayload>("/v1/compressed")
            val success = assertIs<AppResult.Success<BrotliPayload>>(result)
            assertEquals(BrotliPayload(ok = true, encoding = "br"), success.value)
            val resolvedHeader = assertNotNull(acceptEncodingHeader)
            assertTrue(resolvedHeader.contains("br"), "Expected 'br' in Accept-Encoding, got $resolvedHeader")
        }

    @Test
    fun `platform okhttp factory builds both HTTP2 and HTTP1 clients cleanly`() {
        val h2Client =
            HttpClientFactory.create(
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    protocolMode = HttpProtocolMode.AUTO_HTTP2,
                ),
            )
        val h1Client =
            HttpClientFactory.create(
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    protocolMode = HttpProtocolMode.HTTP_1_1_ONLY,
                ),
            )
        h2Client.close()
        h1Client.close()
    }

    @Test
    fun `certificate pins configure OkHttp CertificatePinner cleanly`() {
        assertNull(buildCertificatePinnerOrNull(emptyMap()))
        assertNull(buildCertificatePinnerOrNull(mapOf("api.abeta.dev" to emptyList())))

        val samplePin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val pins = mapOf("api.abeta.dev" to listOf(samplePin))
        val pinner = assertNotNull(buildCertificatePinnerOrNull(pins))
        assertEquals(1, pinner.pins.size)
        assertEquals("api.abeta.dev", pinner.pins.first().pattern)
        assertEquals(
            samplePin,
            pinner.pins.first().hashAlgorithm + "/" +
                pinner.pins
                    .first()
                    .hash
                    .base64(),
        )

        val pinnedClient =
            HttpClientFactory.create(
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    certificatePins = pins,
                ),
            )
        pinnedClient.close()
    }
}
