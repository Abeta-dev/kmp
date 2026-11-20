package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant

private const val CAROUSEL_WIDTH_DP = 220
private const val CONTAINER_SPACING_DP = 12

/**
 * Provides container & commerce [SnippetEnvelope] fixtures (`carousel`, `grid`, `tabs`, `accordion`, `product_card`).
 */
internal object SnippetsContainerSamples {
    fun allItems(): List<SnippetSampleItem> =
        listOf(
            SnippetSampleItem("Carousel", buildCarousel()),
            SnippetSampleItem("Grid", buildGrid()),
            SnippetSampleItem("Tabs", buildTabs()),
            SnippetSampleItem("Accordion", buildAccordion()),
            SnippetSampleItem("Product Card", buildProductCard()),
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
                            value = SnippetsSamples.txt("13", "headline.small"),
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
                            value = SnippetsSamples.txt("14", "headline.small"),
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
                unitPriceCents = 349,
                badge = TagAtom(label = "22% OFF", variant = TagVariant.SUCCESS),
                stepper = StepperAtom(fieldId = "sample_cart_avocados", quantity = 1, maxQuantity = 10),
                rating = SnippetsSamples.txt("4.9 ★", "label.small", "status.success"),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_product_card",
            snippetType = SnippetTypes.PRODUCT_CARD,
            data = SnippetsSamples.encode(product),
        )
    }
}
