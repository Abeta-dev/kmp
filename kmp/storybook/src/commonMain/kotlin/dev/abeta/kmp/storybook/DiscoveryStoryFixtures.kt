package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SEARCH_ICON_DP = 18
private const val CHIP_ICON_DP = 16

/**
 * Sample SDUI envelopes for [DiscoveryAndSearchStory] covering `search_bar`,
 * single/multi-select `chip_group`, and a 3-card `swipe_deck`.
 */
internal object DiscoveryStoryFixtures {
    const val SEARCH_ID = "discovery_search"
    const val CATEGORY_GROUP_ID = "discovery_categories"
    const val FILTER_GROUP_ID = "discovery_filters"

    fun buildSearchEnvelope(): SnippetEnvelope {
        val search =
            SearchBarSnippetData(
                searchId = SEARCH_ID,
                placeholder = "Search Abeta Spark profiles, tags, or verticals...",
                leadingIcon = IconAtom(name = "search", sizeDp = SEARCH_ICON_DP),
                submitLabel = "Search",
                onSubmit =
                    ActionRef(
                        id = "discovery_search_submit",
                        type = ActionTypes.CALL_API,
                        payload =
                            buildJsonObject {
                                put("endpoint", "/api/v1/discovery?q={{query}}")
                                put("query", "{{query}}")
                            },
                    ),
                onClear = ActionRef(id = "discovery_search_clear", type = ActionTypes.TRACK_EVENT),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "discovery_search_bar",
            snippetType = SnippetTypes.SEARCH_BAR,
            data = SnippetsSamples.encode(search),
        )
    }

    fun buildCategoryChipsEnvelope(): SnippetEnvelope {
        val group =
            ChipGroupSnippetData(
                groupId = CATEGORY_GROUP_ID,
                title = SnippetsSamples.txt("Verticals (Single-Select)", "label.large", "text.secondary"),
                multiSelect = false,
                chips =
                    listOf(
                        chipItem("spark", "Abeta Spark", selected = true, iconName = "star"),
                        chipItem("fresh", "Abeta Fresh", badge = "8m"),
                        chipItem("bites", "Abeta Bites"),
                        chipItem("pulse", "Abeta Pulse"),
                        chipItem("mart", "Abeta Mart"),
                        chipItem("trade", "Abeta Trade"),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "discovery_category_chips",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = SnippetsSamples.encode(group),
        )
    }

    fun buildFilterChipsEnvelope(): SnippetEnvelope {
        val group =
            ChipGroupSnippetData(
                groupId = FILTER_GROUP_ID,
                title = SnippetsSamples.txt("Discovery Filters (Multi-Select)", "label.large", "text.secondary"),
                multiSelect = true,
                chips =
                    listOf(
                        chipItem("verified", "Verified", selected = true, iconName = "check"),
                        chipItem("nearby", "Nearby (<5 km)", selected = true),
                        chipItem("top_rated", "Top Rated", badge = "4.8+"),
                        chipItem("instant", "Instant Match"),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "discovery_filter_chips",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = SnippetsSamples.encode(group),
        )
    }

    fun buildSwipeDeckEnvelope(): SnippetEnvelope {
        val deck =
            SwipeDeckSnippetData(
                deckId = "discovery_spark_deck",
                cards =
                    listOf(
                        swipeCard(
                            id = "spark_maya",
                            name = "Maya Lin, 27",
                            role = "Product Designer · Bengaluru",
                            bio = "Crafting tactile mobile design systems, weekend espresso hunter, and trail runner.",
                            badge = "VERIFIED",
                            variant = TagVariant.SUCCESS,
                            distance = "2.4 km away",
                            tags = listOf("Design Systems", "Pour-Over", "Trail Running"),
                        ),
                        swipeCard(
                            id = "spark_arjun",
                            name = "Arjun Nair, 29",
                            role = "Audio Engineer · Indiranagar",
                            bio = "Modular synths by night, vinyl collector, always down for live acoustic sets.",
                            badge = "NEW SPARK",
                            variant = TagVariant.BRAND,
                            distance = "3.8 km away",
                            tags = listOf("Live Music", "Vinyl", "Analog Synths"),
                        ),
                        swipeCard(
                            id = "spark_elena",
                            name = "Elena Torres, 26",
                            role = "Culinary Photographer · Koramangala",
                            bio = "Shooting indie cafes, baking rosemary focaccia, and mapping coastal road trips.",
                            badge = "TOP PICK",
                            variant = TagVariant.INFO,
                            distance = "4.5 km away",
                            tags = listOf("Photography", "Sourdough", "Road Trips"),
                        ),
                    ),
                emptyTitle = SnippetsSamples.txt("You've Seen Every Spark Nearby", "title.medium"),
                emptyMessage =
                    SnippetsSamples.txt(
                        "Tap Reset Deck to replay the 3 discovery cards.",
                        "body.small",
                        "text.secondary",
                    ),
                onUndo =
                    ActionRef(
                        id = "discovery_swipe_undo",
                        type = ActionTypes.TRACK_EVENT,
                        payload = buildJsonObject { put("decision", "undo") },
                    ),
            )
        return SnippetEnvelope(
            snippetId = "discovery_swipe_deck",
            snippetType = SnippetTypes.SWIPE_DECK,
            data = SnippetsSamples.encode(deck),
        )
    }

    private fun chipItem(
        id: String,
        label: String,
        selected: Boolean = false,
        iconName: String? = null,
        badge: String? = null,
    ): FilterChipItem =
        FilterChipItem(
            id = id,
            label = label,
            selected = selected,
            icon = iconName?.let { IconAtom(name = it, sizeDp = CHIP_ICON_DP) },
            badge = badge,
            onClick =
                ActionRef(
                    id = "discovery_chip_$id",
                    type = ActionTypes.TRACK_EVENT,
                    payload = buildJsonObject { put("chip_id", id) },
                ),
        )

    @Suppress("LongParameterList")
    private fun swipeCard(
        id: String,
        name: String,
        role: String,
        bio: String,
        badge: String,
        variant: TagVariant,
        distance: String,
        tags: List<String>,
    ): SwipeCardItem =
        SwipeCardItem(
            cardId = id,
            title = SnippetsSamples.txt(name, "title.large"),
            subtitle = SnippetsSamples.txt(role, "label.medium", "text.brand"),
            bio = SnippetsSamples.txt(bio, "body.medium", "text.secondary"),
            badge = TagAtom(label = badge, variant = variant),
            distanceText = SnippetsSamples.txt(distance, "label.small", "text.tertiary"),
            tags = tags.map { TagAtom(label = it, variant = TagVariant.NEUTRAL) },
            onLike = swipeDecisionAction(id, name, "like", ActionTypes.TRIGGER_FEEDBACK),
            onNope = swipeDecisionAction(id, name, "nope", ActionTypes.TRACK_EVENT),
            onSuperLike = swipeDecisionAction(id, name, "super_like", ActionTypes.TRIGGER_FEEDBACK),
        )

    private fun swipeDecisionAction(
        cardId: String,
        profileName: String,
        decision: String,
        actionType: String,
    ): ActionRef =
        ActionRef(
            id = "swipe_${decision}_$cardId",
            type = actionType,
            payload =
                buildJsonObject {
                    put("card_id", cardId)
                    put("profile_name", profileName)
                    put("decision", decision)
                },
        )
}
