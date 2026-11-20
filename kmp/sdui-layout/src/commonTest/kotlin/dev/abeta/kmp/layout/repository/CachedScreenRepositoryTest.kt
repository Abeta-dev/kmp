package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CachedScreenRepositoryTest {
    @Test
    fun staleWhileRevalidateServesFreshCacheThenRefreshesAfterTtlExpiry() =
        runTest {
            val clock = MutableClock(1_000L)
            val store = InMemoryKeyValueStore()
            var remoteCalls = 0
            var nextTitle = "Version 1"
            val remote =
                ScreenRepository { screenId ->
                    remoteCalls++
                    AppResult.Success(PageResponse(pageId = screenId, title = nextTitle))
                }
            val repo =
                CachedScreenRepository(
                    remote = remote,
                    store = store,
                    clock = clock,
                    policy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
                    ttlMillis = 10_000L,
                )

            val first = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Version 1", first.title)
            assertEquals(1, remoteCalls)

            clock.advanceByMillis(4_000L)
            nextTitle = "Version 2"
            val freshHit = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Version 1", freshHit.title)
            assertEquals(1, remoteCalls)

            clock.advanceByMillis(6_000L)
            val expiredRefresh = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Version 2", expiredRefresh.title)
            assertEquals(2, remoteCalls)
        }

    @Test
    fun staleWhileRevalidateWithBackgroundScopeReturnsStaleImmediatelyAndUpdatesCache() =
        runTest {
            val clock = MutableClock(1_000L)
            val store = InMemoryKeyValueStore()
            var title = "Stale V1"
            val remote =
                ScreenRepository { screenId ->
                    AppResult.Success(PageResponse(pageId = screenId, title = title))
                }
            val repo =
                CachedScreenRepository(
                    remote = remote,
                    store = store,
                    clock = clock,
                    policy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
                    ttlMillis = 5_000L,
                    backgroundScope = this,
                )

            assertEquals("Stale V1", assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value.title)

            clock.advanceByMillis(6_000L)
            title = "Fresh V2"
            val immediate = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Stale V1", immediate.title)

            advanceUntilIdle()
            val updated = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("home")).value
            assertEquals("Fresh V2", updated.title)
        }

    @Test
    fun fallsBackToCachedPageOnRemoteErrorAcrossPolicies() =
        runTest {
            val clock = MutableClock(1_000L)
            val store = InMemoryKeyValueStore()
            var shouldFail = false
            val remote =
                ScreenRepository { screenId ->
                    if (shouldFail) {
                        AppResult.Failure(OfflineError("No internet"))
                    } else {
                        AppResult.Success(PageResponse(pageId = screenId, title = "Cached Home"))
                    }
                }
            val swrRepo =
                CachedScreenRepository(
                    remote = remote,
                    store = store,
                    clock = clock,
                    policy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
                    ttlMillis = 5_000L,
                )

            assertIs<AppResult.Success<PageResponse>>(swrRepo.fetchScreen("home"))
            clock.advanceByMillis(10_000L)
            shouldFail = true

            val swrFallback = assertIs<AppResult.Success<PageResponse>>(swrRepo.fetchScreen("home")).value
            assertEquals("Cached Home", swrFallback.title)

            val networkFirstRepo =
                CachedScreenRepository(
                    remote = remote,
                    store = store,
                    clock = clock,
                    policy = ScreenCachePolicy.NETWORK_FIRST,
                    ttlMillis = 5_000L,
                )
            val nfFallback = assertIs<AppResult.Success<PageResponse>>(networkFirstRepo.fetchScreen("home")).value
            assertEquals("Cached Home", nfFallback.title)

            val coldStartFailure = networkFirstRepo.fetchScreen("missing")
            assertIs<OfflineError>(assertIs<AppResult.Failure>(coldStartFailure).error)
        }

    @Test
    fun cacheFirstRevalidateClearCacheAndBlankScreenIdBehavePredictably() =
        runTest {
            val clock = MutableClock(0L)
            val store = InMemoryKeyValueStore()
            var calls = 0
            val remote =
                ScreenRepository { screenId ->
                    calls++
                    if (screenId == "broken") {
                        AppResult.Failure(NetworkError(statusCode = 500, message = "Server error"))
                    } else {
                        AppResult.Success(PageResponse(pageId = screenId, title = "Call $calls"))
                    }
                }
            val repo =
                CachedScreenRepository(
                    remote = remote,
                    store = store,
                    clock = clock,
                    policy = ScreenCachePolicy.CACHE_FIRST,
                    ttlMillis = 20_000L,
                )

            assertIs<ValidationError>(assertIs<AppResult.Failure>(repo.fetchScreen("  ")).error)
            assertIs<ValidationError>(assertIs<AppResult.Failure>(repo.revalidate("")).error)

            val first = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("catalog")).value
            assertEquals("Call 1", first.title)

            val explicitRevalidated = assertIs<AppResult.Success<PageResponse>>(repo.revalidate("catalog")).value
            assertEquals("Call 2", explicitRevalidated.title)

            repo.clearCache("catalog")
            val afterClear = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("catalog")).value
            assertEquals("Call 3", afterClear.title)

            assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("profile"))
            repo.clearCache()
            assertEquals(0, store.keys().size)
        }
}
