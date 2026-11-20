package dev.abeta.kmp.analytics

/**
 * [AnalyticsTracker] that fans out every tracked event, screen, identify, reset, and impression
 * to all registered [trackers].
 */
class CompositeAnalyticsTracker(
    private val trackers: List<AnalyticsTracker>,
) : AnalyticsTracker {
    constructor(vararg trackers: AnalyticsTracker) : this(trackers.toList())

    override fun track(event: AnalyticsEvent) {
        trackers.forEach { tracker -> tracker.track(event) }
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        trackers.forEach { tracker -> tracker.trackImpression(payload) }
    }

    override fun trackScreen(
        screenName: String,
        properties: Map<String, String>,
    ) {
        trackers.forEach { tracker -> tracker.trackScreen(screenName, properties) }
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        trackers.forEach { tracker -> tracker.identify(userId, traits) }
    }

    override fun reset() {
        trackers.forEach(AnalyticsTracker::reset)
    }
}
