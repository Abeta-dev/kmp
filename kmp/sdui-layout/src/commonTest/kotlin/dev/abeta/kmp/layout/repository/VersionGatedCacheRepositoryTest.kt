package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ScreenManifestResponse
import dev.abeta.kmp.contracts.ScreenVersionMeta
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VersionGatedCacheRepositoryTest {
    @Test
    fun versionGatedWithMatchingManifestServesCacheWithZeroScreenFetches() =
        runTest {
            val clock = MutableClock(1_000L)
            var screenFetches = 0
            var manifestFetches = 0
            val remote =
                conditionalRemote { screenId, _ ->
                    screenFetches++
                    modifiedResult(
                        screenId = screenId,
                        title = "Home V1",
                        pageVersion = "v1",
                        appVersion = "1.0.0",
                        etag = "\"h1\"",
                    )
                }
            val manifestRepo =
                ScreenManifestRepository {
                    manifestFetches++
                    manifestSuccess("1.0.0", "home" to ScreenVersionMeta(version = "v1", etag = "\"h1\""))
                }
            val repo =
                CachedScreenRepository(
                    remote = remote,
                    store = InMemoryKeyValueStore(),
                    clock = clock,
                    policy = ScreenCachePolicy.VERSION_GATED,
                    appVersion = "1.0.0",
                    manifestRepository = manifestRepo,
                    manifestTtlMillis = 30_000L,
                )

            assertEquals("Home V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)
            assertEquals(1, screenFetches)
            assertEquals(1, manifestFetches)

            clock.advanceByMillis(10_000L)
            assertEquals("Home V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)
            assertEquals(1, screenFetches)
            assertEquals(1, manifestFetches)

            clock.advanceByMillis(25_000L)
            assertEquals("Home V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)
            assertEquals(1, screenFetches)
            assertEquals(2, manifestFetches)
        }

    @Test
    fun versionGatedWithChangedManifestSendsIfNoneMatchAndHandles304And200() =
        runTest {
            val clock = MutableClock(1_000L)
            var manifestVersion = "v1"
            var manifestEtag = "\"e1\""
            var mode = "v1"
            val capturedValidators = mutableListOf<String?>()

            val remote =
                conditionalRemote { screenId, ifNoneMatch ->
                    capturedValidators += ifNoneMatch
                    when (mode) {
                        "v1" -> modifiedResult(screenId, title = "Home V1", pageVersion = "v1", etag = "\"e1\"")
                        "not_modified" -> AppResult.Success(ConditionalScreenFetchResult.NotModified(etag = "\"e2\""))
                        else -> modifiedResult(screenId, title = "Home V3", pageVersion = "v3", etag = "\"e3\"")
                    }
                }
            val manifestRepo =
                ScreenManifestRepository {
                    manifestSuccess(
                        appVersion = "1.0.0",
                        "home" to ScreenVersionMeta(version = manifestVersion, etag = manifestEtag),
                    )
                }
            val repo =
                CachedScreenRepository(
                    remote = remote,
                    store = InMemoryKeyValueStore(),
                    clock = clock,
                    policy = ScreenCachePolicy.VERSION_GATED,
                    appVersion = "1.0.0",
                    manifestRepository = manifestRepo,
                    manifestTtlMillis = 10_000L,
                )

            assertEquals("Home V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)
            assertEquals(listOf<String?>(null), capturedValidators)

            manifestVersion = "v2"
            manifestEtag = "\"e2\""
            mode = "not_modified"
            assertIs<AppResult.Success<ScreenManifestResponse>>(repo.refreshManifest())
            assertEquals("Home V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)
            assertEquals(listOf(null, "\"e1\""), capturedValidators)

            manifestVersion = "v3"
            manifestEtag = "\"e3\""
            mode = "v3"
            assertIs<AppResult.Success<ScreenManifestResponse>>(repo.refreshManifest())
            val after200 = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Home V3", after200.title)
            assertEquals("v3", after200.pageVersion)
            assertEquals(listOf(null, "\"e1\"", "\"e2\""), capturedValidators)
        }

    @Test
    fun versionGatedWithoutManifestFallsBackToPerScreenIfNoneMatch304AndOfflineFallback() =
        runTest {
            val clock = MutableClock(5_000L)
            var callCount = 0
            val receivedIfNoneMatch = mutableListOf<String?>()
            var failRemote = false
            val engine =
                createConditionalMockEngine(
                    shouldFail = { failRemote },
                    onRequest = { validator ->
                        callCount++
                        receivedIfNoneMatch += validator
                    },
                )
            val repo =
                CachedScreenRepository(
                    remote = RemoteScreenRepository(createTransport(engine)),
                    store = InMemoryKeyValueStore(),
                    clock = clock,
                    policy = ScreenCachePolicy.VERSION_GATED,
                    manifestRepository = null,
                )

            val first = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Remote Home", first.title)
            assertEquals("\"etag-home-1\"", first.etag)

            clock.advanceByMillis(2_000L)
            val second = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Remote Home", second.title)
            assertEquals(listOf(null, "\"etag-home-1\""), receivedIfNoneMatch)
            assertEquals(2, callCount)

            failRemote = true
            val offlineFallback = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Remote Home", offlineFallback.title)
        }

    @Test
    fun appVersionChangeBustsStaleCacheAcrossPolicies() =
        runTest {
            val clock = MutableClock(1_000L)
            val store = InMemoryKeyValueStore()
            val validators = mutableListOf<String?>()
            var remoteTitle = "App 1.0 Home"
            var shouldFail = false
            val remote =
                conditionalRemote { screenId, ifNoneMatch ->
                    validators += ifNoneMatch
                    if (shouldFail) {
                        AppResult.Failure(OfflineError("Offline"))
                    } else {
                        modifiedResult(screenId, title = remoteTitle, etag = "\"v-etag\"")
                    }
                }

            val v1Repo = repoWithVersion(remote, store, clock, ScreenCachePolicy.VERSION_GATED, "1.0.0")
            val v1Page = assertIs<AppResult.Success<PageResponse>>(v1Repo.fetchScreen("home")).value
            assertEquals("App 1.0 Home", v1Page.title)

            remoteTitle = "App 2.0 Home"
            val v2Repo = repoWithVersion(remote, store, clock, ScreenCachePolicy.VERSION_GATED, "2.0.0")
            val v2Page = assertIs<AppResult.Success<PageResponse>>(v2Repo.fetchScreen("home")).value
            assertEquals("App 2.0 Home", v2Page.title)
            assertEquals(listOf<String?>(null, null), validators)

            remoteTitle = "App 3.0 Home"
            val v3CacheFirst = repoWithVersion(remote, store, clock, ScreenCachePolicy.CACHE_FIRST, "3.0.0")
            val upgraded = assertIs<AppResult.Success<PageResponse>>(v3CacheFirst.fetchScreen("home")).value
            assertEquals("App 3.0 Home", upgraded.title)
            assertNull(validators.last())

            shouldFail = true
            val v4Offline = repoWithVersion(remote, store, clock, ScreenCachePolicy.STALE_WHILE_REVALIDATE, "4.0.0")
            assertIs<OfflineError>(assertIs<AppResult.Failure>(v4Offline.fetchScreen("home")).error)
        }

    @Test
    fun remoteScreenManifestRepositoryDecodesBffEnvelopeAndMapsErrors() =
        runTest {
            val transport = createTransport(createManifestMockEngine())
            val manifestRepo = RemoteScreenManifestRepository(httpClient = transport)
            val manifest = assertIs<AppResult.Success<ScreenManifestResponse>>(manifestRepo.fetchManifest()).value
            assertEquals("0.5.0", manifest.appVersion)
            assertEquals(1_700_000_000_000L, manifest.generatedAtEpochMs)
            assertEquals("v4", manifest.screens["home"]?.version)
            assertEquals("\"sha256-home\"", manifest.screens["home"]?.etag)
            assertEquals("v2", manifest.screens["store-home"]?.version)

            val missingRepo = RemoteScreenManifestRepository(httpClient = transport, manifestPath = "/api/v1/missing")
            val err = assertIs<NotFoundError>(assertIs<AppResult.Failure>(missingRepo.fetchManifest()).error)
            assertEquals("manifest missing", err.message)
        }

    private fun repoWithVersion(
        remote: ConditionalScreenRepository,
        store: InMemoryKeyValueStore,
        clock: MutableClock,
        policy: ScreenCachePolicy,
        version: String,
    ): CachedScreenRepository =
        CachedScreenRepository(
            remote = remote,
            store = store,
            clock = clock,
            policy = policy,
            ttlMillis = 300_000L,
            appVersion = version,
        )

    private fun conditionalRemote(
        handler: suspend (String, String?) -> AppResult<ConditionalScreenFetchResult>,
    ): ConditionalScreenRepository =
        object : ConditionalScreenRepository {
            override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
                error("Should invoke fetchScreenConditional")
            }

            override suspend fun fetchScreenConditional(
                screenId: String,
                ifNoneMatch: String?,
            ): AppResult<ConditionalScreenFetchResult> = handler(screenId, ifNoneMatch)
        }

    private fun modifiedResult(
        screenId: String,
        title: String,
        pageVersion: String? = null,
        appVersion: String? = null,
        etag: String? = null,
    ): AppResult<ConditionalScreenFetchResult> =
        AppResult.Success(
            ConditionalScreenFetchResult.Modified(
                page =
                    PageResponse(
                        pageId = screenId,
                        title = title,
                        pageVersion = pageVersion,
                        appVersion = appVersion,
                        etag = etag,
                    ),
                etag = etag,
            ),
        )

    private fun manifestSuccess(
        appVersion: String,
        vararg entries: Pair<String, ScreenVersionMeta>,
    ): AppResult<ScreenManifestResponse> {
        val manifest = ScreenManifestResponse(appVersion = appVersion, screens = mapOf(*entries))
        return AppResult.Success(manifest)
    }

    private fun createConditionalMockEngine(
        shouldFail: () -> Boolean,
        onRequest: (String?) -> Unit,
    ): MockEngine =
        MockEngine { request ->
            val validator = request.headers[HttpHeaders.IfNoneMatch]
            onRequest(validator)
            when {
                shouldFail() -> {
                    respond(content = "Service Unavailable", status = HttpStatusCode.ServiceUnavailable)
                }

                validator == "\"etag-home-1\"" -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag, "\"etag-home-1\""),
                    )
                }

                else -> {
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": { "page_id": "home", "title": "Remote Home", "page_version": "v1" }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ETag to listOf("\"etag-home-1\""),
                            ),
                    )
                }
            }
        }

    private fun createManifestMockEngine(): MockEngine =
        MockEngine { request ->
            if (request.url.encodedPath == RemoteScreenManifestRepository.DEFAULT_MANIFEST_PATH) {
                respond(
                    content =
                        """
                        {
                          "success": true,
                          "data": {
                            "app_version": "0.5.0",
                            "generated_at_epoch_ms": 1700000000000,
                            "screens": {
                              "home": {
                                "version": "v4",
                                "etag": "\"sha256-home\"",
                                "updated_at_epoch_ms": 1700000000000
                              },
                              "store-home": { "version": "v2", "etag": "\"sha256-store\"" }
                            }
                          }
                        }
                        """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    content =
                        """
                        {
                          "success": false,
                          "error": { "code": "NOT_FOUND", "message": "manifest missing" }
                        }
                        """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }

    private fun createTransport(engine: MockEngine): KtorHttpTransport {
        val ktor =
            HttpClientFactory.create(
                config = NetworkConfig(baseUrl = "https://bff.abeta.dev", retry = RetryPolicy(maxRetries = 0)),
                engine = engine,
            )
        return KtorHttpTransport(httpClient = ktor)
    }
}
