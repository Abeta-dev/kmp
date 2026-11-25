package dev.abeta.kmp.state.session

import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Reactive session-scoped key-value store holding dynamic JSON entries.
 *
 * Implementations must avoid global singleton state so multiple scopes or tests remain isolated.
 */
interface SessionStore {
    /** Observable snapshot of the current session entries. */
    val state: StateFlow<Map<String, JsonElement>>

    /** Returns the [JsonElement] stored for [key], or `null` if absent. */
    fun get(key: String): JsonElement?

    /** Inserts or updates [value] under [key]. */
    fun put(
        key: String,
        value: JsonElement,
    )

    /** Atomically inserts or updates all [entries]. */
    fun putAll(entries: Map<String, JsonElement>)

    /** Atomically removes all entries matching [keys]. */
    fun remove(keys: Collection<String>)

    /** Clears all entries from this session store. */
    fun clear()
}

/**
 * Thread-safe, in-memory [SessionStore] backed by [MutableStateFlow].
 */
class InMemorySessionStore(
    initialState: Map<String, JsonElement> = emptyMap(),
) : SessionStore {
    private val _state = MutableStateFlow(initialState.toMap())

    override val state: StateFlow<Map<String, JsonElement>> = _state.asStateFlow()

    override fun get(key: String): JsonElement? = _state.value[key]

    override fun put(
        key: String,
        value: JsonElement,
    ) {
        _state.update { current -> current + (key to value) }
    }

    override fun putAll(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) {
            return
        }
        _state.update { current -> current + entries }
    }

    override fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) {
            return
        }
        _state.update { current -> current - keys.toSet() }
    }

    override fun clear() {
        _state.value = emptyMap()
    }
}

/**
 * Durable [SessionStore] hydrating initial state from a [KeyValueStore] and persisting mutations as JSON.
 */
class PersistentSessionStore(
    private val backingStore: KeyValueStore,
    private val storageKey: String = DEFAULT_STORAGE_KEY,
    private val json: Json = DefaultSessionJson,
) : SessionStore {
    private val lock = createStateLock()
    private val _state = MutableStateFlow(loadInitial())

    override val state: StateFlow<Map<String, JsonElement>> = _state.asStateFlow()

    override fun get(key: String): JsonElement? = _state.value[key]

    override fun put(
        key: String,
        value: JsonElement,
    ) {
        updateAndPersist { current -> current + (key to value) }
    }

    override fun putAll(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) return
        updateAndPersist { current -> current + entries }
    }

    override fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        updateAndPersist { current -> current - keys.toSet() }
    }

    override fun clear() {
        lock.withLock {
            _state.value = emptyMap()
            backingStore.remove(storageKey)
        }
    }

    private fun updateAndPersist(transform: (Map<String, JsonElement>) -> Map<String, JsonElement>) {
        lock.withLock {
            val next = transform(_state.value)
            _state.value = next
            backingStore.putString(storageKey, json.encodeToString(next))
        }
    }

    private fun loadInitial(): Map<String, JsonElement> {
        val raw = backingStore.getString(storageKey) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, JsonElement>>(raw) }.getOrElse { emptyMap() }
    }

    companion object {
        const val DEFAULT_STORAGE_KEY: String = "abeta_session_state"
    }
}

/**
 * Structured authentication state exposed reactively to UI, actions, and analytics layers.
 */
sealed interface AuthSessionState {
    /** Anonymous / signed-out state retaining an installation-scoped [anonymousId]. */
    data class Unauthenticated(
        val anonymousId: String,
    ) : AuthSessionState

    /** Signed-in principal state holding identity, roles, and attributes. */
    data class Authenticated(
        val userId: String,
        val anonymousId: String,
        val displayName: String? = null,
        val roles: Set<String> = emptySet(),
        val attributes: Map<String, String> = emptyMap(),
    ) : AuthSessionState

    /** Previously active session timed out or invalidated; re-authentication required. */
    data class Expired(
        val previousUserId: String? = null,
        val anonymousId: String,
    ) : AuthSessionState
}

/**
 * Serializable snapshot representing runtime session identity, activity window, and auth metadata.
 */
