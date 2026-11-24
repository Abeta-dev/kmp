package dev.abeta.kmp.layout.di

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionRegistry
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.platform.ClipboardPort
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.security.BiometricAuthenticator
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.engine.OfflineActionCoordinator
import dev.abeta.kmp.layout.media.KtorImageRenderer
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.CachedScreenRepository
import dev.abeta.kmp.layout.repository.RemoteScreenManifestRepository
import dev.abeta.kmp.layout.repository.ScreenCachePolicy
import dev.abeta.kmp.layout.repository.ScreenManifestRepository
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.network.ports.CsrfTokenProvider
import dev.abeta.kmp.network.sse.SseTransport
import dev.abeta.kmp.state.flags.FeatureFlagProvider
import dev.abeta.kmp.state.preferences.PreferenceStore
import dev.abeta.kmp.state.session.SessionStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SduiKoinModuleTest {
    @Test
    fun resolvesEntireDependencyGraphAndLoadsScreenViaLayoutController() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/screens/home" -> {
                            respond(
                                content =
                                    """
                                    {
                                      "success": true,
                                      "data": {
                                        "page_id": "home",
                                        "title": "Koin Home",
                                        "snippets": []
                                      }
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }

                        else -> {
                            respond(content = "Not Found", status = HttpStatusCode.NotFound)
                        }
                    }
                }

            val app =
                koinApplication {
                    modules(
                        createSduiKoinModule(
                            baseUrl = "https://bff.abeta.dev",
                            scope = this@runTest,
                            httpEngine = mockEngine,
                            keyValueStore = InMemoryKeyValueStore(),
                            platformBridges = PlatformBridgeBundle.createTestDefault(),
                        ),
                    )
                }

            try {
                val koin = app.koin
                assertCoreBindings(koin)

                val controller = koin.get<LayoutController>()
                controller.loadScreen("home").join()
                val ready = assertIs<LayoutUiState.Ready>(controller.uiState.value)
                assertEquals("home", ready.page.pageId)
                assertEquals("Koin Home", ready.page.title)
            } finally {
                app.close()
            }
        }

    private fun assertCoreBindings(koin: org.koin.core.Koin) {
        assertNotNull(koin.get<Clock>())
        assertNotNull(koin.get<UuidGenerator>())
        assertNotNull(koin.get<KeyValueStore>())
        assertNotNull(koin.get<SessionStore>())
        assertNotNull(koin.get<PreferenceStore>())
        assertNotNull(koin.get<FeatureFlagProvider>())
        assertNotNull(koin.get<OfflineMutationQueue>())
        assertNotNull(koin.get<AnalyticsTracker>())
        assertNotNull(koin.get<HttpTransport>())
        assertNotNull(koin.get<SseTransport>())
        assertNull(koin.getOrNull<ScreenManifestRepository>())
        assertIs<CachedScreenRepository>(koin.get<ScreenRepository>())
        assertNotNull(koin.get<ApiCallExecutor>())
        assertEquals(21, koin.get<SnippetRegistry>().registeredTypes().size)
        assertNotNull(koin.get<ActionDecoderRegistry>())
        assertNotNull(koin.get<ActionRegistry>())
        assertNotNull(koin.get<ActionDispatcher>())
        assertNotNull(koin.get<OfflineActionCoordinator>())
        assertIs<KtorImageRenderer>(koin.get<UiImageRenderer>())
        assertNotNull(koin.get<PlatformBridgeBundle>())
        assertNotNull(koin.get<PlatformCapabilities>())
        assertNotNull(koin.get<HapticEngine>())
        assertNotNull(koin.get<ClipboardPort>())
        assertNotNull(koin.get<ExternalLauncherPort>())
        assertNotNull(koin.get<SharePort>())
        assertNotNull(koin.get<ConnectivityMonitor>())
        assertNotNull(koin.get<SecureKeyValueStore>())
        assertNotNull(koin.get<BiometricAuthenticator>())
    }

    @Test
    fun wiresCsrfManifestVersionGatedCacheAndSseTransport() =
        runTest {
            var observedCsrfHeader: String? = null
            var screenCalls = 0
            val mockEngine =
                createVersionGatedMockEngine(
                    onScreenFetch = { screenCalls++ },
                    onMutate = { observedCsrfHeader = it },
                )
            val csrfProvider =
                object : CsrfTokenProvider {
                    override suspend fun currentToken(): String = "csrf-secret-123"
                }

            val app =
                koinApplication {
                    modules(
                        createSduiKoinModule(
                            baseUrl = "https://bff.abeta.dev",
                            scope = this@runTest,
                            httpEngine = mockEngine,
                            keyValueStore = InMemoryKeyValueStore(),
                            cachePolicy = ScreenCachePolicy.VERSION_GATED,
                            platformBridges = PlatformBridgeBundle.createTestDefault(),
                            csrfTokenProvider = csrfProvider,
                            appVersion = "0.5.0",
                            manifestPath = RemoteScreenManifestRepository.DEFAULT_MANIFEST_PATH,
                        ),
                    )
                }

            try {
                val koin = app.koin
                assertNotNull(koin.get<ScreenManifestRepository>())
                assertNotNull(koin.get<SseTransport>())

                koin.get<HttpTransport>().request(
                    method = HttpMethodType.POST,
                    path = "/api/v1/mutate",
                    deserializer = JsonElement.serializer(),
                )
                assertEquals("csrf-secret-123", observedCsrfHeader)

                val repo = koin.get<ScreenRepository>()
                repo.fetchScreen("home")
                repo.fetchScreen("home")
                assertEquals(1, screenCalls)
            } finally {
                app.close()
            }
        }

    private fun createVersionGatedMockEngine(
        onScreenFetch: () -> Unit,
        onMutate: (String?) -> Unit,
    ): MockEngine =
        MockEngine { request ->
            when (request.url.encodedPath) {
                RemoteScreenManifestRepository.DEFAULT_MANIFEST_PATH -> {
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "app_version": "0.5.0",
                                "screens": {
                                  "home": { "version": "v1", "etag": "\"home-v1\"" }
                                }
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                "/api/v1/screens/home" -> {
                    onScreenFetch()
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "page_id": "home",
                                "title": "Manifest Home",
                                "page_version": "v1",
                                "snippets": []
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ETag to listOf("\"home-v1\""),
                            ),
                    )
                }

                "/api/v1/mutate" -> {
                    onMutate(request.headers[CsrfTokenProvider.DEFAULT_CSRF_HEADER])
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> {
                    respond(content = "Not Found", status = HttpStatusCode.NotFound)
                }
            }
        }
}
