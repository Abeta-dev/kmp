package dev.abeta.kmp.layout.media

import androidx.compose.ui.graphics.ImageBitmap
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorImageRendererTest {
    @Test
    fun loadsAndDecodesValidPngBytesAndEvictsLeastRecentlyUsedEntries() =
        runTest {
            val requestedUrls = mutableListOf<String>()
            val renderer =
                KtorImageRenderer(
                    fetcher =
                        ImageBytesFetcher { url ->
                            requestedUrls.add(url)
                            AppResult.Success(PNG_1X1_RGBA_BYTES)
                        },
                    maxCacheEntries = 2,
                )

            val bmp1Result = renderer.loadBitmap("https://cdn.abeta.dev/1.png")
            val bmp1 = assertIs<AppResult.Success<ImageBitmap>>(bmp1Result).value
            assertEquals(1, bmp1.width)
            assertEquals(1, bmp1.height)
            assertEquals(1, renderer.cacheSize())

            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/1.png"))
            assertEquals(listOf("https://cdn.abeta.dev/1.png"), requestedUrls)

            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/2.png"))
            assertEquals(2, renderer.cacheSize())

            // Touch 1.png so 2.png becomes LRU, then insert 3.png -> 2.png gets evicted
            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/1.png"))
            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/3.png"))
            assertEquals(2, renderer.cacheSize())

            // 1.png is still cached (no new fetch), 2.png must re-fetch
            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/1.png"))
            assertEquals(3, requestedUrls.size)
            assertIs<AppResult.Success<ImageBitmap>>(renderer.loadBitmap("https://cdn.abeta.dev/2.png"))
            assertEquals(4, requestedUrls.size)
        }

    @Test
    fun ktorImageBytesFetcherAndInvalidBytesReturnTypedErrors() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/ok.png" -> {
                                respond(
                                    content = PNG_1X1_RGBA_BYTES,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
                                )
                            }

                            "/bad.png" -> {
                                respond(
                                    content = byteArrayOf(1, 2, 3, 4),
                                    status = HttpStatusCode.OK,
                                )
                            }

                            else -> {
                                respond(
                                    content = "Missing",
                                    status = HttpStatusCode.NotFound,
                                )
                            }
                        }
                    },
                )
            val fetcher = KtorImageBytesFetcher(client)
            val renderer = KtorImageRenderer(fetcher)

            assertIs<ValidationError>(assertIs<AppResult.Failure>(fetcher.fetch("   ")).error)
            val okBytes = assertIs<AppResult.Success<ByteArray>>(fetcher.fetch("https://cdn.abeta.dev/ok.png")).value
            assertContentEquals(PNG_1X1_RGBA_BYTES, okBytes)

            val notFound = fetcher.fetch("https://cdn.abeta.dev/404.png")
            val netErr = assertIs<NetworkError>(assertIs<AppResult.Failure>(notFound).error)
            assertEquals(404, netErr.statusCode)

            val invalid = renderer.loadBitmap("https://cdn.abeta.dev/bad.png")
            assertIs<SerializationError>(assertIs<AppResult.Failure>(invalid).error)
        }

    private companion object {
        val PNG_1X1_RGBA_BYTES: ByteArray =
            byteArrayOf(
                137.toByte(),
                80,
                78,
                71,
                13,
                10,
                26,
                10,
                0,
                0,
                0,
                13,
                73,
                72,
                68,
                82,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                1,
                8,
                6,
                0,
                0,
                0,
                31,
                21,
                196.toByte(),
                137.toByte(),
                0,
                0,
                0,
                13,
                73,
                68,
                65,
                84,
                120,
                156.toByte(),
                99,
                248.toByte(),
                207.toByte(),
                192.toByte(),
                240.toByte(),
                31,
                0,
                5,
                0,
                1,
                255.toByte(),
                137.toByte(),
                153.toByte(),
                61,
                29,
                0,
                0,
                0,
                0,
                73,
                69,
                78,
                68,
                174.toByte(),
                66,
                96,
                130.toByte(),
            )
    }
}
