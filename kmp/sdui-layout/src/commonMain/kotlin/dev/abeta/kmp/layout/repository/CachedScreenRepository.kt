package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.resilience.SingleFlight
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cache retrieval policy applied by [CachedScreenRepository].
 */
enum class ScreenCachePolicy {
    /** Returns a fresh cached screen when valid, falling back to remote on miss or expiry. */
    CACHE_FIRST,

    /** Fetches from remote first, falling back to cached screen if the remote request fails. */
    NETWORK_FIRST,

    /** Serves cached screens immediately when present and revalidates stale entries. */
    STALE_WHILE_REVALIDATE,
}

/**
 * [ScreenRepository] decorator adding persistent TTL-aware caching and single-flight revalidation
 * backed by [KeyValueStore].
 */
@Suppress("LongParameterList")
class CachedScreenRepository(
    private val remote: ScreenRepository,
    private val store: KeyValueStore,
    private val clock: Clock = SystemClock(),
    private val policy: ScreenCachePolicy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val json: Json = SduiJson,
    private val backgroundScope: CoroutineScope? = null,
) : ScreenRepository {
    init {
        require(ttlMillis >= 0L) { "ttlMillis must be >= 0." }
    }

    private val storeMutex = Mutex()
    private val inFlight = SingleFlight<String, AppResult<PageResponse>>()

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val cleanId = normalizeScreenId(screenId)
        if (cleanId.isEmpty()) {
            return AppResult.Failure(
                ValidationError(
                    field = "screenId",
                    message = "Screen ID must not be blank.",
                ),
            )
        }
        return when (policy) {
            ScreenCachePolicy.CACHE_FIRST -> fetchCacheFirst(cleanId)
            ScreenCachePolicy.NETWORK_FIRST -> fetchNetworkFirst(cleanId)
            ScreenCachePolicy.STALE_WHILE_REVALIDATE -> fetchStaleWhileRevalidate(cleanId)
        }
    }

    /**
     * Forces a single-flight remote fetch for [screenId] and updates the persistent cache on success.
     */
    suspend fun revalidate(screenId: String): AppResult<PageResponse> {
        val cleanId = normalizeScreenId(screenId)
        if (cleanId.isEmpty()) {
            return AppResult.Failure(
                ValidationError(
                    field = "screenId",
                    message = "Screen ID must not be blank.",
                ),
            )
        }
        return inFlight.execute(cleanId) {
            val result = remote.fetchScreen(cleanId)
            if (result is AppResult.Success) {
                writeEntry(cleanId, result.value)
            }
            result
        }
    }

    /**
     * Clears the cached screen entry for [screenId], or all cached screens when [screenId] is `null`.
     */
    suspend fun clearCache(screenId: String? = null) {
        storeMutex.withLock {
            if (screenId != null) {
                val cleanId = normalizeScreenId(screenId)
                if (cleanId.isNotEmpty()) {
                    store.remove(cacheKey(cleanId))
                }
            } else {
                store
                    .keys()
                    .filter { it.startsWith(KEY_PREFIX) }
                    .forEach(store::remove)
            }
        }
    }

    private suspend fun fetchCacheFirst(cleanId: String): AppResult<PageResponse> {
        val cached = readEntry(cleanId)
        if (cached != null && isFresh(cached)) {
            return AppResult.Success(cached.page)
        }
        val refreshed = revalidate(cleanId)
        return if (refreshed is AppResult.Failure && cached != null) {
            AppResult.Success(cached.page)
        } else {
            refreshed
        }
    }

    private suspend fun fetchNetworkFirst(cleanId: String): AppResult<PageResponse> {
        val refreshed = revalidate(cleanId)
        if (refreshed is AppResult.Success) {
            return refreshed
        }
        val cached = readEntry(cleanId)
        return if (cached != null) AppResult.Success(cached.page) else refreshed
    }

    private suspend fun fetchStaleWhileRevalidate(cleanId: String): AppResult<PageResponse> {
        val cached = readEntry(cleanId)
        val scope = backgroundScope
        return when {
            cached == null -> {
                revalidate(cleanId)
            }

            isFresh(cached) -> {
                AppResult.Success(cached.page)
            }

            scope != null -> {
                scope.launch { revalidate(cleanId) }
                AppResult.Success(cached.page)
            }

            else -> {
                val refreshed = revalidate(cleanId)
                if (refreshed is AppResult.Success) refreshed else AppResult.Success(cached.page)
            }
        }
    }

    private fun isFresh(entry: CachedScreenEntry): Boolean {
        val ageMillis = (clock.nowEpochMillis() - entry.cachedAtEpochMillis).coerceAtLeast(0L)
        return ageMillis < ttlMillis
    }

    private suspend fun readEntry(cleanId: String): CachedScreenEntry? =
        storeMutex.withLock {
            val raw = store.getString(cacheKey(cleanId)) ?: return@withLock null
            runCatching { json.decodeFromString(CachedScreenEntry.serializer(), raw) }.getOrNull()
        }

    private suspend fun writeEntry(
        cleanId: String,
        page: PageResponse,
    ) {
        val entry =
            CachedScreenEntry(
                cachedAtEpochMillis = clock.nowEpochMillis(),
                page = page,
            )
        val encoded = json.encodeToString(CachedScreenEntry.serializer(), entry)
        storeMutex.withLock {
            store.putString(cacheKey(cleanId), encoded)
        }
    }

    private fun cacheKey(cleanId: String): String = "$KEY_PREFIX$cleanId"

    private fun normalizeScreenId(screenId: String): String = screenId.trim().removePrefix("/")

    @Serializable
    private data class CachedScreenEntry(
        val cachedAtEpochMillis: Long,
        val page: PageResponse,
    )

    companion object {
        /** Default cache time-to-live: 5 minutes. */
        const val DEFAULT_TTL_MILLIS: Long = 300_000L
        private const val KEY_PREFIX: String = "abeta_screen_cache:"
    }
}
