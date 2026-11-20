package dev.abeta.kmp.analytics

/**
 * Port for dispatching domain analytics events, screen views, identity bindings, and impression payloads.
 */
interface AnalyticsTracker {
    /** Tracks a discrete user or system [event]. */
    fun track(event: AnalyticsEvent)

    /** Tracks an impression or exposure [payload]. */
    fun trackImpression(payload: AnalyticsTrackingPayload)

    /** Tracks navigation into [screenName] with optional contextual [properties]. */
    fun trackScreen(
        screenName: String,
        properties: Map<String, String> = emptyMap(),
    ) {
        track(
            AnalyticsEvent(
                name = "screen_view",
                properties = properties + ("screen_name" to screenName),
                category = AnalyticsCategory.SCREEN,
                screenName = screenName,
            ),
        )
    }

    /** Associates future events with authenticated [userId] and optional [traits]. */
    fun identify(
        userId: String,
        traits: Map<String, String> = emptyMap(),
    ) {
        track(
            AnalyticsEvent(
                name = "user_identify",
                properties = traits + ("user_id" to userId),
                category = AnalyticsCategory.LIFECYCLE,
                userId = userId,
            ),
        )
    }

    /** Clears active user-scoped traits/identifiers (for example on sign-out). */
    fun reset() = Unit
}
