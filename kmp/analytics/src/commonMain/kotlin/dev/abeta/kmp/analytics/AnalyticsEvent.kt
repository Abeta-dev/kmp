package dev.abeta.kmp.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-level semantic classification of an emitted analytics record.
 */
@Serializable
enum class AnalyticsCategory {
    ACTION,
    IMPRESSION,
    SCREEN,
    LIFECYCLE,
    SYSTEM,
    ERROR,
}

/**
 * Domain event emitted to analytics pipelines.
 */
@Serializable
data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestampEpochMillis: Long? = null,
    val category: AnalyticsCategory = AnalyticsCategory.ACTION,
    val eventId: String? = null,
    val sessionId: String? = null,
    val anonymousId: String? = null,
    val userId: String? = null,
    val screenName: String? = null,
)

/**
 * Serializable impression payload used for declarative view/exposure tracking.
 */
@Serializable
data class AnalyticsTrackingPayload(
    @SerialName("event_name")
    val eventName: String,
    val properties: Map<String, String> = emptyMap(),
)

/**
 * Runtime identity context attached to enriched analytics events.
 */
data class AnalyticsIdentity(
    val anonymousId: String? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val traits: Map<String, String> = emptyMap(),
)
