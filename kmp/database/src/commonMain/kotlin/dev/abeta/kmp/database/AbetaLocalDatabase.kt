package dev.abeta.kmp.database

import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.database.entities.CachedScreenEntity
import dev.abeta.kmp.database.entities.KeyValueRecordEntity
import dev.abeta.kmp.database.entities.OfflineMutationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * High-level local storage facade wrapping [AbetaDatabase] and deterministic [Clock] semantics.
 *
 * Provides ergonomic `suspend` and reactive [Flow] operations for SDUI screen caching,
 * FIFO offline mutation queueing, and namespaced key-value persistence.
 */
@Suppress("TooManyFunctions")
class AbetaLocalDatabase(
    /** Underlying Room 3 [AbetaDatabase] instance for direct DAO or transaction access. */
    val database: AbetaDatabase,
    private val clock: Clock = SystemClock(),
    private val uuidGenerator: UuidGenerator = RandomUuidGenerator(),
) {
    /**
     * Upserts a serialized screen payload for [screenId], optionally attaching an HTTP [etag],
     * relative [ttlMillis] expiration window computed from [clock], [pageVersion], and [appVersion].
     */
    suspend fun putScreenCache(
        screenId: String,
        payloadJson: String,
        etag: String? = null,
        ttlMillis: Long? = null,
        pageVersion: String? = null,
        appVersion: String? = null,
    ): CachedScreenEntity {
        require(screenId.isNotBlank()) { "screenId must not be blank." }
        if (ttlMillis != null) {
            require(ttlMillis > 0L) { "ttlMillis must be positive when provided, got $ttlMillis." }
        }
        val now = clock.nowEpochMillis()
        val entity =
            CachedScreenEntity(
                screenId = screenId,
                payloadJson = payloadJson,
                etag = etag,
                cachedAtEpochMillis = now,
                expiresAtEpochMillis = computeExpiresAtEpochMillis(now, ttlMillis),
                pageVersion = pageVersion,
                appVersion = appVersion,
            )
        database.cachedScreenDao().upsert(entity)
        return entity
    }

    /**
     * Returns the cached screen for [screenId] only if present and not expired relative to [clock].
     */
    suspend fun getFreshScreenCache(screenId: String): CachedScreenEntity? {
        val now = clock.nowEpochMillis()
        return database.cachedScreenDao().getById(screenId)?.takeUnless { cached ->
            isExpired(cached.expiresAtEpochMillis, now)
        }
    }

    /** Emits reactive updates whenever the cached screen row for [screenId] changes, filtering expired rows. */
    fun observeScreenCache(screenId: String): Flow<CachedScreenEntity?> =
        database
            .cachedScreenDao()
            .observeById(screenId)
            .map { cached ->
                val now = clock.nowEpochMillis()
                cached?.takeUnless { isExpired(it.expiresAtEpochMillis, now) }
            }.distinctUntilChanged()

    /** Deletes a single cached screen by [screenId] and returns the removed row count. */
    suspend fun deleteScreenCache(screenId: String): Int = database.cachedScreenDao().deleteById(screenId)

    /** Deletes all expired cached screens relative to [clock] and returns the evicted row count. */
    suspend fun evictExpiredScreens(): Int = database.cachedScreenDao().deleteExpired(clock.nowEpochMillis())

    /** Deletes every cached screen entry. */
    suspend fun clearScreenCache() {
        database.cachedScreenDao().clearAll()
    }

    /** Returns the number of non-expired rows currently stored in the screen cache table. */
    suspend fun screenCacheCount(): Int = database.cachedScreenDao().countFresh(clock.nowEpochMillis())

    /**
     * Enqueues an offline mutation into the durable FIFO outbox, trimming oldest entries if
     * the table exceeds [maxQueueSize].
     */
    suspend fun enqueueMutation(
        mutationType: String,
        payloadJson: String,
        id: String = uuidGenerator.nextId(),
        maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    ): OfflineMutationEntity {
        require(mutationType.isNotBlank()) { "mutationType must not be blank." }
        require(id.isNotBlank()) { "Mutation id must not be blank." }
        require(maxQueueSize > 0) { "maxQueueSize must be positive, got $maxQueueSize." }
        val entity =
            OfflineMutationEntity(
                id = id,
                mutationType = mutationType,
                payloadJson = payloadJson,
                createdAtEpochMillis = clock.nowEpochMillis(),
                retryCount = 0,
                lastError = null,
            )
        database.offlineMutationDao().upsert(entity)
        database.offlineMutationDao().trimToMaxSize(maxQueueSize)
        return entity
    }

    /** Returns all pending mutations in deterministic FIFO order. */
    suspend fun pendingMutations(): List<OfflineMutationEntity> = database.offlineMutationDao().listAllOrdered()

    /** Emits reactive FIFO snapshots of all pending mutations. */
    fun observePendingMutations(): Flow<List<OfflineMutationEntity>> = database.offlineMutationDao().observeAllOrdered()

    /** Emits the reactive count of pending offline mutations. */
    fun observePendingMutationCount(): Flow<Int> = database.offlineMutationDao().observePendingCount()

    /** Increments the retry counter and records the latest [error] diagnostic for mutation [id]. */
    suspend fun markMutationRetry(
        id: String,
        error: String? = null,
    ): Int = database.offlineMutationDao().incrementRetry(id = id, error = error)

    /** Removes a successfully synchronized mutation by [id] and returns the deleted row count. */
    suspend fun completeMutation(id: String): Int = database.offlineMutationDao().deleteById(id)

    /** Deletes dead-letter mutations whose retry count has reached [maxRetries] and returns the purged count. */
    suspend fun purgeDeadLetters(maxRetries: Int = DEFAULT_MAX_RETRIES): Int {
        require(maxRetries > 0) { "maxRetries must be positive, got $maxRetries." }
        return database.offlineMutationDao().deleteExceededRetries(maxRetries)
    }

    /** Retains at most [maxSize] newest mutations and deletes older rows, returning the evicted count. */
    suspend fun trimMutationQueue(maxSize: Int): Int {
        require(maxSize >= 0) { "maxSize must be non-negative, got $maxSize." }
        return database.offlineMutationDao().trimToMaxSize(maxSize)
    }

    /** Clears every queued offline mutation. */
    suspend fun clearMutations() {
        database.offlineMutationDao().clearAll()
    }

    /**
     * Stores [value] under `(namespace, key)`, optionally expiring after [ttlMillis] relative to [clock].
     */
    suspend fun putValue(
        namespace: String,
        key: String,
        value: String,
        ttlMillis: Long? = null,
    ) {
        require(namespace.isNotBlank()) { "namespace must not be blank." }
        require(key.isNotBlank()) { "key must not be blank." }
        if (ttlMillis != null) {
            require(ttlMillis > 0L) { "ttlMillis must be positive when provided, got $ttlMillis." }
        }
        val now = clock.nowEpochMillis()
        database.keyValueRecordDao().upsert(
            KeyValueRecordEntity(
                namespace = namespace,
                recordKey = key,
                valueText = value,
                updatedAtEpochMillis = now,
                expiresAtEpochMillis = computeExpiresAtEpochMillis(now, ttlMillis),
            ),
        )
    }

    /** Returns the stored value for `(namespace, key)` if present and not expired relative to [clock]. */
    suspend fun getValue(
        namespace: String,
        key: String,
    ): String? {
        val now = clock.nowEpochMillis()
        return database
            .keyValueRecordDao()
            .get(namespace = namespace, key = key)
            ?.takeUnless { record -> isExpired(record.expiresAtEpochMillis, now) }
            ?.valueText
    }

    /** Emits reactive value updates for `(namespace, key)`, or `null` when absent or expired. */
    fun observeValue(
        namespace: String,
        key: String,
    ): Flow<String?> =
        database
            .keyValueRecordDao()
            .observeRecord(namespace = namespace, key = key)
            .map { record ->
                val now = clock.nowEpochMillis()
                record?.takeUnless { isExpired(it.expiresAtEpochMillis, now) }?.valueText
            }.distinctUntilChanged()

    /** Lists all non-expired stored keys in [namespace], ordered lexicographically. */
    suspend fun listKeys(namespace: String): List<String> =
        database.keyValueRecordDao().listFreshKeys(namespace = namespace, nowEpochMillis = clock.nowEpochMillis())

    /** Removes a single entry for `(namespace, key)` and returns the deleted row count. */
    suspend fun removeValue(
        namespace: String,
        key: String,
    ): Int = database.keyValueRecordDao().delete(namespace = namespace, key = key)

    /** Removes all entries in [namespace] and returns the deleted row count. */
    suspend fun clearNamespace(namespace: String): Int = database.keyValueRecordDao().deleteNamespace(namespace)

    /** Deletes all expired key-value records relative to [clock] and returns the evicted row count. */
    suspend fun evictExpiredValues(): Int = database.keyValueRecordDao().deleteExpired(clock.nowEpochMillis())

    /** Releases SQLite connections and invalidation trackers held by [database]. */
    fun close() {
        database.close()
    }

    private fun computeExpiresAtEpochMillis(
        nowEpochMillis: Long,
        ttlMillis: Long?,
    ): Long? {
        if (ttlMillis == null) return null
        return if (nowEpochMillis > 0L && ttlMillis > Long.MAX_VALUE - nowEpochMillis) {
            Long.MAX_VALUE
        } else {
            nowEpochMillis + ttlMillis
        }
    }

    private fun isExpired(
        expiresAtEpochMillis: Long?,
        nowEpochMillis: Long,
    ): Boolean = expiresAtEpochMillis != null && expiresAtEpochMillis <= nowEpochMillis

    companion object {
        /** Default maximum number of pending mutations retained in SQLite. */
        const val DEFAULT_MAX_QUEUE_SIZE: Int = 500

        /** Default retry threshold at or above which mutations are purged as dead letters. */
        const val DEFAULT_MAX_RETRIES: Int = 5
    }
}
