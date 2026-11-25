package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetDeltaOp
import dev.abeta.kmp.contracts.SnippetDeltaPatch
import dev.abeta.kmp.contracts.SnippetEnvelope

private const val MAX_PATCH_RECURSION_DEPTH = 16

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
            if (targetId == null) {
                page
            } else {
                val (updated, changed) = removeSnippetRecursive(page.snippets, targetId, depth = 0)
                if (changed) page.copy(snippets = updated) else page
            }
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

private fun updateTargetSnippet(
    page: PageResponse,
    targetId: String,
    transform: (SnippetEnvelope) -> SnippetEnvelope,
): PageResponse {
    val (updated, changed) = updateSnippetRecursive(page.snippets, targetId, depth = 0, transform = transform)
    return if (changed) page.copy(snippets = updated) else page
}

private fun updateSnippetRecursive(
    snippets: List<SnippetEnvelope>,
    targetId: String,
    depth: Int,
    transform: (SnippetEnvelope) -> SnippetEnvelope,
): Pair<List<SnippetEnvelope>, Boolean> {
    if (depth > MAX_PATCH_RECURSION_DEPTH || snippets.isEmpty()) {
        return snippets to false
    }
    var changed = false
    val result =
        snippets.map { current ->
            if (current.snippetId == targetId) {
                val next = transform(current)
                if (next != current) changed = true
                next
            } else {
                val (nested, nestedChanged) =
                    transformContainerChildren(current, depth + 1) { childList ->
                        updateSnippetRecursive(childList, targetId, depth + 1, transform)
                    }
                if (nestedChanged) changed = true
                nested
            }
        }
    return if (changed) result to true else snippets to false
}

private fun removeSnippetRecursive(
    snippets: List<SnippetEnvelope>,
    targetId: String,
    depth: Int,
): Pair<List<SnippetEnvelope>, Boolean> {
    if (depth > MAX_PATCH_RECURSION_DEPTH || snippets.isEmpty()) {
        return snippets to false
    }
    var changed = false
    val nextList = ArrayList<SnippetEnvelope>(snippets.size)
    for (current in snippets) {
        if (current.snippetId == targetId) {
            changed = true
            continue
        }
        val (nested, nestedChanged) =
            transformContainerChildren(current, depth + 1) { childList ->
                removeSnippetRecursive(childList, targetId, depth + 1)
            }
        if (nestedChanged) changed = true
        nextList.add(nested)
    }
    return if (changed) nextList to true else snippets to false
}
