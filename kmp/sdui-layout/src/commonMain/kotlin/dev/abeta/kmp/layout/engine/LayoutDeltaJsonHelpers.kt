@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_PATCH_NESTING_DEPTH = 16

private val DEFAULT_APPEND_FIELD_PATHS =
    listOf(
        "body.text",
        "description.text",
        "text.text",
        "message.text",
        "subtitle.text",
        "title.text",
    )

internal fun transformContainerChildren(
    envelope: SnippetEnvelope,
    nextDepth: Int,
    mutator: (List<SnippetEnvelope>) -> Pair<List<SnippetEnvelope>, Boolean>,
): Pair<SnippetEnvelope, Boolean> {
    if (nextDepth > MAX_PATCH_NESTING_DEPTH) return envelope to false
    return when (envelope.snippetType) {
        SnippetTypes.CAROUSEL, SnippetTypes.GRID -> {
            mutateEnvelopeArrayField(envelope, fieldName = "items", mutator = mutator)
        }

        SnippetTypes.ACCORDION -> {
            mutateEnvelopeArrayField(envelope, fieldName = "snippets", mutator = mutator)
        }

        SnippetTypes.TABS -> {
            mutateTabsSnippets(envelope, mutator = mutator)
        }

        else -> {
            envelope to false
        }
    }
}

private fun mutateEnvelopeArrayField(
    envelope: SnippetEnvelope,
    fieldName: String,
    mutator: (List<SnippetEnvelope>) -> Pair<List<SnippetEnvelope>, Boolean>,
): Pair<SnippetEnvelope, Boolean> {
    val decoded = (envelope.data[fieldName] as? JsonArray)?.let(::decodeEnvelopeList)
    val (updated, changed) = if (decoded != null) mutator(decoded) else (emptyList<SnippetEnvelope>() to false)
    return if (!changed) {
        envelope to false
    } else {
        val encodedArray =
            JsonArray(updated.map { SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), it) })
        val nextData = JsonObject(envelope.data.toMutableMap().apply { put(fieldName, encodedArray) })
        envelope.copy(data = nextData) to true
    }
}

private fun mutateTabsSnippets(
    envelope: SnippetEnvelope,
    mutator: (List<SnippetEnvelope>) -> Pair<List<SnippetEnvelope>, Boolean>,
): Pair<SnippetEnvelope, Boolean> {
    val tabsArray = envelope.data["tabs"] as? JsonArray ?: return envelope to false
    var anyChanged = false
    val updatedTabs =
        tabsArray.map { tabElement ->
            val tabObj = tabElement as? JsonObject
            val decoded = (tabObj?.get("snippets") as? JsonArray)?.let(::decodeEnvelopeList)
            if (tabObj == null || decoded == null) {
                tabElement
            } else {
                val (updated, changed) = mutator(decoded)
                if (!changed) {
                    tabElement
                } else {
                    anyChanged = true
                    val encodedSnippets =
                        JsonArray(updated.map { SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), it) })
                    JsonObject(tabObj.toMutableMap().apply { put("snippets", encodedSnippets) })
                }
            }
        }
    return if (!anyChanged) {
        envelope to false
    } else {
        val nextData = JsonObject(envelope.data.toMutableMap().apply { put("tabs", JsonArray(updatedTabs)) })
        envelope.copy(data = nextData) to true
    }
}

private fun decodeEnvelopeList(array: JsonArray): List<SnippetEnvelope>? =
    runCatching {
        array.map { element -> SduiJson.decodeFromJsonElement(SnippetEnvelope.serializer(), element) }
    }.getOrNull()

internal fun resolveAppendPathSegments(
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

internal fun appendTextAtPath(
    current: JsonObject,
    segments: List<String>,
    chunk: String,
): JsonObject = (appendTextAtElement(current, segments, chunk) as? JsonObject) ?: current

private fun appendTextAtElement(
    current: JsonElement?,
    segments: List<String>,
    chunk: String,
): JsonElement? {
    if (segments.isEmpty()) return current
    val head = segments.first()
    val tail = segments.drop(1)
    return when (current) {
        is JsonObject -> {
            appendTextInObject(current, head, tail, chunk)
        }

        is JsonArray -> {
            appendTextInArray(current, head, tail, chunk)
        }

        null, is JsonNull -> {
            if (head.toIntOrNull() != null) {
                null
            } else {
                appendTextInObject(JsonObject(emptyMap()), head, tail, chunk)
            }
        }

        else -> {
            null
        }
    }
}

private fun appendTextInObject(
    current: JsonObject,
    head: String,
    tail: List<String>,
    chunk: String,
): JsonObject? {
    val existing = current[head]
    val updated =
        if (tail.isEmpty()) {
            appendLeafText(existing, chunk)
        } else {
            appendTextAtElement(existing, tail, chunk)
        }
    return updated?.let { JsonObject(current.toMutableMap().apply { put(head, it) }) }
}

private fun appendTextInArray(
    current: JsonArray,
    head: String,
    tail: List<String>,
    chunk: String,
): JsonArray? {
    val index = head.toIntOrNull()?.takeIf { it in current.indices }
    return if (index == null) {
        null
    } else {
        val existing = current[index]
        val updated =
            if (tail.isEmpty()) {
                appendLeafText(existing, chunk)
            } else {
                appendTextAtElement(existing, tail, chunk)
            }
        updated?.let { JsonArray(current.toMutableList().apply { set(index, it) }) }
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
        if (overlayValue is JsonNull) {
            merged.remove(key)
            continue
        }
        val existingValue = merged[key]
        merged[key] =
            if (existingValue is JsonObject && overlayValue is JsonObject) {
                deepMergeJsonObjects(existingValue, overlayValue)
            } else {
                overlayValue
            }
    }
    return JsonObject(merged)
}
