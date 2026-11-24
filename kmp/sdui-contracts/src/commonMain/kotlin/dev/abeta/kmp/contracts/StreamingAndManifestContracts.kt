@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Deterministic version and cache metadata for a single SDUI screen in [ScreenManifestResponse].
 */
@Serializable
data class ScreenVersionMeta(
    val version: String,
    val etag: String? = null,
    @SerialName("updated_at_epoch_ms")
    val updatedAtEpochMs: Long? = null,
)

/**
 * Manifest response mapping every registered SDUI screen ID to its [ScreenVersionMeta].
 */
@Serializable
data class ScreenManifestResponse(
    @SerialName("app_version")
    val appVersion: String,
    val screens: Map<String, ScreenVersionMeta> = emptyMap(),
    @SerialName("generated_at_epoch_ms")
    val generatedAtEpochMs: Long? = null,
)

/**
 * Server-Sent Events (SSE) stream attachment specification declared on a [PageResponse].
 */
@Serializable
data class SseStreamSpec(
    val path: String,
    @SerialName("auto_connect")
    val autoConnect: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    @SerialName("retry_ms")
    val retryMs: Long? = null,
)

/**
 * Wire operation discriminator for incremental SDUI [SnippetDeltaPatch] stream events.
 */
@Serializable
enum class SnippetDeltaOp {
    @SerialName("append_text")
    APPEND_TEXT,

    @SerialName("patch_snippet_data")
    PATCH_SNIPPET_DATA,

    @SerialName("replace_snippet")
    REPLACE_SNIPPET,

    @SerialName("insert_snippet")
    INSERT_SNIPPET,

    @SerialName("remove_snippet")
    REMOVE_SNIPPET,

    @SerialName("update_page_meta")
    UPDATE_PAGE_META,
}

/**
 * Incremental SDUI delta patch delivered over Server-Sent Events (SSE).
 */
@Serializable
data class SnippetDeltaPatch(
    val op: SnippetDeltaOp,
    @SerialName("snippet_id")
    val snippetId: String? = null,
    @SerialName("field_path")
    val fieldPath: String? = null,
    @SerialName("text_chunk")
    val textChunk: String? = null,
    @SerialName("data_patch")
    val dataPatch: JsonObject? = null,
    val snippet: SnippetEnvelope? = null,
    @SerialName("insert_index")
    val insertIndex: Int? = null,
    val title: String? = null,
    @SerialName("page_version")
    val pageVersion: String? = null,
    val done: Boolean = false,
)