@Serializable
data class SessionMetadata(
    val sessionId: String,
    val anonymousId: String,
    val userId: String? = null,
    val displayName: String? = null,
    val roles: Set<String> = emptySet(),
    val attributes: Map<String, String> = emptyMap(),
    val startedAtEpochMillis: Long,
    val lastActiveEpochMillis: Long,
)

/**
 * Manages installation anonymous identity, idle session expiration windows, and reactive auth state.
 */
class SessionLifecycleManager(
    private val backingStore: KeyValueStore,
    private val clock: Clock = SystemClock(),
    private val uuidGenerator: UuidGenerator = RandomUuidGenerator(),
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val storageKey: String = DEFAULT_METADATA_KEY,
    private val json: Json = DefaultSessionJson,
) {
    private val lock = createStateLock()
    private val initial = loadOrInitialize()
    private val _metadata = MutableStateFlow(initial.metadata)
    private val _authState = MutableStateFlow(initial.authState)

    /** Observable session metadata including [SessionMetadata.sessionId] and [SessionMetadata.anonymousId]. */
    val metadata: StateFlow<SessionMetadata> = _metadata.asStateFlow()

    /** Observable authentication state transition stream. */
    val authState: StateFlow<AuthSessionState> = _authState.asStateFlow()

    /**
     * Records activity and automatically rotates the session ID if [idleTimeoutMillis] has elapsed.
     * When an authenticated session exceeds [idleTimeoutMillis], credentials are cleared and [authState]
     * transitions to [AuthSessionState.Expired]; an already-expired state is preserved until [signIn] or [signOut].
     */
    fun touch(): SessionMetadata =
        lock.withLock {
            val now = clock.nowEpochMillis()
            val current = _metadata.value
            val currentAuth = _authState.value
            val expired = (now - current.lastActiveEpochMillis) >= idleTimeoutMillis
            when {
                expired && current.userId != null -> {
                    val rotated =
                        current.copy(
                            sessionId = uuidGenerator.nextId(),
                            userId = null,
                            displayName = null,
                            roles = emptySet(),
                            attributes = emptyMap(),
                            startedAtEpochMillis = now,
                            lastActiveEpochMillis = now,
                        )
                    commitLocked(
                        updated = rotated,
                        nextAuthState =
                            AuthSessionState.Expired(
                                previousUserId = current.userId,
                                anonymousId = rotated.anonymousId,
                            ),
                    )
                    rotated
                }

                currentAuth is AuthSessionState.Expired -> {
                    val updated =
                        if (expired) {
                            current.copy(
                                sessionId = uuidGenerator.nextId(),
                                startedAtEpochMillis = now,
                                lastActiveEpochMillis = now,
                            )
                        } else {
                            current.copy(lastActiveEpochMillis = now)
                        }
                    commitLocked(updated = updated, nextAuthState = currentAuth)
                    updated
                }

                else -> {
                    val updated =
                        if (expired) {
                            current.copy(
                                sessionId = uuidGenerator.nextId(),
                                startedAtEpochMillis = now,
                                lastActiveEpochMillis = now,
                            )
                        } else {
                            current.copy(lastActiveEpochMillis = now)
                        }
                    commitLocked(updated = updated, nextAuthState = toAuthState(updated, expired = false))
                    updated
                }
            }
        }

    /** Transitions the active session into [AuthSessionState.Authenticated] for [userId]. */
    fun signIn(
        userId: String,
        displayName: String? = null,
        roles: Set<String> = emptySet(),
        attributes: Map<String, String> = emptyMap(),
    ): SessionMetadata =
        lock.withLock {
            val now = clock.nowEpochMillis()
            val updated =
                _metadata.value.copy(
                    sessionId = uuidGenerator.nextId(),
                    userId = userId,
                    displayName = displayName,
                    roles = roles,
                    attributes = attributes,
                    startedAtEpochMillis = now,
                    lastActiveEpochMillis = now,
                )
            commitLocked(updated = updated, nextAuthState = toAuthState(updated, expired = false))
            updated
        }

    /** Clears authenticated user claims while preserving the anonymous identifier and starting a fresh session. */
    fun signOut(): SessionMetadata =
        lock.withLock {
            val now = clock.nowEpochMillis()
            val updated =
                _metadata.value.copy(
                    sessionId = uuidGenerator.nextId(),
                    userId = null,
                    displayName = null,
                    roles = emptySet(),
                    attributes = emptyMap(),
                    startedAtEpochMillis = now,
                    lastActiveEpochMillis = now,
                )
            commitLocked(updated = updated, nextAuthState = toAuthState(updated, expired = false))
            updated
        }

    /** Marks the current authenticated session expired while preserving [anonymousId]. */
    fun markExpired() {
        lock.withLock {
            val current = _metadata.value
            val previousUser = current.userId ?: (_authState.value as? AuthSessionState.Expired)?.previousUserId
            val updated =
                current.copy(
                    userId = null,
                    displayName = null,
                    roles = emptySet(),
                    attributes = emptyMap(),
                )
            commitLocked(
                updated = updated,
                nextAuthState =
                    AuthSessionState.Expired(
                        previousUserId = previousUser,
                        anonymousId = updated.anonymousId,
                    ),
            )
        }
    }

    private fun commitLocked(
        updated: SessionMetadata,
        nextAuthState: AuthSessionState,
    ) {
        _metadata.value = updated
        persist(updated)
        _authState.value = nextAuthState
    }

    private fun persist(value: SessionMetadata) {
        backingStore.putString(storageKey, json.encodeToString(SessionMetadata.serializer(), value))
    }

    private fun loadOrInitialize(): InitialSession {
        val now = clock.nowEpochMillis()
        val restored =
            backingStore.getString(storageKey)?.let { raw ->
                runCatching { json.decodeFromString(SessionMetadata.serializer(), raw) }.getOrNull()
            }
        if (restored != null) {
            val isStillFresh = (now - restored.lastActiveEpochMillis) < idleTimeoutMillis
            return if (isStillFresh) {
                val updated = restored.copy(lastActiveEpochMillis = now).also(::persist)
                InitialSession(metadata = updated, authState = toAuthState(updated, expired = false))
            } else if (restored.userId != null) {
                val cleared =
                    restored
                        .copy(
                            sessionId = uuidGenerator.nextId(),
                            userId = null,
                            displayName = null,
                            roles = emptySet(),
                            attributes = emptyMap(),
                            startedAtEpochMillis = now,
                            lastActiveEpochMillis = now,
                        ).also(::persist)
                InitialSession(
                    metadata = cleared,
                    authState =
                        AuthSessionState.Expired(
                            previousUserId = restored.userId,
                            anonymousId = cleared.anonymousId,
                        ),
                )
            } else {
                val rotated =
                    restored
                        .copy(
                            sessionId = uuidGenerator.nextId(),
                            startedAtEpochMillis = now,
                            lastActiveEpochMillis = now,
                        ).also(::persist)
                InitialSession(metadata = rotated, authState = toAuthState(rotated, expired = false))
            }
        }
        val created =
            SessionMetadata(
                sessionId = uuidGenerator.nextId(),
                anonymousId = uuidGenerator.nextId(),
                startedAtEpochMillis = now,
                lastActiveEpochMillis = now,
            ).also(::persist)
        return InitialSession(metadata = created, authState = toAuthState(created, expired = false))
    }

    private data class InitialSession(
        val metadata: SessionMetadata,
        val authState: AuthSessionState,
    )

    private fun toAuthState(
        value: SessionMetadata,
        expired: Boolean,
    ): AuthSessionState =
        when {
            expired -> {
                AuthSessionState.Expired(previousUserId = value.userId, anonymousId = value.anonymousId)
            }

            value.userId != null -> {
                AuthSessionState.Authenticated(
                    userId = value.userId,
                    anonymousId = value.anonymousId,
                    displayName = value.displayName,
                    roles = value.roles,
                    attributes = value.attributes,
                )
            }

            else -> {
                AuthSessionState.Unauthenticated(anonymousId = value.anonymousId)
            }
        }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MILLIS: Long = 30L * 60L * 1000L
        const val DEFAULT_METADATA_KEY: String = "abeta_session_lifecycle"
    }
}

private val DefaultSessionJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
