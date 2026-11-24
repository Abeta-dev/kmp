package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetDeltaOp
import dev.abeta.kmp.contracts.SnippetDeltaPatch
import dev.abeta.kmp.contracts.SnippetEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val DEFAULT_APPEND_FIELD_PATHS =
    listOf(
        "body.text",
        "text.text",
        "message.text",
        "subtitle.text",
        "title.text",
    )

/**
 * Pure, deterministic reducer applying an incremental [SnippetDeltaPatch] to [page] without
 * mutating any input structures.
 */
fun applySnippetDeltaPatch(
    page: PageResponse,
    patch: SnippetDeltaPatch,
): PageResponse =
    when (patch.op) {
        SnippetDeltaOp.APPEND_TEXT -> {
            applyAppendText(page, patch)
        }

        SnippetDeltaOp.PATCH_SNIPPET_DATA -> {
            applyPatchSnippetData(page, patch)
        }

        SnippetDeltaOp.REPLACE_SNIPPET -> {
            applyReplaceSnippet(page, patch)
        }

        SnippetDeltaOp.INSERT_SNIPPET -> {
            applyInsertSnippet(page, patch)
        }

        SnippetDeltaOp.REMOVE_SNIPPET -> {
            val targetId = patch.snippetId?.takeIf { it.isNotBlank() }
            val filtered =
                if (targetId != null) page.snippets.filterNot { it.snippetId == targetId } else page.snippets
            if (filtered.size == page.snippets.size) page else page.copy(snippets = filtered)
        }

        SnippetDeltaOp.UPDATE_PAGE_META -> {
            page.copy(
                title = patch.title ?: page.title,
                pageVersion = patch.pageVersion ?: page.pageVersion,
            )
        }
    }

private fun applyReplaceSnippet(
    page: PageResponse,
    patch: SnippetDeltaPatch,
): PageResponse {
    val targetId = patch.snippetId?.takeIf { it.isNotBlank() }
    val replacement = patch.snippet
    return if (targetId == null || replacement == null) {
        page
    } else {
        updateTargetSnippet(page, targetId) { replacement }
    }
}

private fun applyAppendText(
    page: PageResponse,
    patch: SnippetDeltaPatch,
): PageResponse {
    val targetId = patch.snippetId?.takeIf { it.isNotBlank() }
    val chunk = patch.textChunk?.takeIf { it.isNotEmpty() }
    return if (targetId == null || chunk == null) {
        page
    } else {
        updateTargetSnippet(page, targetId) { snippet ->
            val segments = resolveAppendPathSegments(snippet.data, patch.fieldPath)
            val updatedData = appendTextAtPath(snippet.data, segments, chunk)
            if (updatedData == snippet.data) snippet else snippet.copy(data = updatedData)
        }
    }
}

private fun applyPatchSnippetData(
    page: PageResponse,
    patch: SnippetDeltaPatch,
): PageResponse {
    val targetId = patch.snippetId?.takeIf { it.isNotBlank() }
    val overlay = patch.dataPatch
    return if (targetId == null || overlay == null) {
        page
    } else {
        updateTargetSnippet(page, targetId) { snippet ->
            snippet.copy(data = deepMergeJsonObjects(snippet.data, overlay))
        }
    }
}

private fun applyInsertSnippet(
    page: PageResponse,
    patch: SnippetDeltaPatch,
): PageResponse {
    val incoming = patch.snippet ?: return page
    val existingIndex = page.snippets.indexOfFirst { it.snippetId == incoming.snippetId }
    val updatedSnippets =
        if (existingIndex >= 0) {
            page.snippets.mapIndexed { index, current -> if (index == existingIndex) incoming else current }
        } else {
            val targetIndex = (patch.insertIndex ?: page.snippets.size).coerceIn(0, page.snippets.size)
            page.snippets
                .toMutableList()
                .apply { add(targetIndex, incoming) }
                .toList()
        }
    return page.copy(snippets = updatedSnippets)
}

private inline fun updateTargetSnippet(
    page: PageResponse,
    targetId: String,
    transform: (SnippetEnvelope) -> SnippetEnvelope,
): PageResponse {
    val index = page.snippets.indexOfFirst { it.snippetId == targetId }
    return if (index < 0) {
        page
    } else {
        val current = page.snippets[index]
        val updated = transform(current)
        if (updated == current) {
            page
        } else {
            val nextSnippets =
                page.snippets
                    .toMutableList()
                    .apply { set(index, updated) }
                    .toList()
            page.copy(snippets = nextSnippets)
        }
    }
}

private fun resolveAppendPathSegments(
    data: JsonObject,
    explicitPath: String?,
): List<String> {
    val parsedExplicit =
        explicitPath
            ?.split('.')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    return if (parsedExplicit.isNotEmpty()) {
        parsedExplicit
    } else {
        DEFAULT_APPEND_FIELD_PATHS
            .map { it.split('.') }
            .firstOrNull { segments ->
                val parent =
                    segments.dropLast(1).fold<String, JsonElement?>(data) { acc, segment ->
                        (acc as? JsonObject)?.get(segment)
                    } as? JsonObject
                val leaf = parent?.get(segments.last())
                leaf is JsonPrimitive && leaf.isString
            } ?: DEFAULT_APPEND_FIELD_PATHS.first().split('.')
    }
}

private fun appendTextAtPath(
    current: JsonObject,
    segments: List<String>,
    chunk: String,
): JsonObject {
    if (segments.isEmpty()) return current
    val head = segments.first()
    val existing = current[head]
    val updatedElement: JsonElement? =
        if (segments.size == 1) {
            appendLeafText(existing, chunk)
        } else {
            val childObject =
                when (existing) {
                    is JsonObject -> existing
                    null, is JsonNull -> JsonObject(emptyMap())
                    else -> null
                }
            childObject?.let { appendTextAtPath(it, segments.drop(1), chunk) }
        }
    return if (updatedElement == null) {
        current
    } else {
        JsonObject(current.toMutableMap().apply { put(head, updatedElement) })
    }
}

private fun appendLeafText(
    existing: JsonElement?,
    chunk: String,
): JsonElement? =
    when {
        existing is JsonPrimitive && existing.isString -> {
            JsonPrimitive(existing.content + chunk)
        }

        existing == null || existing is JsonNull -> {
            JsonPrimitive(chunk)
        }

        existing is JsonObject && existing["text"] is JsonPrimitive -> {
            val textLeaf = existing["text"] as JsonPrimitive
            if (textLeaf.isString) {
                JsonObject(existing.toMutableMap().apply { put("text", JsonPrimitive(textLeaf.content + chunk)) })
            } else {
                null
            }
        }

        else -> {
            null
        }
    }

internal fun deepMergeJsonObjects(
    base: JsonObject,
    overlay: JsonObject,
): JsonObject {
    val merged = base.toMutableMap()
    for ((key, overlayValue) in overlay) {
        val baseValue = merged[key]
        merged[key] =
            if (baseValue is JsonObject && overlayValue is JsonObject) {
                deepMergeJsonObjects(baseValue, overlayValue)
            } else {
                overlayValue
            }
    }
    return JsonObject(merged)
}
