package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ScreenManifestResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.resilience.SingleFlight
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CancellationException
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
        val activeManifest = manifestMutex.withLock { cachedManifest }?.response
        val existingCached = readValidEntry(cleanId, activeManifest)
        val conditionalRemote = remote as? ConditionalScreenRepository
        return if (conditionalRemote != null) {
            executeConditionalRevalidation(conditionalRemote, cleanId, existingCached, activeManifest)
        } else {
            val result = remote.fetchScreen(cleanId)
            if (result is AppResult.Success) {
                val page = result.value
                writeEntry(
                    cleanId = cleanId,
                    page = page,
                    httpEtag = page.etag,
                    contentEtag = page.etag,
                    pageVersion = page.pageVersion,
                    serverAppVersion = page.appVersion ?: activeManifest?.appVersion,
                    sduiSchemaVersion = activeManifest?.sduiSchemaVersion,
                )
            }
            result
        }
    }

    private suspend fun executeConditionalRevalidation(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
        existingCached: CachedScreenEntry?,
        activeManifest: ScreenManifestResponse?,
    ): AppResult<PageResponse> {
        val validator =
            existingCached?.let { cached ->
                cached.httpEtag?.takeIf { it.isNotBlank() }
                    ?: cached.contentEtag?.takeIf { it.isNotBlank() }
                    ?: cached.etag?.takeIf { it.isNotBlank() }
                    ?: cached.page.etag?.takeIf { it.isNotBlank() }
            }
        return when (val result = conditionalRemote.fetchScreenConditional(cleanId, ifNoneMatch = validator)) {
            is AppResult.Failure -> {
                result
            }

            is AppResult.Success -> {
                handleConditionalSuccess(
                    conditionalRemote = conditionalRemote,
                    cleanId = cleanId,
                    existingCached = existingCached,
                    conditional = result.value,
                    activeManifest = activeManifest,
                )
            }
        }
    }

    private suspend fun handleConditionalSuccess(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
        existingCached: CachedScreenEntry?,
        conditional: ConditionalScreenFetchResult,
        activeManifest: ScreenManifestResponse?,
    ): AppResult<PageResponse> =
        when (conditional) {
            is ConditionalScreenFetchResult.Modified -> {
                persistModifiedResult(cleanId, conditional, activeManifest)
            }

            is ConditionalScreenFetchResult.NotModified -> {
                if (existingCached != null) {
                    persistNotModifiedResult(cleanId, existingCached, conditional.etag, activeManifest)
                } else {
                    fetchUnconditionalFallback(conditionalRemote, cleanId, activeManifest)
                }
            }
        }

    private suspend fun persistModifiedResult(
        cleanId: String,
        modified: ConditionalScreenFetchResult.Modified,
        activeManifest: ScreenManifestResponse?,
    ): AppResult<PageResponse> {
        val page = modified.page
        val contentEtag = page.etag?.takeIf { it.isNotBlank() } ?: modified.etag?.takeIf { it.isNotBlank() }
        val httpEtag =
            modified.httpEtag?.takeIf { it.isNotBlank() }
                ?: modified.etag?.takeIf { it.isNotBlank() }
                ?: contentEtag
        writeEntry(
            cleanId = cleanId,
            page = page,
            httpEtag = httpEtag,
            contentEtag = contentEtag,
            pageVersion = page.pageVersion,
            serverAppVersion = page.appVersion ?: activeManifest?.appVersion,
            sduiSchemaVersion = activeManifest?.sduiSchemaVersion,
        )
        return AppResult.Success(page)
    }

    private suspend fun persistNotModifiedResult(
        cleanId: String,
        existingCached: CachedScreenEntry,
        conditionalEtag: String?,
        activeManifest: ScreenManifestResponse?,
    ): AppResult<PageResponse> {
        val refreshedHttpEtag =
            conditionalEtag?.takeIf { it.isNotBlank() } ?: existingCached.httpEtag ?: existingCached.etag
        val refreshedContentEtag =
            existingCached.contentEtag?.takeIf { it.isNotBlank() }
                ?: existingCached.page.etag?.takeIf { it.isNotBlank() }
                ?: refreshedHttpEtag
        writeEntry(
            cleanId = cleanId,
            page = existingCached.page,
            httpEtag = refreshedHttpEtag,
            contentEtag = refreshedContentEtag,
            pageVersion = existingCached.pageVersion ?: existingCached.page.pageVersion,
            serverAppVersion =
                existingCached.serverAppVersion
                    ?: existingCached.page.appVersion
                    ?: activeManifest?.appVersion,
            sduiSchemaVersion = activeManifest?.sduiSchemaVersion ?: existingCached.sduiSchemaVersion,
        )
        return AppResult.Success(existingCached.page)
    }

    private suspend fun fetchUnconditionalFallback(
        conditionalRemote: ConditionalScreenRepository,
        cleanId: String,
        activeManifest: ScreenManifestResponse?,
    ): AppResult<PageResponse> =
        when (val fallback = conditionalRemote.fetchScreenConditional(cleanId, ifNoneMatch = null)) {
            is AppResult.Failure -> {
                fallback
            }

            is AppResult.Success -> {
                when (val value = fallback.value) {
                    is ConditionalScreenFetchResult.Modified -> {
                        persistModifiedResult(cleanId, value, activeManifest)
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
        val manifestResult = getOrFetchManifest()
        val manifest = (manifestResult as? AppResult.Success)?.value
        val cached = readValidEntry(cleanId, manifest)
        if (manifestResult is AppResult.Failure && manifestResult.error is OfflineError && cached != null) {
            return AppResult.Success(cached.page)
        }
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
        val meta = manifest.screens[cleanId] ?: return false
        return matchesServerAndSchemaVersions(cached, manifest) && matchesScreenValidators(cached, meta)
    }

    private fun matchesServerAndSchemaVersions(
        cached: CachedScreenEntry,
        manifest: ScreenManifestResponse,
    ): Boolean {
        val manifestServerVersion = manifest.appVersion.takeIf { it.isNotBlank() }
        val cachedServerVersion = cached.serverAppVersion.nonBlankOrNull() ?: cached.page.appVersion.nonBlankOrNull()
        val serverMatches =
            manifestServerVersion == null || cachedServerVersion == null || manifestServerVersion == cachedServerVersion
        val manifestSchema = manifest.sduiSchemaVersion.nonBlankOrNull()
        val schemaMatches = manifestSchema == null || cached.sduiSchemaVersion.nonBlankOrNull() == manifestSchema
        return serverMatches && schemaMatches
    }

    private fun matchesScreenValidators(
        cached: CachedScreenEntry,
        meta: dev.abeta.kmp.contracts.ScreenVersionMeta,
    ): Boolean {
        val metaVersion = meta.version.nonBlankOrNull()
        val metaEtag = meta.etag.nonBlankOrNull()
        val cachedVersion = cached.pageVersion.nonBlankOrNull() ?: cached.page.pageVersion.nonBlankOrNull()
        val cachedEtag =
            cached.contentEtag.nonBlankOrNull()
                ?: cached.page.etag.nonBlankOrNull()
                ?: cached.httpEtag.nonBlankOrNull()
                ?: cached.etag.nonBlankOrNull()
        val versionComparable = metaVersion != null && cachedVersion != null
        val etagComparable = metaEtag != null && cachedEtag != null
        val versionMatches = !versionComparable || metaVersion == cachedVersion
        val etagMatches = !etagComparable || metaEtag == cachedEtag
        return (versionComparable || etagComparable) && versionMatches && etagMatches
    }

    private fun isFresh(entry: CachedScreenEntry): Boolean {
        val ageMillis = (clock.nowEpochMillis() - entry.cachedAtEpochMillis).coerceAtLeast(0L)
        return ageMillis < ttlMillis
    }

    private fun isAppVersionMismatched(
        entry: CachedScreenEntry,
        activeManifest: ScreenManifestResponse? = null,
    ): Boolean {
        val expectedClientVersion = appVersion.nonBlankOrNull()
        val clientMismatched =
            expectedClientVersion != null && entry.clientAppVersion.nonBlankOrNull() != expectedClientVersion
        val manifestSchema = activeManifest?.sduiSchemaVersion.nonBlankOrNull()
        val schemaMismatched =
            manifestSchema != null && entry.sduiSchemaVersion.nonBlankOrNull() != manifestSchema
        return clientMismatched || schemaMismatched
    }

    private fun String?.nonBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

    private suspend fun readValidEntry(
        cleanId: String,
        activeManifest: ScreenManifestResponse? = null,
    ): CachedScreenEntry? {
        val entry = readEntry(cleanId) ?: return null
        val manifest = activeManifest ?: manifestMutex.withLock { cachedManifest }?.response
        return if (isAppVersionMismatched(entry, manifest)) {
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

    @Suppress("LongParameterList")
    private suspend fun writeEntry(
        cleanId: String,
        page: PageResponse,
        httpEtag: String? = page.etag,
        contentEtag: String? = page.etag,
        pageVersion: String? = page.pageVersion,
        serverAppVersion: String? = page.appVersion,
        sduiSchemaVersion: String? = null,
    ) {
        val normalizedHttpEtag = httpEtag?.takeIf { it.isNotBlank() }
        val normalizedContentEtag = contentEtag?.takeIf { it.isNotBlank() }
        val normalizedClientVersion = appVersion?.takeIf { it.isNotBlank() }
        val normalizedServerVersion = serverAppVersion?.takeIf { it.isNotBlank() }
        val entry =
            CachedScreenEntry(
                cachedAtEpochMillis = clock.nowEpochMillis(),
                page = page,
                etag = normalizedContentEtag ?: normalizedHttpEtag,
                pageVersion = pageVersion?.takeIf { it.isNotBlank() },
                appVersion = normalizedClientVersion ?: normalizedServerVersion,
                httpEtag = normalizedHttpEtag,
                contentEtag = normalizedContentEtag,
                serverAppVersion = normalizedServerVersion,
                clientAppVersion = normalizedClientVersion,
                sduiSchemaVersion = sduiSchemaVersion?.takeIf { it.isNotBlank() },
            )
        runCatching {
            val encoded = json.encodeToString(CachedScreenEntry.serializer(), entry)
            storeMutex.withLock {
                store.putString(cacheKey(cleanId), encoded)
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
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
        val httpEtag: String? = null,
        val contentEtag: String? = null,
        val serverAppVersion: String? = null,
        val clientAppVersion: String? = null,
        val sduiSchemaVersion: String? = null,
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
