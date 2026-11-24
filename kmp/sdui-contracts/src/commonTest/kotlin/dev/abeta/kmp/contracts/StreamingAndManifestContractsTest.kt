package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingAndManifestContractsTest {
    @Test
    fun pageResponseVersionAndStreamSpecRoundTripWithBackwardsCompatDefaults() {
        val streamSpec =
            SseStreamSpec(
                path = "/api/v1/streams/ai-assistant",
                autoConnect = true,
                headers = mapOf("X-Stream-Mode" to "delta"),
                retryMs = 3_000L,
            )
        val page =
            PageResponse(
                pageId = "streaming-demo",
                title = "Live Assistant",
                pageVersion = "v1-a1b2c3d4e5f6",
                appVersion = "0.7.0",
                etag = "\"abcdef0123456789\"",
                streamSpec = streamSpec,
            )

        val encoded = SduiJson.encodeToString(page)
        for (key in listOf("page_version", "app_version", "etag", "stream_spec", "retry_ms")) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key \"$key\" in $encoded")
        }
        assertEquals(page, SduiJson.decodeFromString<PageResponse>(encoded))

        val minimalPage = SduiJson.decodeFromString<PageResponse>("""{"page_id":"legacy"}""")
        assertNull(minimalPage.pageVersion)
        assertNull(minimalPage.appVersion)
        assertNull(minimalPage.etag)
        assertNull(minimalPage.streamSpec)
    }

    @Test
    fun screenManifestAndVersionMetaRoundTripWithWireKeysAndDefaults() {
        val manifest =
            ScreenManifestResponse(
                appVersion = "0.7.0",
                screens =
                    mapOf(
                        "home" to
                            ScreenVersionMeta(
                                version = "v1-112233445566",
                                etag = "\"0123456789abcdef\"",
                                updatedAtEpochMs = 1_764_000_000_000L,
                            ),
                        "store-home" to ScreenVersionMeta(version = "v1-abcdef012345"),
                    ),
                generatedAtEpochMs = 1_764_000_010_000L,
            )

        val encoded = SduiJson.encodeToString(manifest)
        for (key in listOf("app_version", "screens", "updated_at_epoch_ms", "generated_at_epoch_ms")) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key \"$key\" in $encoded")
        }
        assertEquals(manifest, SduiJson.decodeFromString<ScreenManifestResponse>(encoded))

        val minimalManifest = SduiJson.decodeFromString<ScreenManifestResponse>("""{"app_version":"0.7.0"}""")
        assertEquals("0.7.0", minimalManifest.appVersion)
        assertTrue(minimalManifest.screens.isEmpty())
        assertNull(minimalManifest.generatedAtEpochMs)

        val minimalMeta = SduiJson.decodeFromString<ScreenVersionMeta>("""{"version":"v1-abc"}""")
        assertEquals("v1-abc", minimalMeta.version)
        assertNull(minimalMeta.etag)
        assertNull(minimalMeta.updatedAtEpochMs)
    }

    @Test
    fun sseStreamSpecDefaultsAndExplicitAutoConnectFalseRoundTrip() {
        val minimal = SduiJson.decodeFromString<SseStreamSpec>("""{"path":"/api/v1/streams/live"}""")
        assertEquals("/api/v1/streams/live", minimal.path)
        assertTrue(minimal.autoConnect)
        assertTrue(minimal.headers.isEmpty())
        assertNull(minimal.retryMs)

        val manual =
            SseStreamSpec(
                path = "/api/v1/streams/manual",
                autoConnect = false,
                headers = mapOf("Accept" to "text/event-stream"),
                retryMs = 5_000L,
            )
        val encoded = SduiJson.encodeToString(manual)
        assertTrue(encoded.contains("\"auto_connect\":false"))
        assertTrue(encoded.contains("\"retry_ms\":5000"))
        assertEquals(manual, SduiJson.decodeFromString<SseStreamSpec>(encoded))
    }

    @Test
    fun snippetDeltaOpEnumWireLiteralsRoundTrip() {
        val opWirePairs =
            listOf(
                SnippetDeltaOp.APPEND_TEXT to "append_text",
                SnippetDeltaOp.PATCH_SNIPPET_DATA to "patch_snippet_data",
                SnippetDeltaOp.REPLACE_SNIPPET to "replace_snippet",
                SnippetDeltaOp.INSERT_SNIPPET to "insert_snippet",
                SnippetDeltaOp.REMOVE_SNIPPET to "remove_snippet",
                SnippetDeltaOp.UPDATE_PAGE_META to "update_page_meta",
            )
        for ((op, wire) in opWirePairs) {
            val encoded = SduiJson.encodeToString(op)
            assertEquals("\"$wire\"", encoded)
            assertEquals(op, SduiJson.decodeFromString<SnippetDeltaOp>(encoded))
        }
    }

    @Test
    fun snippetDeltaPatchPayloadsAndDefaultsRoundTrip() {
        val sampleEnvelope =
            SnippetEnvelope(
                snippetId = "stream_card_1",
                snippetType = SnippetTypes.CARD,
                data = buildJsonObject { put("title", "Inserted") },
            )

        val patches =
            listOf(
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.APPEND_TEXT,
                    snippetId = "stream_response_card",
                    fieldPath = "description.text",
                    textChunk = "Hello streaming SDUI!",
                ),
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.PATCH_SNIPPET_DATA,
                    snippetId = "stream_status_metric",
                    dataPatch = buildJsonObject { put("status", "live") },
                ),
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.REPLACE_SNIPPET,
                    snippetId = "stream_card_1",
                    snippet = sampleEnvelope,
                ),
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.INSERT_SNIPPET,
                    snippet = sampleEnvelope,
                    insertIndex = 0,
                ),
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.REMOVE_SNIPPET,
                    snippetId = "stream_card_1",
                ),
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.UPDATE_PAGE_META,
                    title = "Completed Stream",
                    pageVersion = "v1-998877665544",
                    done = true,
                ),
            )

        for (patch in patches) {
            val encoded = SduiJson.encodeToString(patch)
            assertEquals(patch, SduiJson.decodeFromString<SnippetDeltaPatch>(encoded))
        }

        val minimalJson = """{"op":"remove_snippet","snippet_id":"old_1"}"""
        val minimalPatch = SduiJson.decodeFromString<SnippetDeltaPatch>(minimalJson)
        assertEquals(SnippetDeltaOp.REMOVE_SNIPPET, minimalPatch.op)
        assertEquals("old_1", minimalPatch.snippetId)
        assertFalse(minimalPatch.done)
        assertNull(minimalPatch.fieldPath)
        assertNull(minimalPatch.textChunk)
        assertNull(minimalPatch.dataPatch)
        assertNull(minimalPatch.snippet)
        assertNull(minimalPatch.insertIndex)
        assertNull(minimalPatch.title)
        assertNull(minimalPatch.pageVersion)
    }
}
