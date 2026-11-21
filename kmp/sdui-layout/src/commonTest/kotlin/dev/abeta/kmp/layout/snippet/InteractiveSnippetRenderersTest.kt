package dev.abeta.kmp.layout.snippet

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TextAtom
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InteractiveSnippetRenderersTest {
    @Test
    fun resolveSwipeDirectionHonorsHorizontalVerticalAndThresholdBoundaries() {
        val h = 110f
        val v = 96f
        assertNull(resolveSwipeDirection(40f, -30f, h, v))
        assertEquals(SwipeDirection.LIKE, resolveSwipeDirection(120f, 10f, h, v))
        assertEquals(SwipeDirection.NOPE, resolveSwipeDirection(-115f, -20f, h, v))
        assertEquals(SwipeDirection.SUPER_LIKE, resolveSwipeDirection(25f, -105f, h, v))
        assertEquals(SwipeDirection.LIKE, resolveSwipeDirection(140f, -100f, h, v))
        assertEquals(SwipeDirection.SUPER_LIKE, resolveSwipeDirection(115f, -150f, h, v))
    }

    @Test
    fun resolveSwipeActionPrefersCardActionAndFallsBackToDeckAction() {
        val deckLike = ActionRef(id = "deck_like", type = "show_toast")
        val deckNope = ActionRef(id = "deck_nope", type = "show_toast")
        val deckSuper = ActionRef(id = "deck_super", type = "show_toast")
        val cardLike = ActionRef(id = "card_like", type = "call_api")

        val card =
            SwipeCardItem(
                cardId = "c1",
                title = TextAtom(text = "Aria"),
                onLike = cardLike,
            )
        val deck =
            SwipeDeckSnippetData(
                deckId = "discover",
                cards = listOf(card),
                onLike = deckLike,
                onNope = deckNope,
                onSuperLike = deckSuper,
            )

        assertEquals(cardLike, resolveSwipeAction(card, deck, SwipeDirection.LIKE))
        assertEquals(deckNope, resolveSwipeAction(card, deck, SwipeDirection.NOPE))
        assertEquals(deckSuper, resolveSwipeAction(card, deck, SwipeDirection.SUPER_LIKE))
    }

    @Test
    fun resolveQueryPlaceholdersUrlEncodesRouteAndEndpointAndTrimsRawValuesRecursively() {
        val template =
            ActionRef(
                type = "batch",
                payload =
                    buildJsonObject {
                        put("route", "/catalog?q={{query}}&sort=top")
                        put("endpoint", "/api/v1/search?term={{query}}")
                        put("query", "{{query}}")
                        put("message", "Searching for {{query}}")
                        put("limit", 25)
                        put(
                            "nested",
                            buildJsonObject {
                                put("endpoint", "/v2/items/{{query}}")
                                put("label", "Tag: {{query}}")
                            },
                        )
                        put(
                            "actions",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("route", "/results?q={{query}}")
                                        put("raw", "{{query}}")
                                    },
                                )
                            },
                        )
                    },
                secondaryActions =
                    listOf(
                        ActionRef(
                            type = "track_event",
                            payload = buildJsonObject { put("term", "{{query}}") },
                        ),
                    ),
            )

        val resolved = resolveQueryPlaceholders(template, "  fresh milk  ")
        val payload = resolved.payload

        assertEquals("/catalog?q=fresh%20milk&sort=top", payload["route"]?.jsonPrimitive?.content)
        assertEquals("/api/v1/search?term=fresh%20milk", payload["endpoint"]?.jsonPrimitive?.content)
        assertEquals("fresh milk", payload["query"]?.jsonPrimitive?.content)
        assertEquals("Searching for fresh milk", payload["message"]?.jsonPrimitive?.content)
        assertEquals(25, payload["limit"]?.jsonPrimitive?.content?.toInt())

        val nested = payload["nested"]?.jsonObject
        assertEquals("/v2/items/fresh%20milk", nested?.get("endpoint")?.jsonPrimitive?.content)
        assertEquals("Tag: fresh milk", nested?.get("label")?.jsonPrimitive?.content)

        val nestedAction = payload["actions"]?.jsonArray?.firstOrNull()?.jsonObject
        assertEquals("/results?q=fresh%20milk", nestedAction?.get("route")?.jsonPrimitive?.content)
        assertEquals("fresh milk", nestedAction?.get("raw")?.jsonPrimitive?.content)

        assertEquals(
            "fresh milk",
            resolved.secondaryActions
                .first()
                .payload["term"]
                ?.jsonPrimitive
                ?.content,
        )
    }
}
