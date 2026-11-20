package dev.abeta.kmp.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Captured identify call recorded by [RecordingAnalyticsTracker].
 */
data class RecordedIdentifyCall(
    val userId: String,
    val traits: Map<String, String>,
)

/**
 * Thread-safe in-memory [AnalyticsTracker] that captures events, impressions, and identity calls for tests.
 */
class RecordingAnalyticsTracker : AnalyticsTracker {
    private val _events = MutableStateFlow<List<AnalyticsEvent>>(emptyList())
    private val _impressions = MutableStateFlow<List<AnalyticsTrackingPayload>>(emptyList())
    private val _identifyCalls = MutableStateFlow<List<RecordedIdentifyCall>>(emptyList())
    private val _resetCount = MutableStateFlow(0)

    /** Snapshot of all recorded [AnalyticsEvent]s in order of receipt. */
    val events: List<AnalyticsEvent>
        get() = _events.value

    /** Snapshot of all recorded [AnalyticsTrackingPayload]s in order of receipt. */
    val impressions: List<AnalyticsTrackingPayload>
        get() = _impressions.value

    /** Snapshot of all recorded `identify` invocations. */
    val identifyCalls: List<RecordedIdentifyCall>
        get() = _identifyCalls.value

    /** Number of `reset()` invocations observed. */
    val resetCount: Int
        get() = _resetCount.value

    override fun track(event: AnalyticsEvent) {
        _events.update { current -> current + event }
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        _impressions.update { current -> current + payload }
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        _identifyCalls.update { current -> current + RecordedIdentifyCall(userId = userId, traits = traits) }
    }

    override fun reset() {
        _resetCount.update { it + 1 }
    }

    /** Clears all recorded events, impressions, identify calls, and reset counters. */
    fun clear() {
        _events.value = emptyList()
        _impressions.value = emptyList()
        _identifyCalls.value = emptyList()
        _resetCount.value = 0
    }
}
