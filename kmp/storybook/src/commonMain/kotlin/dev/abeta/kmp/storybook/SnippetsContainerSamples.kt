package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CAROUSEL_WIDTH_DP = 220
private const val CONTAINER_SPACING_DP = 12
private const val SEARCH_ICON_DP = 18
private const val CHIP_ICON_DP = 16
private const val PRODUCT_PRICE_CENTS = 349
private const val PRODUCT_MAX_QTY = 10

/**
 * Provides container, commerce, and interactive discovery [SnippetEnvelope] fixtures
 * (`carousel`, `grid`, `tabs`, `accordion`, `product_card`, `search_bar`, `chip_group`, `swipe_deck`).
 */
internal object SnippetsContainerSamples {
    fun allItems(): List<SnippetSampleItem> =
        listOf(
            SnippetSampleItem("Carousel", buildCarousel()),
            SnippetSampleItem("Grid", buildGrid()),
            SnippetSampleItem("Tabs", buildTabs()),
            SnippetSampleItem("Accordion", buildAccordion()),
            SnippetSampleItem("Product Card", buildProductCard()),
            SnippetSampleItem("Search Bar", buildSearchBar()),
            SnippetSampleItem("Chip Group", buildChipGroup()),
            SnippetSampleItem("Swipe Deck", buildSwipeDeck()),
        )

    private fun buildCarousel(): SnippetEnvelope {
        val item1 =
            SnippetEnvelope(
                snippetId = "story_car_1",
                snippetType = SnippetTypes.CARD,
                data =
                    SnippetsSamples.encode(
                        CardSnippetData(
                            title = SnippetsSamples.txt("Carousel Item 1", "title.small"),
                            badge = TagAtom(label = "ONE", variant = TagVariant.BRAND),
                            layout = SnippetsSamples.cardLayout,
                        ),
                    ),
            )
        val item2 =
            SnippetEnvelope(
                snippetId = "story_car_2",
                snippetType = SnippetTypes.METRIC_CARD,
                data =
                    SnippetsSamples.encode(
                        MetricCardSnippetData(
                            label = SnippetsSamples.txt("Throughput", "label.medium", "text.secondary"),
                            value = SnippetsSamples.txt("4.2k/s", "title.large"),
                            layout = SnippetsSamples.cardLayout,
                        ),
                    ),
            )
        val carousel =
            CarouselSnippetData(
                title = SnippetsSamples.txt("Horizontal Carousel", "title.medium"),
                itemWidthDp = CAROUSEL_WIDTH_DP,
                spacingDp = CONTAINER_SPACING_DP,
                items = listOf(item1, item2),
            )
        return SnippetEnvelope(
            snippetId = "story_carousel",
            snippetType = SnippetTypes.CAROUSEL,
            data = SnippetsSamples.encode(carousel),
        )
    }

    private fun buildGrid(): SnippetEnvelope {
        val cell1 =
            SnippetEnvelope(
                snippetId = "story_grid_1",
                snippetType = SnippetTypes.METRIC_CARD,
                data =
                    SnippetsSamples.encode(
                        MetricCardSnippetData(
                            label = SnippetsSamples.txt("Snippets", "label.medium", "text.secondary"),
                            value = SnippetsSamples.txt("23", "headline.small"),
                            layout = SnippetsSamples.cardLayout,
                        ),
                    ),
            )
        val cell2 =
            SnippetEnvelope(
                snippetId = "story_grid_2",
                snippetType = SnippetTypes.METRIC_CARD,
                data =
                    SnippetsSamples.encode(
                        MetricCardSnippetData(
                            label = SnippetsSamples.txt("Atoms", "label.medium", "text.secondary"),
                            value = SnippetsSamples.txt("16", "headline.small"),
                            layout = SnippetsSamples.cardLayout,
                        ),
                    ),
            )
        val grid =
            GridSnippetData(
                title = SnippetsSamples.txt("2-Column Grid", "title.medium"),
                columns = 2,
                spacingDp = CONTAINER_SPACING_DP,
                items = listOf(cell1, cell2),
            )
        return SnippetEnvelope(
            snippetId = "story_grid",
            snippetType = SnippetTypes.GRID,
            data = SnippetsSamples.encode(grid),
        )
    }

