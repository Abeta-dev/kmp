package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteScreenRepositoryTest {
    @Test
    fun fetchScreenUnwrapsSuccessBffEnvelope() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/screens/home", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "page_id": "home",
                                "title": "Abeta Workspace",
                                "snippets": [
                                  {
                                    "snippet_id": "hdr_1",
                                    "snippet_type": "header_v1",
                                    "data": {
                                      "title": { "text": "Welcome" }
                                    }
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val repo = RemoteScreenRepository(createClient(engine))
            val result = repo.fetchScreen("home")
            val page = assertIs<AppResult.Success<PageResponse>>(result).value
            assertEquals("home", page.pageId)
            assertEquals("Abeta Workspace", page.title)
            assertEquals(1, page.snippets.size)
            assertEquals("hdr_1", page.snippets.single().snippetId)
        }

    @Test
    fun fetchScreenMapsBffErrorEnvelopeToTypedAppError() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "success": false,
                              "error": {
                                "code": "NOT_FOUND",
                                "message": "screen not found: unknown"
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val repo = RemoteScreenRepository(createClient(engine))
            val result = repo.fetchScreen("unknown")
            val failure = assertIs<AppResult.Failure>(result)
            val err = assertIs<NotFoundError>(failure.error)
            assertEquals("unknown", err.resource)
            assertEquals("screen not found: unknown", err.message)
        }

    @Test
    fun fetchScreenMapsHttpFailureAndBlankId() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "Internal Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val repo = RemoteScreenRepository(createClient(engine))

            val blankResult = repo.fetchScreen("   ")
            assertIs<ValidationError>(assertIs<AppResult.Failure>(blankResult).error)

            val httpResult = repo.fetchScreen("home")
            val netErr = assertIs<NetworkError>(assertIs<AppResult.Failure>(httpResult).error)
            assertEquals(500, netErr.statusCode)
        }

    @Test
    fun remoteApiCallExecutorHandlesSuccessEnvelope() =
        runTest {
            val engine = createActionMockEngine()
            val executor = RemoteApiCallExecutor(createClient(engine))
            val okResult =
                executor.execute(
                    payload =
                        CallApiPayload(
                            endpoint = "/api/v1/actions/execute",
                            method = "POST",
                            body = buildJsonObject { put("form_id", "workspace") },
                        ),
                    context = ActionContext(),
                )
            val okOutcome = assertIs<AppResult.Success<ActionOutcome.ApiCallResult>>(okResult).value
            assertTrue(okOutcome.isSuccess)
            assertEquals(1, okOutcome.followUpActions.size)
            assertEquals(ActionTypes.SHOW_TOAST, okOutcome.followUpActions.single().type)
        }

    @Test
    fun remoteApiCallExecutorHandlesFailureEnvelope() =
        runTest {
            val engine = createActionMockEngine()
            val executor = RemoteApiCallExecutor(createClient(engine))
            val failResult =
                executor.execute(
                    payload =
                        CallApiPayload(
                            endpoint = "/api/v1/actions/fail",
                            method = "POST",
                            onError =
                                ActionRef(
                                    type = ActionTypes.SHOW_TOAST,
                                    payload = buildJsonObject { put("message", "Fallback") },
                                ),
                        ),
                    context = ActionContext(),
                )
            val failOutcome = assertIs<AppResult.Success<ActionOutcome.ApiCallResult>>(failResult).value
            assertFalse(failOutcome.isSuccess)
            assertEquals("Invalid input", failOutcome.errorMessage)
        }

    private fun createActionMockEngine(): MockEngine =
        MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/actions/execute" -> {
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "action_id": "act_1",
                                "status": "executed",
                                "follow_up_actions": [
                                  {
                                    "id": "toast_1",
                                    "type": "show_toast",
                                    "payload": { "message": "Saved!" }
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> {
                    respond(
                        content =
                            """
                            {
                              "success": false,
                              "error": {
                                "code": "BAD_REQUEST",
                                "message": "Invalid input"
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }

    private fun createClient(engine: MockEngine): KtorHttpTransport {
        val ktor =
            HttpClientFactory.create(
                config =
                    NetworkConfig(
                        baseUrl = "https://bff.abeta.dev",
                        retry = RetryPolicy(maxRetries = 0),
                    ),
                engine = engine,
            )
        return KtorHttpTransport(httpClient = ktor)
    }
}
