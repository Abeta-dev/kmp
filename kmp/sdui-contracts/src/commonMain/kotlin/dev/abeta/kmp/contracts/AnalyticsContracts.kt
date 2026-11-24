@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pure wire contract representing a single client analytics event ingested by the BFF.
 *
 * Supported [category] values include `"page_open"`, `"impression"`, `"interaction"`,
 * `"lifecycle"`, and `"custom"`.
 */
@Serializable
data class AnalyticsEventContract(
    @SerialName("event_id")
    val eventId: String? = null,
    @SerialName("event_name")
    val eventName: String,
    val category: String = "interaction",
    @SerialName("page_id")
    val pageId: String? = null,
    @SerialName("snippet_id")
    val snippetId: String? = null,
    @SerialName("component_id")
    val componentId: String? = null,
    @SerialName("action_id")
    val actionId: String? = null,
    @SerialName("action_type")
    val actionType: String? = null,
    @SerialName("timestamp_epoch_ms")
    val timestampEpochMs: Long = 0L,
    val properties: Map<String, String> = emptyMap(),
)

/**
 * Pure wire contract for a batched analytics ingestion request posted to the BFF.
 */
@Serializable
data class AnalyticsBatchRequest(
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    val platform: String? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
    val events: List<AnalyticsEventContract>,
)

/**
 * Pure wire contract for the BFF acknowledgement returned after ingesting an [AnalyticsBatchRequest].
 */
@Serializable
data class AnalyticsIngestResponse(
    val accepted: Int,
    val dropped: Int = 0,
    @SerialName("server_timestamp_epoch_ms")
    val serverTimestampEpochMs: Long = 0L,
)
