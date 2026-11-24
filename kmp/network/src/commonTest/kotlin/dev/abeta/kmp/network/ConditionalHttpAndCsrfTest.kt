package dev.abeta.kmp.network

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.ConditionalHttpResult
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpResponseEnvelope
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.client.safeDelete
import dev.abeta.kmp.network.client.safeGet
import dev.abeta.kmp.network.client.safeGetConditional
import dev.abeta.kmp.network.client.safeGetWithResponse
import dev.abeta.kmp.network.client.safePost
import dev.abeta.kmp.network.client.safePut
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.network.ports.CsrfTokenProvider
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionalHttpAndCsrfTest {
    @Serializable
    private data class ScreenDto(
        val id: String,
        val version: Int,
    )

    @Serializable
    private data class MutationPayload(
        val action: String,
    )

    private fun testConfig(): NetworkConfig =
        NetworkConfig(
            baseUrl = "https://api.abeta.dev",
            retry = RetryPolicy(maxRetries = 0),
        )

    @Test
    fun requestWithResponsePreservesStatusHeadersAndCaseInsensitiveEtag() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"id":"home","version":3}""",
                        status = HttpStatusCode.Created,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                "eTaG" to listOf("W/\"screen-v3\""),
                                "X-Request-Id" to listOf("req-777"),
                            ),
                    )
                }

            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                )

            val result = transport.safeGetWithResponse<ScreenDto>("/v1/screens/home")
            val success = assertIs<AppResult.Success<HttpResponseEnvelope<ScreenDto>>>(result)
            val envelope = success.value

            assertEquals(201, envelope.statusCode)
            assertEquals(ScreenDto(id = "home", version = 3), envelope.body)
            assertEquals("W/\"screen-v3\"", envelope.etag)
            assertEquals("W/\"screen-v3\"", envelope.header("ETAG"))
            assertEquals("req-777", envelope.header("x-request-id"))
            assertNull(envelope.header("X-Missing"))
        }

    @Test
    fun requestConditionalReturnsNotModifiedOn304AndRecordsSuccessTelemetry() =
        runTest {
            var capturedIfNoneMatch: String? = null
            val engine =
                MockEngine { request ->
                    capturedIfNoneMatch = request.headers[HttpHeaders.IfNoneMatch]
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers =
                            headersOf(
                                HttpHeaders.ETag to listOf("\"etag-v4\""),
                                "Cache-Control" to listOf("max-age=60"),
                            ),
                    )
                }

            val metrics = mutableListOf<NetworkMetric>()
            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val result =
                transport.safeGetConditional<ScreenDto>(
                    path = "/v1/screens/home",
                    ifNoneMatch = "\"etag-v4\"",
                )

            assertEquals("\"etag-v4\"", capturedIfNoneMatch)
            val success = assertIs<AppResult.Success<ConditionalHttpResult<ScreenDto>>>(result)
            val notModified = assertIs<ConditionalHttpResult.NotModified>(success.value)
            assertEquals("\"etag-v4\"", notModified.etag)
            assertEquals("max-age=60", notModified.headers["Cache-Control"])

            assertEquals(1, metrics.size)
            val metric = metrics.single()
            assertEquals("GET", metric.method)
            assertEquals(304, metric.statusCode)
            assertTrue(metric.isSuccess)
            assertNull(metric.errorType)
        }

    @Test
    fun requestConditionalFallsBackToRequestIfNoneMatchWhen304OmitsEtagAndReturnsModifiedOn200() =
        runTest {
            var callCount = 0
            val engine =
                MockEngine {
                    callCount += 1
                    if (callCount == 1) {
                        respond(content = "", status = HttpStatusCode.NotModified)
                    } else {
                        respond(
                            content = """{"id":"catalog","version":5}""",
                            status = HttpStatusCode.OK,
                            headers =
                                headersOf(
                                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                    HttpHeaders.ETag to listOf("\"etag-v5\""),
                                ),
                        )
                    }
                }

            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                )

            val first =
                assertIs<AppResult.Success<ConditionalHttpResult<ScreenDto>>>(
                    transport.safeGetConditional<ScreenDto>("/v1/screens/catalog", ifNoneMatch = "\"etag-v4\""),
                )
            val notModified = assertIs<ConditionalHttpResult.NotModified>(first.value)
            assertEquals("\"etag-v4\"", notModified.etag)

            val second =
                assertIs<AppResult.Success<ConditionalHttpResult<ScreenDto>>>(
                    transport.safeGetConditional<ScreenDto>("/v1/screens/catalog", ifNoneMatch = "\"etag-v4\""),
                )
            val modified = assertIs<ConditionalHttpResult.Modified<ScreenDto>>(second.value)
            assertEquals(200, modified.envelope.statusCode)
            assertEquals("\"etag-v5\"", modified.envelope.etag)
            assertEquals(ScreenDto(id = "catalog", version = 5), modified.envelope.body)
        }

    @Test
    fun defaultInterfaceMethodsRemainBackwardsCompatible() =
        runTest {
            var capturedHeaders: Map<String, String> = emptyMap()
            val minimalTransport =
                object : HttpTransport {
                    override suspend fun <T> request(
                        method: HttpMethodType,
                        path: String,
                        deserializer: DeserializationStrategy<T>,
                        headers: Map<String, String>,
                        queryParams: Map<String, String>,
                        body: HttpRequestBody?,
                    ): AppResult<T> {
                        capturedHeaders = headers
                        @Suppress("UNCHECKED_CAST")
                        return AppResult.Success(ScreenDto(id = "minimal", version = 1) as T)
                    }
                }

            val conditional =
                assertIs<AppResult.Success<ConditionalHttpResult<ScreenDto>>>(
                    minimalTransport.safeGetConditional<ScreenDto>("/v1/minimal", ifNoneMatch = "\"v1\""),
                )
            val modified = assertIs<ConditionalHttpResult.Modified<ScreenDto>>(conditional.value)
            assertEquals(200, modified.envelope.statusCode)
            assertEquals("minimal", modified.envelope.body.id)
            assertEquals("\"v1\"", capturedHeaders["If-None-Match"])
        }

    @Test
    fun csrfTokenInjectedOnMutatingMethodsAndSkippedOnGet() =
        runTest {
            val recorded = mutableListOf<Pair<String, String?>>()
            val engine =
                MockEngine { request ->
                    recorded.add(request.method.value to request.headers[CsrfTokenProvider.DEFAULT_CSRF_HEADER])
                    respond(
                        content = """{"id":"ok","version":1}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            var currentTokenCalls = 0
            val csrfProvider =
                object : CsrfTokenProvider {
                    override suspend fun currentToken(): String {
                        currentTokenCalls += 1
                        return "csrf-token-1"
                    }
                }

            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    csrfTokenProvider = csrfProvider,
                )

            assertIs<AppResult.Success<ScreenDto>>(transport.safeGet<ScreenDto>("/v1/items"))
            assertEquals(0, currentTokenCalls)

            assertIs<AppResult.Success<ScreenDto>>(
                transport.safePost<ScreenDto, MutationPayload>("/v1/items", MutationPayload("create")),
            )
            assertIs<AppResult.Success<ScreenDto>>(
                transport.safePut<ScreenDto, MutationPayload>("/v1/items/1", MutationPayload("replace")),
            )
            assertIs<AppResult.Success<ScreenDto>>(
                transport.request(
                    method = HttpMethodType.PATCH,
                    path = "/v1/items/1",
                    deserializer = ScreenDto.serializer(),
                ),
            )
            assertIs<AppResult.Success<Unit>>(transport.safeDelete<Unit>("/v1/items/1"))

            assertEquals(4, currentTokenCalls)
            assertEquals(
                listOf(
                    "GET" to null,
                    "POST" to "csrf-token-1",
                    "PUT" to "csrf-token-1",
                    "PATCH" to "csrf-token-1",
                    "DELETE" to "csrf-token-1",
                ),
                recorded,
            )
        }

    @Test
    fun csrfRetriesOnceAfter403WithRefreshedTokenAndSucceeds() =
        runTest {
            val receivedTokens = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    val token = request.headers[CsrfTokenProvider.DEFAULT_CSRF_HEADER]
                    receivedTokens.add(token)
                    if (token == "fresh-csrf") {
                        respond(
                            content = """{"id":"saved","version":2}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respondError(HttpStatusCode.Forbidden, "Invalid CSRF token")
                    }
                }

            var refreshCalls = 0
            val csrfProvider =
                object : CsrfTokenProvider {
                    override suspend fun currentToken(): String = "stale-csrf"

                    override suspend fun refreshToken(): String {
                        refreshCalls += 1
                        return "fresh-csrf"
                    }
                }

            val metrics = mutableListOf<NetworkMetric>()
            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                    csrfTokenProvider = csrfProvider,
                )

            val result =
                transport.safePost<ScreenDto, MutationPayload>(
                    path = "/v1/orders",
                    body = MutationPayload("submit"),
                )

            val success = assertIs<AppResult.Success<ScreenDto>>(result)
            assertEquals("saved", success.value.id)
            assertEquals(listOf<String?>("stale-csrf", "fresh-csrf"), receivedTokens)
            assertEquals(1, refreshCalls)
            assertEquals(1, metrics.size)
            assertEquals(200, metrics.single().statusCode)
            assertTrue(metrics.single().isSuccess)
        }

    @Test
    fun csrfStopsAfterSingleRetryWhenServerStillReturns403() =
        runTest {
            var attempts = 0
            var refreshCalls = 0
            val receivedTokens = mutableListOf<String?>()

            val engine =
                MockEngine { request ->
                    attempts += 1
                    receivedTokens.add(request.headers[CsrfTokenProvider.DEFAULT_CSRF_HEADER])
                    respondError(HttpStatusCode.Forbidden, "Still forbidden")
                }

            val csrfProvider =
                object : CsrfTokenProvider {
                    override suspend fun currentToken(): String = "tok-1"

                    override suspend fun refreshToken(): String {
                        refreshCalls += 1
                        return "tok-2"
                    }
                }

            val transport =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = testConfig(), engine = engine),
                    csrfTokenProvider = csrfProvider,
                )

            val result =
                transport.safePost<ScreenDto, MutationPayload>(
                    path = "/v1/orders",
                    body = MutationPayload("submit"),
                    headers = mapOf("x-csrf-token" to "caller-stale"),
                )

            val failure = assertIs<AppResult.Failure>(result)
            assertIs<ForbiddenError>(failure.error)
            assertEquals(2, attempts)
            assertEquals(1, refreshCalls)
            assertEquals(listOf<String?>("caller-stale", "tok-2"), receivedTokens)
        }
}
