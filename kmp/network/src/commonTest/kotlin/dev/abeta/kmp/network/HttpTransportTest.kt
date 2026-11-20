package dev.abeta.kmp.network

import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.client.safeDelete
import dev.abeta.kmp.network.client.safeGet
import dev.abeta.kmp.network.client.safePost
import dev.abeta.kmp.network.client.safePut
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.network.ports.AuthTokenProvider
import dev.abeta.kmp.network.ports.HeaderProvider
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpTransportTest {
    @Serializable
    private data class UserDto(
        val id: String,
        val name: String,
    )

    @Serializable
    private data class CreateUserRequest(
        val name: String,
    )

    private class AdvancingClock(
        private var currentMillis: Long = 1_000L,
        private val stepMillis: Long = 25L,
    ) : Clock {
        override fun nowEpochMillis(): Long {
            val value = currentMillis
            currentMillis += stepMillis
            return value
        }
    }

    @Test
    fun safeGetDecodesJsonAndInjectsStaticDynamicAndAuthHeaders() =
        runTest {
            var capturedHeaders: Map<String, String> = emptyMap()
            var capturedUrl = ""

            val engine =
                MockEngine { request ->
                    capturedUrl = request.url.toString()
                    capturedHeaders = request.headers.entries().associate { (k, v) -> k to v.first() }
                    respond(
                        content = """{"id":"u-42","name":"Ada Lovelace","ignored":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                    defaultHeaders = mapOf("X-App-Platform" to "kmp"),
                )
            val authProvider =
                object : AuthTokenProvider {
                    override suspend fun accessToken(): String = "jwt-initial"

                    override suspend fun refreshToken(): String? = null
                }
            val headerProvider =
                object : HeaderProvider {
                    override suspend fun headers(): Map<String, String> = mapOf("X-Session-Id" to "sess-99")
                }

            val metrics = mutableListOf<NetworkMetric>()
            val httpClient =
                HttpClientFactory.create(
                    config = config,
                    authTokenProvider = authProvider,
                    headerProvider = headerProvider,
                    engine = engine,
                )
            val client =
                KtorHttpTransport(
                    httpClient = httpClient,
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                    clock = AdvancingClock(currentMillis = 100L, stepMillis = 40L),
                )

            val result =
                client.safeGet<UserDto>(
                    path = "/v1/users/u-42",
                    headers = mapOf("X-Trace-Id" to "trace-1"),
                    queryParams = mapOf("include" to "profile"),
                )

            val success = assertIs<AppResult.Success<UserDto>>(result)
            assertEquals(UserDto(id = "u-42", name = "Ada Lovelace"), success.value)
            assertEquals("https://api.abeta.dev/v1/users/u-42?include=profile", capturedUrl)
            assertEquals("kmp", capturedHeaders["X-App-Platform"])
            assertEquals("sess-99", capturedHeaders["X-Session-Id"])
            assertEquals("trace-1", capturedHeaders["X-Trace-Id"])
            assertEquals("Bearer jwt-initial", capturedHeaders[HttpHeaders.Authorization])

            assertEquals(1, metrics.size)
            val metric = metrics.single()
            assertEquals("GET", metric.method)
            assertEquals(200, metric.statusCode)
            assertEquals(40L, metric.durationMillis)
            assertTrue(metric.isSuccess)
            assertNull(metric.errorType)
        }

    @Test
    fun safePostPutAndDeleteSerializeBodiesAndHandleNoContent() =
        runTest {
            val recordedMethods = mutableListOf<HttpMethod>()
            val recordedBodies = mutableListOf<String>()

            val engine =
                MockEngine { request ->
                    recordedMethods.add(request.method)
                    val bodyText = (request.body as? TextContent)?.text.orEmpty()
                    recordedBodies.add(bodyText)

                    when (request.method) {
                        HttpMethod.Post, HttpMethod.Put -> {
                            respond(
                                content = """{"id":"u-1","name":"Grace"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }

                        HttpMethod.Delete -> {
                            respond(
                                content = "",
                                status = HttpStatusCode.NoContent,
                            )
                        }

                        else -> {
                            error("Unexpected method ${request.method}")
                        }
                    }
                }

            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = config, engine = engine),
                )

            val postResult =
                client.safePost<UserDto, CreateUserRequest>(
                    path = "/v1/users",
                    body = CreateUserRequest(name = "Grace"),
                )
            val putResult =
                client.safePut<UserDto, CreateUserRequest>(
                    path = "/v1/users/u-1",
                    body = CreateUserRequest(name = "Grace Hopper"),
                )
            val deleteResult = client.safeDelete<Unit>(path = "/v1/users/u-1")

            assertIs<AppResult.Success<UserDto>>(postResult)
            assertIs<AppResult.Success<UserDto>>(putResult)
            assertIs<AppResult.Success<Unit>>(deleteResult)

            assertEquals(listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete), recordedMethods)
            assertEquals("""{"name":"Grace"}""", recordedBodies[0])
            assertEquals("""{"name":"Grace Hopper"}""", recordedBodies[1])
        }

    @Test
    fun http401MapsToUnauthorizedErrorWhenNoRefreshTokenAvailable() =
        runTest {
            val engine =
                MockEngine {
                    respondError(HttpStatusCode.Unauthorized, "Expired token")
                }
            val metrics = mutableListOf<NetworkMetric>()
            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = config, engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val result = client.safeGet<UserDto>("/v1/me")

            val failure = assertIs<AppResult.Failure>(result)
            assertIs<UnauthorizedError>(failure.error)
            assertEquals(1, metrics.size)
            assertEquals(401, metrics.single().statusCode)
            assertFalse(metrics.single().isSuccess)
            assertEquals("UnauthorizedError", metrics.single().errorType)
        }

    @Test
    fun http401RetriesWithRefreshedTokenWhenAuthProviderRefreshes() =
        runTest {
            var attempts = 0
            val engine =
                MockEngine { request ->
                    attempts += 1
                    val auth = request.headers[HttpHeaders.Authorization]
                    if (auth == "Bearer fresh-token") {
                        respond(
                            content = """{"id":"u-1","name":"Refreshed"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respondError(HttpStatusCode.Unauthorized, "Stale token")
                    }
                }

            val authProvider =
                object : AuthTokenProvider {
                    override suspend fun accessToken(): String = "stale-token"

                    override suspend fun refreshToken(): String = "fresh-token"
                }

            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient =
                        HttpClientFactory.create(
                            config = config,
                            authTokenProvider = authProvider,
                            engine = engine,
                        ),
                )

            val result = client.safeGet<UserDto>("/v1/me")
            val success = assertIs<AppResult.Success<UserDto>>(result)
            assertEquals("Refreshed", success.value.name)
            assertEquals(2, attempts)
        }

    @Test
    fun httpStatusesMapToTypedErrorsAndEmitTelemetry() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/v1/missing" -> {
                            respondError(HttpStatusCode.NotFound, "Missing")
                        }

                        "/v1/limit" -> {
                            respond(
                                content = "Too fast",
                                status = HttpStatusCode.TooManyRequests,
                                headers = headersOf(HttpHeaders.RetryAfter, "12"),
                            )
                        }

                        else -> {
                            respondError(HttpStatusCode.InternalServerError, "Boom")
                        }
                    }
                }
            val metrics = mutableListOf<NetworkMetric>()
            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = config, engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val notFound = assertIs<AppResult.Failure>(client.safeGet<UserDto>("/v1/missing"))
            assertIs<NotFoundError>(notFound.error)

            val limited = assertIs<AppResult.Failure>(client.safeGet<UserDto>("/v1/limit"))
            val rateErr = assertIs<RateLimitError>(limited.error)
            assertEquals(12_000L, rateErr.retryAfterMillis)

            val serverErrResult = assertIs<AppResult.Failure>(client.safeGet<UserDto>("/v1/crash"))
            val serverErr = assertIs<NetworkError>(serverErrResult.error)
            assertEquals(500, serverErr.statusCode)

            assertEquals(3, metrics.size)
            assertEquals(404, metrics[0].statusCode)
            assertEquals(429, metrics[1].statusCode)
            assertEquals(500, metrics[2].statusCode)
        }

    @Test
    fun invalidJsonMapsToSerializationErrorAndEmitsTelemetry() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"unexpected":"schema"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val metrics = mutableListOf<NetworkMetric>()
            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = config, engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val result = client.safeGet<UserDto>("/v1/broken")
            val failure = assertIs<AppResult.Failure>(result)
            assertIs<SerializationError>(failure.error)

            assertEquals(1, metrics.size)
            assertFalse(metrics.single().isSuccess)
            assertEquals("SerializationError", metrics.single().errorType)
        }

    @Test
    fun transportExceptionMapsToNetworkErrorWithoutStatusCode() =
        runTest {
            val engine =
                MockEngine {
                    throw IllegalStateException("Connection reset by peer")
                }
            val metrics = mutableListOf<NetworkMetric>()
            val config =
                NetworkConfig(
                    baseUrl = "https://api.abeta.dev",
                    retry = RetryPolicy(maxRetries = 0),
                )
            val client =
                KtorHttpTransport(
                    httpClient = HttpClientFactory.create(config = config, engine = engine),
                    telemetrySink = NetworkTelemetrySink { metrics.add(it) },
                )

            val result = client.safeGet<UserDto>("/v1/timeout")
            val failure = assertIs<AppResult.Failure>(result)
            val error = assertIs<NetworkError>(failure.error)
            assertNull(error.statusCode)
            assertEquals("Connection reset by peer", error.message)

            assertEquals(1, metrics.size)
            assertNull(metrics.single().statusCode)
            assertFalse(metrics.single().isSuccess)
        }
}
