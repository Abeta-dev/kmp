package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ScreenManifestResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.SerializationError
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

    /** Validates cached screens via BFF `/manifest` handshake and conditional `ETag` (`304 Not Modified`). */
    VERSION_GATED,
}

/**
 * [ScreenRepository] decorator adding persistent TTL-aware caching, app-version busting,
 * manifest-gated validation, conditional `If-None-Match` (`304 Not Modified`) revalidation,
 * and single-flight deduplication backed by [KeyValueStore].
 */
@Suppress("LongParameterList", "TooManyFunctions")
class CachedScreenRepository(
    private val remote: ScreenRepository,
    private val store: KeyValueStore,
    private val clock: Clock = SystemClock(),
    private val policy: ScreenCachePolicy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val json: Json = SduiJson,
    private val backgroundScope: CoroutineScope? = null,
    private val appVersion: String? = null,
    private val manifestRepository: ScreenManifestRepository? = null,
    private val manifestTtlMillis: Long = DEFAULT_MANIFEST_TTL_MILLIS,
) : ScreenRepository {
    init {
        require(ttlMillis >= 0L) { "ttlMillis must be >= 0." }
        require(manifestTtlMillis >= 0L) { "manifestTtlMillis must be >= 0." }
    }

    private val storeMutex = Mutex()
    private val manifestMutex = Mutex()
    private val inFlight = SingleFlight<String, AppResult<PageResponse>>()
    private val manifestInFlight = SingleFlight<String, AppResult<ScreenManifestResponse>>()
    private var cachedManifest: CachedManifest? = null

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
            ScreenCachePolicy.VERSION_GATED -> fetchVersionGated(cleanId)
        }
    }

    /**
     * Forces a single-flight refresh of the remote BFF screen manifest, updating the cached manifest
     * on success. Returns `null` when no [manifestRepository] is configured.
     */
    suspend fun refreshManifest(): AppResult<ScreenManifestResponse>? {
        val repo = manifestRepository ?: return null
        return manifestInFlight.execute(MANIFEST_FLIGHT_KEY) {
            val result = repo.fetchManifest()
            if (result is AppResult.Success) {
                manifestMutex.withLock {
                    cachedManifest =
                        CachedManifest(
                            fetchedAtEpochMillis = clock.nowEpochMillis(),
                            response = result.value,
                        )
                }
            }
            result
        }
    }

    /**
     * Forces a single-flight remote fetch for [screenId] (using `If-None-Match` when [remote]
     * implements [ConditionalScreenRepository] and a cached ETag is present) and updates the
     * persistent cache on success.
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
            executeRemoteRevalidation(cleanId)
        }
    }

    /**
     * Clears the cached screen entry for [screenId], or all cached screens and manifest metadata
     * when [screenId] is `null`.
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
        if (screenId == null) {
            manifestMutex.withLock {
                cachedManifest = null
            }
        }
    }

    private suspend fun executeRemoteRevalidation(cleanId: String): AppResult<PageResponse> {
        val existingCached = readValidEntry(cleanId)
        val conditionalRemote = remote as? ConditionalScreenRepository
        return if (conditionalRemote != null) {
            executeConditionalRevalidation(conditionalRemote, cleanId, existingCached)
        } else {
            val result = remote.fetchScreen(cleanId)
            if (result is AppResult.Success) {
                writeEntry(
                    cleanId = cleanId,
                    page = result.value,
                    etag = result.value.etag,
                    pageVersion = result.value.pageVersion,
                    entryAppVersion = appVersion ?: result.value.appVersion,
                )
            }
            result
        }
    }

    private suspend fun executeConditionalRevalidation(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
        existingCached: CachedScreenEntry?,
    ): AppResult<PageResponse> {
        val validator =
            existingCached?.let { cached ->
                cached.etag?.takeIf { it.isNotBlank() } ?: cached.page.etag?.takeIf { it.isNotBlank() }
            }
        return when (val result = conditionalRemote.fetchScreenConditional(cleanId, ifNoneMatch = validator)) {
            is AppResult.Failure -> result
            is AppResult.Success -> handleConditionalSuccess(conditionalRemote, cleanId, existingCached, result.value)
        }
    }

    private suspend fun handleConditionalSuccess(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
        existingCached: CachedScreenEntry?,
        conditional: ConditionalScreenFetchResult,
    ): AppResult<PageResponse> =
        when (conditional) {
            is ConditionalScreenFetchResult.Modified -> {
                val page = conditional.page
                val resolvedEtag = conditional.etag?.takeIf { it.isNotBlank() } ?: page.etag
                writeEntry(
                    cleanId = cleanId,
                    page = page,
                    etag = resolvedEtag,
                    pageVersion = page.pageVersion,
                    entryAppVersion = appVersion ?: page.appVersion,
                )
                AppResult.Success(page)
            }

            is ConditionalScreenFetchResult.NotModified -> {
                if (existingCached != null) {
                    val refreshedEtag = conditional.etag?.takeIf { it.isNotBlank() } ?: existingCached.etag
                    writeEntry(
                        cleanId = cleanId,
                        page = existingCached.page,
                        etag = refreshedEtag,
                        pageVersion = existingCached.pageVersion ?: existingCached.page.pageVersion,
                        entryAppVersion = appVersion ?: existingCached.appVersion ?: existingCached.page.appVersion,
                    )
                    AppResult.Success(existingCached.page)
                } else {
                    fetchUnconditionalFallback(conditionalRemote, cleanId)
                }
            }
        }

    private suspend fun fetchUnconditionalFallback(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
    ): AppResult<PageResponse> =
        when (val fallback = conditionalRemote.fetchScreenConditional(cleanId, ifNoneMatch = null)) {
            is AppResult.Failure -> {
                fallback
            }

            is AppResult.Success -> {
                when (val value = fallback.value) {
                    is ConditionalScreenFetchResult.Modified -> {
                        val page = value.page
                        writeEntry(
                            cleanId = cleanId,
                            page = page,
                            etag = value.etag?.takeIf { it.isNotBlank() } ?: page.etag,
                            pageVersion = page.pageVersion,
                            entryAppVersion = appVersion ?: page.appVersion,
                        )
                        AppResult.Success(page)
                    }

                    is ConditionalScreenFetchResult.NotModified -> {
                        AppResult.Failure(
                            SerializationError(
                                message = "Unexpected 304 Not Modified without cached screen for '$cleanId'.",
                            ),
                        )
                    }
                }
            }
        }

    private suspend fun fetchCacheFirst(cleanId: String): AppResult<PageResponse> {
        val cached = readValidEntry(cleanId)
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
        val cached = readValidEntry(cleanId)
        return if (cached != null) AppResult.Success(cached.page) else refreshed
    }

    private suspend fun fetchStaleWhileRevalidate(cleanId: String): AppResult<PageResponse> {
        val cached = readValidEntry(cleanId)
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

    private suspend fun fetchVersionGated(cleanId: String): AppResult<PageResponse> {
        val cached = readValidEntry(cleanId)
        val manifest = (getOrFetchManifest() as? AppResult.Success)?.value
        val canServeDirectly = cached != null && manifest != null && matchesManifest(cached, manifest, cleanId)
        return if (canServeDirectly) {
            AppResult.Success(cached.page)
        } else {
            val refreshed = revalidate(cleanId)
            if (refreshed is AppResult.Failure && cached != null) {
                AppResult.Success(cached.page)
            } else {
                refreshed
            }
        }
    }

    private suspend fun getOrFetchManifest(): AppResult<ScreenManifestResponse>? {
        if (manifestRepository == null) return null
        val existing = manifestMutex.withLock { cachedManifest }
        return if (existing != null && isManifestFresh(existing)) {
            AppResult.Success(existing.response)
        } else {
            refreshManifest()
        }
    }

    private fun isManifestFresh(entry: CachedManifest): Boolean {
        val ageMillis = (clock.nowEpochMillis() - entry.fetchedAtEpochMillis).coerceAtLeast(0L)
        return ageMillis < manifestTtlMillis
    }

    private fun matchesManifest(
        cached: CachedScreenEntry,
        manifest: ScreenManifestResponse,
        cleanId: String,
    ): Boolean {
        val meta = manifest.screens[cleanId]
        val manifestAppVersion = manifest.appVersion.takeIf { it.isNotBlank() }
        val cachedAppVersion = cached.appVersion?.takeIf { it.isNotBlank() }
        val appVersionMatches =
            manifestAppVersion == null || cachedAppVersion == null || manifestAppVersion == cachedAppVersion
        val metaVersion = meta?.version?.takeIf { it.isNotBlank() }
        val metaEtag = meta?.etag?.takeIf { it.isNotBlank() }
        val cachedVersion =
            cached.pageVersion?.takeIf { it.isNotBlank() } ?: cached.page.pageVersion?.takeIf { it.isNotBlank() }
        val cachedEtag =
            cached.etag?.takeIf { it.isNotBlank() } ?: cached.page.etag?.takeIf { it.isNotBlank() }
        val versionComparable = metaVersion != null && cachedVersion != null
        val etagComparable = metaEtag != null && cachedEtag != null
        val hasComparableValidator = versionComparable || etagComparable
        val versionMatches = !versionComparable || metaVersion == cachedVersion
        val etagMatches = !etagComparable || metaEtag == cachedEtag
        return meta != null && appVersionMatches && hasComparableValidator && versionMatches && etagMatches
    }

    private fun isFresh(entry: CachedScreenEntry): Boolean {
        val ageMillis = (clock.nowEpochMillis() - entry.cachedAtEpochMillis).coerceAtLeast(0L)
        return ageMillis < ttlMillis
    }

    private fun isAppVersionMismatched(entry: CachedScreenEntry): Boolean {
        val expected = appVersion?.takeIf { it.isNotBlank() }
        val stored = entry.appVersion?.takeIf { it.isNotBlank() }
        return expected != null && stored != null && stored != expected
    }

    private suspend fun readValidEntry(cleanId: String): CachedScreenEntry? {
        val entry = readEntry(cleanId) ?: return null
        return if (isAppVersionMismatched(entry)) {
            removeEntry(cleanId)
            null
        } else {
            entry
        }
    }

    private suspend fun readEntry(cleanId: String): CachedScreenEntry? =
        storeMutex.withLock {
            val raw = store.getString(cacheKey(cleanId)) ?: return@withLock null
            runCatching { json.decodeFromString(CachedScreenEntry.serializer(), raw) }.getOrNull()
        }

    private suspend fun removeEntry(cleanId: String) {
        storeMutex.withLock {
            store.remove(cacheKey(cleanId))
        }
    }

    private suspend fun writeEntry(
        cleanId: String,
        page: PageResponse,
        etag: String? = page.etag,
        pageVersion: String? = page.pageVersion,
        entryAppVersion: String? = appVersion ?: page.appVersion,
    ) {
        val entry =
            CachedScreenEntry(
                cachedAtEpochMillis = clock.nowEpochMillis(),
                page = page,
                etag = etag?.takeIf { it.isNotBlank() },
                pageVersion = pageVersion?.takeIf { it.isNotBlank() },
                appVersion = entryAppVersion?.takeIf { it.isNotBlank() },
            )
        val encoded = json.encodeToString(CachedScreenEntry.serializer(), entry)
        storeMutex.withLock {
            store.putString(cacheKey(cleanId), encoded)
        }
    }

    private fun cacheKey(cleanId: String): String = "$KEY_PREFIX$cleanId"

    private fun normalizeScreenId(screenId: String): String = screenId.trim().removePrefix("/")

    private data class CachedManifest(
        val fetchedAtEpochMillis: Long,
        val response: ScreenManifestResponse,
    )

    @Serializable
    private data class CachedScreenEntry(
        val cachedAtEpochMillis: Long,
        val page: PageResponse,
        val etag: String? = null,
        val pageVersion: String? = null,
        val appVersion: String? = null,
    )

    companion object {
        /** Default cache time-to-live: 5 minutes. */
        const val DEFAULT_TTL_MILLIS: Long = 300_000L

        /** Default manifest cache time-to-live: 1 minute. */
        const val DEFAULT_MANIFEST_TTL_MILLIS: Long = 60_000L

        private const val KEY_PREFIX: String = "abeta_screen_cache:"
        private const val MANIFEST_FLIGHT_KEY: String = "__manifest__"
    }
}
