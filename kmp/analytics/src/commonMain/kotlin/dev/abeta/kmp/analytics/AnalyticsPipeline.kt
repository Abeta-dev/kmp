package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.security.SensitiveDataRedactor
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * [AnalyticsTracker] decorator that redacts sensitive credentials and PII patterns from
 * event properties, impression payloads, and user traits before forwarding downstream.
 */
class SanitizingAnalyticsTracker(
    private val delegate: AnalyticsTracker,
    private val redactor: SensitiveDataRedactor = SensitiveDataRedactor.Default,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        delegate.track(event.copy(properties = redactor.redactMap(event.properties)))
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        delegate.trackImpression(payload.copy(properties = redactor.redactMap(payload.properties)))
    }

    override fun trackScreen(
        screenName: String,
        properties: Map<String, String>,
    ) {
        delegate.trackScreen(
            screenName = screenName,
            properties = redactor.redactMap(properties),
        )
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        delegate.identify(
            userId = userId,
            traits = redactor.redactMap(traits),
        )
    }

    internal fun notifyIdentifyWithoutDuplicateEvent(
        userId: String,
        traits: Map<String, String>,
    ) {
        propagateIdentifyWithoutDuplicateEvent(
            tracker = delegate,
            userId = userId,
            traits = redactor.redactMap(traits),
        )
    }

    override fun reset() {
        delegate.reset()
    }
}

/**
 * [AnalyticsTracker] decorator preventing duplicate impression storms caused by Compose recomposition
 * or rapid scrolling within a configurable [cooldownMillis] window.
 */
class DeduplicatingAnalyticsTracker(
    private val delegate: AnalyticsTracker,
    private val clock: Clock = SystemClock(),
    private val cooldownMillis: Long = DEFAULT_IMPRESSION_COOLDOWN_MILLIS,
    private val maxTrackedEntries: Int = DEFAULT_MAX_TRACKED_ENTRIES,
) : AnalyticsTracker {
    private val seenImpressions = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun track(event: AnalyticsEvent) {
        delegate.track(event)
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        val fingerprint = buildFingerprint(payload)
        val now = clock.nowEpochMillis()
        val capacity = maxTrackedEntries.coerceAtLeast(1)
        var accepted = false
        seenImpressions.update { current ->
            val lastSeen = current[fingerprint]
            if (lastSeen == null || (now - lastSeen) >= cooldownMillis) {
                accepted = true
                val next = LinkedHashMap<String, Long>(current.size + 1)
                current.forEach { (key, seenAt) ->
                    if ((now - seenAt) < cooldownMillis) {
                        next[key] = seenAt
                    }
                }
                next[fingerprint] = now
                while (next.size > capacity) {
                    val oldestKey = next.keys.firstOrNull() ?: break
                    next.remove(oldestKey)
                }
                next
            } else {
                accepted = false
                current
            }
        }
        if (accepted) {
            delegate.trackImpression(payload)
        }
    }

    override fun trackScreen(
        screenName: String,
        properties: Map<String, String>,
    ) {
        delegate.trackScreen(screenName, properties)
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        delegate.identify(userId, traits)
    }

    internal fun notifyIdentifyWithoutDuplicateEvent(
        userId: String,
        traits: Map<String, String>,
    ) {
        propagateIdentifyWithoutDuplicateEvent(tracker = delegate, userId = userId, traits = traits)
    }

    override fun reset() {
        seenImpressions.value = emptyMap()
        delegate.reset()
    }

    /** Clears cached impression timestamps so subsequent exposures emit fresh events. */
    fun clearImpressions() {
        seenImpressions.value = emptyMap()
    }

    private fun buildFingerprint(payload: AnalyticsTrackingPayload): String {
        val sortedProps =
            payload.properties.entries
                .sortedBy { it.key }
                .joinToString("&") { "${it.key}=${it.value}" }
        return "${payload.eventName}|$sortedProps"
    }

    companion object {
        const val DEFAULT_IMPRESSION_COOLDOWN_MILLIS: Long = 30_000L
        const val DEFAULT_MAX_TRACKED_ENTRIES: Int = 4_096
    }
}

