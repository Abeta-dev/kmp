package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainerAndControlContractsTest {
    private val sampleAction =
        ActionRef(
            id = "act_1",
            type = "navigate",
            payload = buildJsonObject { put("route", "/checkout") },
        )

    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 12),
            margin = SpacingValues(top = 8, bottom = 8),
            bgColor = ColorTokenRef(token = "surface.elevated", alpha = 0.95f, fallbackHex = "#FFFFFF"),
            cornerRadius = CornerRadiusSpec(token = "radius.md", all = 12),
            border = BorderSpec(widthDp = 1f, widthToken = "thin", color = ColorTokenRef(token = "border.default")),
            fillMaxWidth = true,
        )

    @Test
    fun paginationAndOverlayPresentationRoundTrip() {
        assertEquals(
            OverlayPresentation.DIALOG,
            SduiJson.decodeFromString<OverlayPresentation>("\"dialog\""),
        )
        assertEquals(
            OverlayPresentation.BOTTOM_SHEET,
            SduiJson.decodeFromString<OverlayPresentation>("\"bottom_sheet\""),
        )
        assertEquals(
            "\"bottom_sheet\"",
            SduiJson.encodeToString(OverlayPresentation.BOTTOM_SHEET),
        )

        val pagination =
            PaginationSpec(
                nextCursor = "cursor_page_2",
                hasMore = true,
                loadMoreAction =
                    ActionRef(
                        type = "call_api",
                        payload = buildJsonObject { put("endpoint", "/v1/next") },
                    ),
            )
        val encodedPagination = SduiJson.encodeToString(pagination)
        assertTrue(encodedPagination.contains("\"next_cursor\""))
        assertTrue(encodedPagination.contains("\"has_more\""))
        assertTrue(encodedPagination.contains("\"load_more_action\""))
        assertEquals(pagination, SduiJson.decodeFromString<PaginationSpec>(encodedPagination))

        val page =
            PageResponse(
                pageId = "feed",
                title = "Feed",
                pagination = pagination,
                pullToRefresh = ActionRef(type = "refresh_page"),
            )
        val encodedPage = SduiJson.encodeToString(page)
        assertTrue(encodedPage.contains("\"pull_to_refresh\""))
        assertEquals(page, SduiJson.decodeFromString<PageResponse>(encodedPage))
    }

    @Test
    fun avatarSlotsAndFormControlsRoundTrip() {
        val headerWithAvatar =
            HeaderSnippetData(
                title = TextAtom(text = "Profile"),
                avatar = AvatarAtom(initials = "AB", sizeDp = 44),
            )
        assertEquals(
            headerWithAvatar,
            SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(headerWithAvatar)),
        )

        val cardWithAvatar =
            CardSnippetData(
                title = TextAtom(text = "Builder"),
                avatar = AvatarAtom(initials = "JD", contentDescription = "Jane Doe", onClick = sampleAction),
                skeletons = listOf(SkeletonAtom(kind = SkeletonKind.TEXT, widthDp = 120, heightDp = 14)),
            )
        assertEquals(
            cardWithAvatar,
            SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(cardWithAvatar)),
        )

        val formWithControls =
            FormSnippetData(
                formId = "prefs_form",
                switches = listOf(SwitchAtom(fieldId = "alerts", label = "Alerts", checked = true)),
                checkboxes = listOf(CheckboxAtom(fieldId = "tos", label = "TOS", required = true)),
                sliders = listOf(SliderAtom(fieldId = "volume", label = "Volume", value = 80f)),
            )
        assertEquals(
            formWithControls,
            SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(formWithControls)),
        )
    }

    @Test
    fun carouselAndGridSnippetDtosRoundTrip() {
        val childEnvelope =
            SnippetEnvelope(
                snippetId = "child_1",
                snippetType = SnippetTypes.CARD,
                data = SduiJson.encodeToJsonElement(CardSnippetData(title = TextAtom(text = "Item 1"))).jsonObject,
            )

        val carousel =
            CarouselSnippetData(
                title = TextAtom(text = "Featured"),
                items = listOf(childEnvelope),
                itemWidthDp = 240,
                spacingDp = 12,
                layout = sampleLayout,
            )
        val encodedCarousel = SduiJson.encodeToString(carousel)
        assertTrue(encodedCarousel.contains("\"item_width_dp\""))
        assertTrue(encodedCarousel.contains("\"spacing_dp\""))
        assertEquals(carousel, SduiJson.decodeFromString<CarouselSnippetData>(encodedCarousel))
        assertEquals("carousel", SnippetTypes.CAROUSEL)

        val grid =
            GridSnippetData(
                title = TextAtom(text = "Categories"),
                columns = 3,
                spacingDp = 8,
                items = listOf(childEnvelope),
                layout = sampleLayout,
            )
        val encodedGrid = SduiJson.encodeToString(grid)
        assertTrue(encodedGrid.contains("\"spacing_dp\""))
        assertEquals(grid, SduiJson.decodeFromString<GridSnippetData>(encodedGrid))
        assertEquals("grid", SnippetTypes.GRID)
    }

    @Test
    fun tabsAndAccordionSnippetDtosRoundTrip() {
        val childEnvelope =
            SnippetEnvelope(
                snippetId = "child_1",
                snippetType = SnippetTypes.CARD,
                data = SduiJson.encodeToJsonElement(CardSnippetData(title = TextAtom(text = "Item 1"))).jsonObject,
            )

        val tabs =
            TabsSnippetData(
                tabs =
                    listOf(
                        TabItemSpec(
                            id = "overview",
                            title = "Overview",
                            badge = TagAtom(label = "2"),
                            snippets = listOf(childEnvelope),
                        ),
                        TabItemSpec(
                            id = "history",
                            title = "History",
                        ),
                    ),
                selectedIndex = 1,
                layout = sampleLayout,
            )
        val encodedTabs = SduiJson.encodeToString(tabs)
        assertTrue(encodedTabs.contains("\"selected_index\""))
        assertEquals(tabs, SduiJson.decodeFromString<TabsSnippetData>(encodedTabs))
        assertEquals("tabs", SnippetTypes.TABS)

        val accordion =
            AccordionSnippetData(
                title = TextAtom(text = "Advanced Settings"),
                subtitle = TextAtom(text = "Optional configuration"),
                initiallyExpanded = true,
                snippets = listOf(childEnvelope),
                layout = sampleLayout,
            )
        val encodedAccordion = SduiJson.encodeToString(accordion)
        assertTrue(encodedAccordion.contains("\"initially_expanded\""))
        assertEquals(accordion, SduiJson.decodeFromString<AccordionSnippetData>(encodedAccordion))
        assertEquals("accordion", SnippetTypes.ACCORDION)
    }
}
