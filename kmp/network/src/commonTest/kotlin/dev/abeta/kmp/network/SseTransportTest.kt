package dev.abeta.kmp.network

import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import dev.abeta.kmp.network.sse.KtorSseTransport
import dev.abeta.kmp.network.sse.SseEvent
import dev.abeta.kmp.network.sse.SseLineParser
import dev.abeta.kmp.network.sse.SseReconnectPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseTransportTest {
    private fun testConfig(): NetworkConfig =
        NetworkConfig(
            baseUrl = "https://api.abeta.dev",
            retry = RetryPolicy(maxRetries = 0),
        )

    @Test
    fun sseLineParserHandlesMultilineDataCommentsCustomEventsIdRetryAndCrlf() {
        val parser = SseLineParser()

        assertNull(parser.parseLine("\uFEFF: initial keepalive comment\r"))
        assertNull(parser.parseLine("id: evt-1\r"))
        assertNull(parser.parseLine("event: order.updated\r"))
        assertNull(parser.parseLine("retry: 2500\r"))
        assertNull(parser.parseLine("data: line one\r"))
        assertNull(parser.parseLine("data:  indented line two\r"))
        assertNull(parser.parseLine("data:no-space-line\r"))

        val firstEvent = parser.parseLine("\r")
        assertEquals(
            SseEvent(
                id = "evt-1",
                event = "order.updated",
                data = "line one\n indented line two\nno-space-line",
                retryMillis = 2_500L,
            ),
            firstEvent,
        )
        assertEquals("evt-1", parser.lastEventId)
        assertEquals(2_500L, parser.lastRetryMillis)
    }

    @Test
    fun sseLineParserIgnoresInvalidIdAndRetryAndDispatchesOnFlush() {
        val parser = SseLineParser(initialLastEventId = "evt-1")

        assertNull(parser.parseLine("id: bad\u0000id"))
        assertNull(parser.parseLine("retry: -100"))
        assertNull(parser.parseLine("retry: +200"))
        assertNull(parser.parseLine("retry: soon"))
        assertNull(parser.parseLine("data: second payload"))
        val secondEvent = parser.parseLine("")
        assertEquals(
            SseEvent(
                id = "evt-1",
                event = SseEvent.DEFAULT_EVENT_NAME,
                data = "second payload",
                retryMillis = null,
            ),
            secondEvent,
        )

        assertNull(parser.parseLine("id: evt-3"))
        assertNull(parser.parseLine("data: trailing"))
        assertEquals(
            SseEvent(
                id = "evt-3",
                event = SseEvent.DEFAULT_EVENT_NAME,
                data = "trailing",
                retryMillis = null,
            ),
            parser.flush(),
        )
        assertNull(parser.flush())
    }

    @Test
    fun ktorSseTransportStreamsMultipleEventsInOrderWithRequiredHeaders() =
        runTest {
            var capturedAccept: String? = null
            var capturedCacheControl: String? = null
            var capturedCustomHeader: String? = null
            var capturedUrl = ""

            val payload =
                ": heartbeat\n\n" +
                    "id: 1\nevent: price\ndata: {\"symbol\":\"ABT\",\"price\":101}\n\n" +
                    "id: 2\ndata: line-a\ndata: line-b\n\n"

            val engine =
                MockEngine { request ->
                    capturedUrl = request.url.toString()
                    capturedAccept = request.headers[HttpHeaders.Accept]
                    capturedCacheControl = request.headers[HttpHeaders.CacheControl]
                    capturedCustomHeader = request.headers["X-Client-Id"]
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }

            val metrics = mutableListOf<NetworkMetric>()
            val transport =
                KtorSseTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val results =
                transport
                    .connect(
                        path = "/v1/stream/prices",
                        headers = mapOf("X-Client-Id" to "mobile-1"),
                        queryParams = mapOf("channel" to "live"),
                        reconnectPolicy = SseReconnectPolicy(enabled = false),
                    ).toList()

            assertEquals("https://api.abeta.dev/v1/stream/prices?channel=live", capturedUrl)
            assertEquals("text/event-stream", capturedAccept)
            assertEquals("no-cache", capturedCacheControl)
            assertEquals("mobile-1", capturedCustomHeader)

            assertEquals(2, results.size)
            val first = assertIs<AppResult.Success<SseEvent>>(results[0]).value
            val second = assertIs<AppResult.Success<SseEvent>>(results[1]).value
            assertEquals(SseEvent(id = "1", event = "price", data = """{"symbol":"ABT","price":101}"""), first)
            assertEquals(SseEvent(id = "2", event = SseEvent.DEFAULT_EVENT_NAME, data = "line-a\nline-b"), second)
            assertEquals(1, metrics.size)
            assertEquals(200, metrics.single().statusCode)
            assertTrue(metrics.single().isSuccess)
        }

    @Test
    fun ktorSseTransportReconnectsWithLastEventIdAfterDropAndStopsOn204() =
        runTest {
            val capturedLastEventIds = mutableListOf<String?>()
            var attempt = 0

            val engine =
                MockEngine { request ->
                    attempt += 1
                    capturedLastEventIds.add(request.headers["Last-Event-ID"])
                    when (attempt) {
                        1 -> {
                            respond(
                                content = "id: 10\nretry: 50\ndata: first-batch\n\n",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                        }

                        2 -> {
                            throw IllegalStateException("Transient socket drop")
                        }

                        3 -> {
                            respond(
                                content = "id: 11\ndata: resumed-batch\n\n",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                        }

                        else -> {
                            respond(content = "", status = HttpStatusCode.NoContent)
                        }
                    }
                }

            val transport =
                KtorSseTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                )

            val results =
                transport
                    .connect(
                        path = "/v1/stream/orders",
                        lastEventId = "9",
                        reconnectPolicy =
                            SseReconnectPolicy(
                                enabled = true,
                                maxReconnectAttempts = 5,
                                initialDelayMillis = 25L,
                                maxDelayMillis = 200L,
                            ),
                    ).toList()

            assertEquals(listOf<String?>("9", "10", "10", "11"), capturedLastEventIds)
            assertEquals(2, results.size)
            assertEquals("first-batch", assertIs<AppResult.Success<SseEvent>>(results[0]).value.data)
            assertEquals("resumed-batch", assertIs<AppResult.Success<SseEvent>>(results[1]).value.data)
        }

    @Test
    fun ktorSseTransportMapsHttpErrorsAndRespectsReconnectBounds() =
        runTest {
            var calls = 0
            val engine =
                MockEngine { request ->
                    calls += 1
                    when (request.url.encodedPath) {
                        "/v1/unauth" -> respondError(HttpStatusCode.Unauthorized, "Expired")
                        "/v1/missing" -> respondError(HttpStatusCode.NotFound, "Not found")
                        else -> respondError(HttpStatusCode.ServiceUnavailable, "Down")
                    }
                }

            val metrics = mutableListOf<NetworkMetric>()
            val transport =
                KtorSseTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val unauth =
                transport
                    .connect(
                        path = "/v1/unauth",
                        reconnectPolicy = SseReconnectPolicy(enabled = true, maxReconnectAttempts = 3),
                    ).toList()
            assertEquals(1, unauth.size)
            assertIs<UnauthorizedError>(assertIs<AppResult.Failure>(unauth.single()).error)
            assertEquals(1, calls)

            val missing = transport.connect("/v1/missing").toList()
            assertIs<NotFoundError>(assertIs<AppResult.Failure>(missing.single()).error)

            calls = 0
            val serverError =
                transport
                    .connect(
                        path = "/v1/failing",
                        reconnectPolicy =
                            SseReconnectPolicy(
                                enabled = true,
                                maxReconnectAttempts = 2,
                                initialDelayMillis = 10L,
                                maxDelayMillis = 50L,
                            ),
                    ).toList()
            assertEquals(3, calls)
            val err = assertIs<NetworkError>(assertIs<AppResult.Failure>(serverError.single()).error)
            assertEquals(503, err.statusCode)
            assertFalse(metrics.last().isSuccess)
        }

    @Test
    fun ktorSseTransportRespectsCollectorCancellationWithoutReconnecting() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls += 1
                    respond(
                        content = "id: 1\ndata: one\n\nid: 2\ndata: two\n\n",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }

            val transport =
                KtorSseTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                )

            val taken =
                transport
                    .connect(
                        path = "/v1/live",
                        reconnectPolicy = SseReconnectPolicy(enabled = true, maxReconnectAttempts = 5),
                    ).take(1)
                    .toList()

            assertEquals(1, taken.size)
            assertEquals("one", assertIs<AppResult.Success<SseEvent>>(taken.single()).value.data)
            assertEquals(1, calls)
        }

    @Test
    fun sseRejectsNonEventStreamContentTypeAndEnforcesDataLimits() =
        runTest {
            var capturedAcceptEncoding: String? = null
            val htmlEngine =
                MockEngine { request ->
                    capturedAcceptEncoding = request.headers[HttpHeaders.AcceptEncoding]
                    respond(
                        content = "<html>Not SSE</html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }
            val htmlTransport =
                KtorSseTransport(httpClient = HttpClientFactory.create(config = testConfig(), engine = htmlEngine))
            val wrongType =
                htmlTransport
                    .connect(
                        path = "/v1/wrong",
                        reconnectPolicy = SseReconnectPolicy(enabled = true, maxReconnectAttempts = 3),
                    ).toList()
            assertEquals("identity", capturedAcceptEncoding)
            assertEquals(1, wrongType.size)
            assertIs<NetworkError>(assertIs<AppResult.Failure>(wrongType.single()).error)

            val boundedParser = SseLineParser(maxEventDataBytes = 8)
            assertNull(boundedParser.parseLine("data: 12345"))
            assertFailsWith<IllegalStateException> {
                boundedParser.parseLine("data: 67890")
            }
        }

    @Test
    fun sseDropsTruncatedEofEventsAndResetsReconnectCounter() =
        runTest {
            val lastIds = mutableListOf<String?>()
            var streamAttempt = 0
            val dropEngine =
                MockEngine { request ->
                    streamAttempt += 1
                    lastIds.add(request.headers["Last-Event-ID"])
                    when (streamAttempt) {
                        1 -> {
                            respond(
                                content = "id: 1\ndata: ok-1\n\nid: 2\ndata: truncated-no-blank-line",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream; charset=utf-8"),
                            )
                        }

                        2 -> {
                            respond(
                                content = "id: 2\ndata: ok-2\n\n",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                        }

                        3 -> {
                            respond(
                                content = "id: 3\ndata: ok-3\n\n",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                        }

                        else -> {
                            respond(content = "", status = HttpStatusCode.NoContent)
                        }
                    }
                }
            val dropTransport =
                KtorSseTransport(httpClient = HttpClientFactory.create(config = testConfig(), engine = dropEngine))
            val results =
                dropTransport
                    .connect(
                        path = "/v1/resilient",
                        reconnectPolicy =
                            SseReconnectPolicy(
                                enabled = true,
                                maxReconnectAttempts = 1,
                                initialDelayMillis = 1L,
                                maxDelayMillis = 5L,
                            ),
                    ).toList()

            assertEquals(listOf<String?>(null, "1", "2", "3"), lastIds)
            assertEquals(
                listOf("ok-1", "ok-2", "ok-3"),
                results.map { assertIs<AppResult.Success<SseEvent>>(it).value.data },
            )
        }
}