/**
 * Stateful [AnalyticsTracker] facade enriching every emitted event with `eventId`, timestamp,
 * active screen name, global context properties, and session/user identity metadata.
 */
class EnrichingAnalyticsTracker(
    private val delegate: AnalyticsTracker,
    private val clock: Clock = SystemClock(),
    private val uuidGenerator: UuidGenerator = RandomUuidGenerator(),
    initialIdentity: AnalyticsIdentity = AnalyticsIdentity(),
    initialGlobalProperties: Map<String, String> = emptyMap(),
) : AnalyticsTracker {
    private val identityState = MutableStateFlow(initialIdentity)
    private val globalProps = MutableStateFlow(initialGlobalProperties.toMap())
    private val currentScreen = MutableStateFlow<String?>(null)

    /** Replaces the active [AnalyticsIdentity] context. */
    fun updateIdentity(identity: AnalyticsIdentity) {
        identityState.value = identity
    }

    /** Merges [properties] into global metadata attached to subsequent events. */
    fun setGlobalProperties(properties: Map<String, String>) {
        if (properties.isEmpty()) return
        globalProps.update { current -> current + properties }
    }

    /** Removes specific [keys] (or all global metadata when `null`) from global properties. */
    fun clearGlobalProperties(keys: Collection<String>? = null) {
        if (keys == null) {
            globalProps.value = emptyMap()
        } else {
            globalProps.update { current -> current - keys.toSet() }
        }
    }

    override fun track(event: AnalyticsEvent) {
        val identity = identityState.value
        val enriched =
            event.copy(
                eventId = event.eventId ?: uuidGenerator.nextId(),
                timestampEpochMillis = event.timestampEpochMillis ?: clock.nowEpochMillis(),
                sessionId = event.sessionId ?: identity.sessionId,
                anonymousId = event.anonymousId ?: identity.anonymousId,
                userId = event.userId ?: identity.userId,
                screenName = event.screenName ?: currentScreen.value,
                properties = globalProps.value + event.properties,
            )
        delegate.track(enriched)
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        delegate.trackImpression(
            payload.copy(properties = globalProps.value + payload.properties),
        )
    }

    override fun trackScreen(
        screenName: String,
        properties: Map<String, String>,
    ) {
        currentScreen.value = screenName
        track(
            AnalyticsEvent(
                name = "screen_view",
                properties = properties + ("screen_name" to screenName),
                category = AnalyticsCategory.SCREEN,
                screenName = screenName,
            ),
        )
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        identityState.update { current ->
            current.copy(userId = userId, traits = current.traits + traits)
        }
        track(
            AnalyticsEvent(
                name = "user_identify",
                properties = traits + ("user_id" to userId),
                category = AnalyticsCategory.LIFECYCLE,
                userId = userId,
            ),
        )
        propagateIdentifyWithoutDuplicateEvent(tracker = delegate, userId = userId, traits = traits)
    }

    override fun reset() {
        identityState.update { current -> current.copy(userId = null, traits = emptyMap()) }
        currentScreen.value = null
        delegate.reset()
    }
}

internal fun propagateIdentifyWithoutDuplicateEvent(
    tracker: AnalyticsTracker,
    userId: String,
    traits: Map<String, String>,
) {
    when (tracker) {
        is BufferedAnalyticsTracker,
        is LoggerAnalyticsTracker,
        -> {
            Unit
        }

        is SanitizingAnalyticsTracker -> {
            tracker.notifyIdentifyWithoutDuplicateEvent(userId, traits)
        }

        is DeduplicatingAnalyticsTracker -> {
            tracker.notifyIdentifyWithoutDuplicateEvent(userId, traits)
        }

        is CompositeAnalyticsTracker -> {
            tracker.notifyIdentifyWithoutDuplicateEvent(userId, traits)
        }

        else -> {
            tracker.identify(userId, traits)
        }
    }
}
