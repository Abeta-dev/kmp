package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InteractiveSnippetContractsTest {
    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 12),
            cornerRadius = CornerRadiusSpec(token = "radius.md", all = 12),
            fillMaxWidth = true,
        )

    private fun action(
        type: String,
        key: String,
        value: String,
    ): ActionRef = ActionRef(type = type, payload = buildJsonObject { put(key, value) })

    @Test
    fun snippetTypesExposeCanonicalInteractiveIdentifiers() {
        assertEquals("swipe_deck", SnippetTypes.SWIPE_DECK)
        assertEquals("search_bar", SnippetTypes.SEARCH_BAR)
        assertEquals("chip_group", SnippetTypes.CHIP_GROUP)
    }

    @Test
    fun swipeDeckSnippetDataRoundTripsWithSnakeCaseWireNames() {
        val likeAction = action("call_api", "endpoint", "/api/v1/swipe/like")
        val nopeAction = action("call_api", "endpoint", "/api/v1/swipe/nope")
        val superAction = action("trigger_feedback", "effect", "celebration")
        val undoAction = action("show_toast", "message", "Undone")
        val clickAction = action("navigate", "route", "/profile/c1")
        val deck =
            SwipeDeckSnippetData(
                deckId = "discover_deck",
                cards =
                    listOf(
                        SwipeCardItem(
                            cardId = "card_1",
                            title = TextAtom(text = "Maya, 28"),
                            subtitle = TextAtom(text = "Staff KMP Engineer"),
                            bio = TextAtom(text = "Building multiplatform apps and espresso rigs."),
                            badge = TagAtom(label = "Verified", variant = TagVariant.SUCCESS),
                            distanceText = TextAtom(text = "3 km away"),
                            image = ImageAtom(url = "https://example.com/maya.jpg", contentDescription = "Maya"),
                            tags = listOf(TagAtom(label = "Kotlin"), TagAtom(label = "Hiking")),
                            onLike = likeAction,
                            onNope = nopeAction,
                            onSuperLike = superAction,
                            onClick = clickAction,
                        ),
                    ),
                likeLabel = "MATCH",
                nopeLabel = "PASS",
                superLikeLabel = "STAR",
                emptyTitle = TextAtom(text = "All caught up"),
                emptyMessage = TextAtom(text = "Check back soon for new picks."),
                onLike = likeAction,
                onNope = nopeAction,
                onSuperLike = superAction,
                onUndo = undoAction,
                layout = sampleLayout,
            )

        val encoded = SduiJson.encodeToString(deck)
        for (key in listOf(
            "deck_id",
            "card_id",
            "distance_text",
            "like_label",
            "nope_label",
            "super_like_label",
            "empty_title",
            "empty_message",
            "on_like",
            "on_nope",
            "on_super_like",
            "on_undo",
            "on_click",
        )) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key $key")
        }
        assertEquals(deck, SduiJson.decodeFromString<SwipeDeckSnippetData>(encoded))
    }

    @Test
    fun swipeDeckDefaultsDeserializeFromMinimalJson() {
        val minimal = SduiJson.decodeFromString<SwipeDeckSnippetData>("""{"deck_id":"empty_deck"}""")
        assertEquals("empty_deck", minimal.deckId)
        assertEquals("LIKE", minimal.likeLabel)
        assertEquals("NOPE", minimal.nopeLabel)
        assertEquals("SUPER LIKE", minimal.superLikeLabel)
        assertTrue(minimal.cards.isEmpty())
    }

    @Test
    fun searchBarSnippetDataRoundTripsWithSnakeCaseWireNamesAndDefaults() {
        val search =
            SearchBarSnippetData(
                searchId = "catalog_search",
                query = "avocados",
                placeholder = "Search groceries...",
                leadingIcon = IconAtom(name = "search", sizeDp = 20),
                trailingIcon = IconAtom(name = "settings", sizeDp = 20),
                submitLabel = "Go",
                clearable = true,
                onSubmit = action("navigate", "route", "/catalog?q={{query}}"),
                onClear = action("navigate", "route", "/catalog"),
                onTrailingClick = action("open_dialog", "dialog_id", "filters"),
                layout = sampleLayout,
            )

        val encoded = SduiJson.encodeToString(search)
        for (key in listOf(
            "search_id",
            "leading_icon",
            "trailing_icon",
            "submit_label",
            "on_submit",
            "on_clear",
            "on_trailing_click",
        )) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key $key")
        }
        assertEquals(search, SduiJson.decodeFromString<SearchBarSnippetData>(encoded))

        val minimal = SduiJson.decodeFromString<SearchBarSnippetData>("""{"search_id":"quick"}""")
        assertEquals("quick", minimal.searchId)
        assertEquals("", minimal.query)
        assertEquals("Search...", minimal.placeholder)
        assertTrue(minimal.clearable)
    }

    @Test
    fun chipGroupSnippetDataRoundTripsWithSnakeCaseWireNamesAndDefaults() {
        val chipGroup =
            ChipGroupSnippetData(
                groupId = "category_chips",
                title = TextAtom(text = "Filter by category"),
                multiSelect = true,
                chips =
                    listOf(
                        FilterChipItem(
                            id = "produce",
                            label = "Produce",
                            selected = true,
                            icon = IconAtom(name = "star"),
                            badge = "12",
                            onClick = action("navigate", "route", "/catalog?cat=produce"),
                        ),
                        FilterChipItem(id = "bakery", label = "Bakery"),
                    ),
                layout = sampleLayout,
            )

        val encoded = SduiJson.encodeToString(chipGroup)
        assertTrue(encoded.contains("\"group_id\""))
        assertTrue(encoded.contains("\"multi_select\""))
        assertTrue(encoded.contains("\"on_click\""))
        assertEquals(chipGroup, SduiJson.decodeFromString<ChipGroupSnippetData>(encoded))

        val minimal = SduiJson.decodeFromString<ChipGroupSnippetData>("""{"group_id":"tags"}""")
        assertEquals("tags", minimal.groupId)
        assertFalse(minimal.multiSelect)
        assertTrue(minimal.chips.isEmpty())
    }
}