    private fun buildTabs(): SnippetEnvelope {
        val sampleRow = SnippetsSamples.buildRowAndEmpty().first().envelope
        val sampleEmpty = SnippetsSamples.buildRowAndEmpty().last().envelope
        val tabs =
            TabsSnippetData(
                tabs =
                    listOf(
                        TabItemSpec(
                            id = "tab_overview",
                            title = "Overview",
                            badge = TagAtom(label = "2", variant = TagVariant.BRAND),
                            snippets = listOf(sampleRow.copy(snippetId = "tab_row_1")),
                        ),
                        TabItemSpec(
                            id = "tab_status",
                            title = "Status",
                            snippets = listOf(sampleEmpty.copy(snippetId = "tab_empty_2")),
                        ),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_tabs",
            snippetType = SnippetTypes.TABS,
            data = SnippetsSamples.encode(tabs),
        )
    }

    private fun buildAccordion(): SnippetEnvelope {
        val childRow =
            SnippetsSamples
                .buildRowAndEmpty()
                .first()
                .envelope
                .copy(snippetId = "acc_child_row")
        val accordion =
            AccordionSnippetData(
                title = SnippetsSamples.txt("Expandable Accordion", "title.medium"),
                subtitle =
                    SnippetsSamples.txt(
                        "Click header to collapse or expand child snippets",
                        "body.small",
                        "text.secondary",
                    ),
                initiallyExpanded = true,
                snippets = listOf(childRow),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_accordion",
            snippetType = SnippetTypes.ACCORDION,
            data = SnippetsSamples.encode(accordion),
        )
    }

    private fun buildProductCard(): SnippetEnvelope {
        val product =
            ProductCardSnippetData(
                productId = "story_sample_avocados",
                title = SnippetsSamples.txt("Hass Avocados (2 pcs)", "title.small"),
                subtitle = SnippetsSamples.txt("Farm fresh · 350g", "body.small", "text.secondary"),
                price = SnippetsSamples.txt("$3.49", "label.large"),
                originalPrice = SnippetsSamples.txt("$4.49", "body.small", "text.tertiary"),
                unitPriceCents = PRODUCT_PRICE_CENTS,
                badge = TagAtom(label = "22% OFF", variant = TagVariant.SUCCESS),
                stepper = StepperAtom(fieldId = "sample_cart_avocados", quantity = 1, maxQuantity = PRODUCT_MAX_QTY),
                rating = SnippetsSamples.txt("4.9 ★", "label.small", "status.success"),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_product_card",
            snippetType = SnippetTypes.PRODUCT_CARD,
            data = SnippetsSamples.encode(product),
        )
    }

    private fun buildSearchBar(): SnippetEnvelope {
        val searchBar =
            SearchBarSnippetData(
                searchId = "story_search_bar",
                placeholder = "Search Abeta Fresh, Bites, or Mart...",
                leadingIcon = IconAtom(name = "search", sizeDp = SEARCH_ICON_DP),
                submitLabel = "Search",
                onSubmit =
                    ActionRef(
                        id = "story_search_submit",
                        type = ActionTypes.CALL_API,
                        payload = buildJsonObject { put("endpoint", "/api/v1/search?q={{query}}") },
                    ),
                onClear = ActionRef(id = "story_search_clear", type = ActionTypes.TRACK_EVENT),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_search_bar",
            snippetType = SnippetTypes.SEARCH_BAR,
            data = SnippetsSamples.encode(searchBar),
        )
    }

    private fun buildChipGroup(): SnippetEnvelope {
        val chips =
            ChipGroupSnippetData(
                groupId = "story_chip_group",
                title = SnippetsSamples.txt("Quick Filters", "title.small"),
                multiSelect = true,
                chips =
                    listOf(
                        FilterChipItem(
                            id = "all",
                            label = "All Verticals",
                            selected = true,
                            icon = IconAtom(name = "star", sizeDp = CHIP_ICON_DP),
                            onClick = ActionRef(id = "story_chip_all", type = ActionTypes.TRACK_EVENT),
                        ),
                        FilterChipItem(
                            id = "express",
                            label = "8-Min Express",
                            badge = "FAST",
                            onClick = ActionRef(id = "story_chip_express", type = ActionTypes.TRACK_EVENT),
                        ),
                        FilterChipItem(
                            id = "verified",
                            label = "Verified",
                            icon = IconAtom(name = "check", sizeDp = CHIP_ICON_DP),
                            onClick = ActionRef(id = "story_chip_verified", type = ActionTypes.TRACK_EVENT),
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "story_chip_group",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = SnippetsSamples.encode(chips),
        )
    }

    private fun buildSwipeDeck(): SnippetEnvelope {
        val deck =
            SwipeDeckSnippetData(
                deckId = "story_swipe_deck",
                cards =
                    listOf(
                        SwipeCardItem(
                            cardId = "story_spark_1",
                            title = SnippetsSamples.txt("Maya Lin, 27", "title.medium"),
                            subtitle =
                                SnippetsSamples.txt("Product Designer · Abeta Spark", "body.small", "text.secondary"),
                            bio =
                                SnippetsSamples.txt(
                                    "Building tactile interfaces and hunting single-origin espresso.",
                                ),
                            badge = TagAtom(label = "VERIFIED", variant = TagVariant.SUCCESS),
                            distanceText = SnippetsSamples.txt("2.4 km", "label.small", "text.tertiary"),
                            tags =
                                listOf(
                                    TagAtom(label = "Design", variant = TagVariant.BRAND),
                                    TagAtom(label = "Coffee", variant = TagVariant.NEUTRAL),
                                ),
                            onLike = ActionRef(id = "story_deck_like_1", type = ActionTypes.TRACK_EVENT),
                            onNope = ActionRef(id = "story_deck_nope_1", type = ActionTypes.TRACK_EVENT),
                            onSuperLike = ActionRef(id = "story_deck_super_1", type = ActionTypes.TRIGGER_FEEDBACK),
                        ),
                        SwipeCardItem(
                            cardId = "story_spark_2",
                            title = SnippetsSamples.txt("Arjun Nair, 29", "title.medium"),
                            subtitle =
                                SnippetsSamples.txt("Audio Engineer · Abeta Spark", "body.small", "text.secondary"),
                            bio = SnippetsSamples.txt("Modular synths, vinyl crates, and late-night acoustic sets."),
                            badge = TagAtom(label = "NEW", variant = TagVariant.INFO),
                            distanceText = SnippetsSamples.txt("3.8 km", "label.small", "text.tertiary"),
                            tags =
                                listOf(
                                    TagAtom(label = "Live Music", variant = TagVariant.BRAND),
                                    TagAtom(label = "Vinyl", variant = TagVariant.NEUTRAL),
                                ),
                            onLike = ActionRef(id = "story_deck_like_2", type = ActionTypes.TRACK_EVENT),
                            onNope = ActionRef(id = "story_deck_nope_2", type = ActionTypes.TRACK_EVENT),
                            onSuperLike = ActionRef(id = "story_deck_super_2", type = ActionTypes.TRIGGER_FEEDBACK),
                        ),
                    ),
                emptyTitle = SnippetsSamples.txt("All Caught Up", "title.medium"),
                emptyMessage =
                    SnippetsSamples.txt("Tap Reset Deck to replay the sample stack.", "body.small", "text.secondary"),
                onUndo = ActionRef(id = "story_deck_undo", type = ActionTypes.TRACK_EVENT),
            )
        return SnippetEnvelope(
            snippetId = "story_swipe_deck",
            snippetType = SnippetTypes.SWIPE_DECK,
            data = SnippetsSamples.encode(deck),
        )
    }
}
