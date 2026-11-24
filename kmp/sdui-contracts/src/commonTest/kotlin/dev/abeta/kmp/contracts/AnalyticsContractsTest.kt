package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsContractsTest {
    @Test
    fun actionRefTrackingAndStickySnippetEnvelopeRoundTripWithDefaults() {
        val actionWithTracking =
            ActionRef(
                id = "add_to_cart_1",
                type = "call_api",
                payload = buildJsonObject { put("sku", "avocado-2pk") },
                tracking =
                    TrackingContract(
                        eventName = "cart_item_added",
                        properties = mapOf("sku" to "avocado-2pk", "surface" to "fresh_home"),
                    ),
            )
        val encodedAction = SduiJson.encodeToString(actionWithTracking)
        assertTrue(encodedAction.contains("\"tracking\""))
        assertTrue(encodedAction.contains("\"cart_item_added\""))
        assertEquals(actionWithTracking, SduiJson.decodeFromString<ActionRef>(encodedAction))

        val minimalAction = SduiJson.decodeFromString<ActionRef>("""{"type":"refresh_page"}""")
        assertNull(minimalAction.tracking)

        val stickyEnvelope =
            SnippetEnvelope(
                snippetId = "sticky_search_1",
                snippetType = SnippetTypes.SEARCH_BAR,
                sticky = true,
            )
        val encodedEnvelope = SduiJson.encodeToString(stickyEnvelope)
        assertTrue(encodedEnvelope.contains("\"sticky\":true"))
        assertEquals(stickyEnvelope, SduiJson.decodeFromString<SnippetEnvelope>(encodedEnvelope))

        val defaultEnvelope =
            SduiJson.decodeFromString<SnippetEnvelope>(
                """{"snippet_id":"hdr_1","snippet_type":"header_v1"}""",
            )
        assertFalse(defaultEnvelope.sticky)
    }

    @Test
    fun analyticsBatchRequestAndIngestResponseRoundTripWithWireKeysAndDefaults() {
        val event =
            AnalyticsEventContract(
                eventId = "evt-001",
                eventName = "snippet_clicked",
                category = "interaction",
                pageId = "fresh-home",
                snippetId = "hero_banner",
                componentId = "cta_shop_now",
                actionId = "act_open_deals",
                actionType = "navigate",
                timestampEpochMs = 1_764_000_000_123L,
                properties = mapOf("slot" to "hero", "position" to "0"),
            )
        val request =
            AnalyticsBatchRequest(
                sessionId = "sess-42",
                userId = "usr-9",
                platform = "ios",
                appVersion = "1.4.0",
                events = listOf(event),
            )
        val encodedRequest = SduiJson.encodeToString(request)
        for (key in listOf(
            "session_id",
            "user_id",
            "app_version",
            "event_id",
            "event_name",
            "page_id",
            "snippet_id",
            "component_id",
            "action_id",
            "action_type",
            "timestamp_epoch_ms",
        )) {
            assertTrue(encodedRequest.contains("\"$key\""), "Missing wire key \"$key\" in $encodedRequest")
        }
        assertEquals(request, SduiJson.decodeFromString<AnalyticsBatchRequest>(encodedRequest))

        val minimalEvent = SduiJson.decodeFromString<AnalyticsEventContract>("""{"event_name":"page_opened"}""")
        assertEquals("interaction", minimalEvent.category)
        assertEquals(0L, minimalEvent.timestampEpochMs)
        assertTrue(minimalEvent.properties.isEmpty())
        assertNull(minimalEvent.eventId)

        val response =
            AnalyticsIngestResponse(
                accepted = 5,
                dropped = 1,
                serverTimestampEpochMs = 1_764_000_001_000L,
            )
        val encodedResponse = SduiJson.encodeToString(response)
        assertTrue(encodedResponse.contains("\"server_timestamp_epoch_ms\""))
        assertEquals(response, SduiJson.decodeFromString<AnalyticsIngestResponse>(encodedResponse))

        val minimalResponse = SduiJson.decodeFromString<AnalyticsIngestResponse>("""{"accepted":3}""")
        assertEquals(3, minimalResponse.accepted)
        assertEquals(0, minimalResponse.dropped)
        assertEquals(0L, minimalResponse.serverTimestampEpochMs)
    }
}
