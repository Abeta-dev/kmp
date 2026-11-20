package dev.abeta.kmp.layout.snippet

import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.ResponsiveIntSpec
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContainerSnippetRenderersTest {
    @Test
    fun decodesContainerPageAndAllNestedSnippetEnvelopes() {
        val page = SduiJson.decodeFromString(PageResponse.serializer(), CONTAINERS_PAYLOAD_JSON)
        val registry = BuiltInSnippetModule.createDefaultRegistry()

        assertEquals("containers", page.pageId)
        assertTrue(page.pagination?.hasMore == true)
        assertNotNull(page.pullToRefresh)

        val byType = page.snippets.associateBy { it.snippetType }
        val header =
            assertIs<AppResult.Success<HeaderSnippetData>>(
                registry.resolve(SnippetTypes.HEADER)!!.decode(byType.getValue(SnippetTypes.HEADER).data),
            ).value
        assertEquals("AC", header.avatar?.initials)

        val carousel =
            assertIs<AppResult.Success<CarouselSnippetData>>(
                registry.resolve(SnippetTypes.CAROUSEL)!!.decode(byType.getValue(SnippetTypes.CAROUSEL).data),
            ).value
        assertEquals(240, carousel.itemWidthDp)
        assertEquals(1, carousel.items.size)
        val carouselChild = carousel.items.single()
        val carouselCard =
            assertIs<AppResult.Success<CardSnippetData>>(
                registry.resolve(carouselChild.snippetType)!!.decode(carouselChild.data),
            ).value
        assertEquals("SR", carouselCard.avatar?.initials)
        assertEquals(1, carouselCard.skeletons.size)
        assertEquals(SkeletonKind.CIRCLE, carouselCard.skeletons.single().kind)

        val grid =
            assertIs<AppResult.Success<GridSnippetData>>(
                registry.resolve(SnippetTypes.GRID)!!.decode(byType.getValue(SnippetTypes.GRID).data),
            ).value
        assertEquals(2, grid.columns)
        assertEquals(2, grid.items.size)

        val tabs =
            assertIs<AppResult.Success<TabsSnippetData>>(
                registry.resolve(SnippetTypes.TABS)!!.decode(byType.getValue(SnippetTypes.TABS).data),
            ).value
        assertEquals(2, tabs.tabs.size)
        assertEquals(
            "LIVE",
            tabs.tabs
                .first()
                .badge
                ?.label,
        )

        val accordion =
            assertIs<AppResult.Success<AccordionSnippetData>>(
                registry.resolve(SnippetTypes.ACCORDION)!!.decode(byType.getValue(SnippetTypes.ACCORDION).data),
            ).value
        assertTrue(accordion.initiallyExpanded)
        assertEquals(1, accordion.snippets.size)

        val form =
            assertIs<AppResult.Success<FormSnippetData>>(
                registry.resolve(SnippetTypes.FORM)!!.decode(byType.getValue(SnippetTypes.FORM).data),
            ).value
        assertEquals(1, form.switches.size)
        assertEquals(1, form.checkboxes.size)
        assertEquals(1, form.sliders.size)
    }

    @Test
    fun resolvesResponsiveGridColumnsAndCarouselWidthsAcrossBreakpoints() {
        val columnSpec = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4)
        assertEquals(2, resolveResponsiveColumns(2, columnSpec, WindowWidthClass.COMPACT))
        assertEquals(3, resolveResponsiveColumns(2, columnSpec, WindowWidthClass.MEDIUM))
        assertEquals(4, resolveResponsiveColumns(2, columnSpec, WindowWidthClass.EXPANDED))
        assertEquals(3, resolveResponsiveColumns(3, null, WindowWidthClass.EXPANDED))

        val widthSpec = ResponsiveIntSpec(compact = 180, medium = 220, expanded = 280)
        assertEquals(180, resolveResponsiveItemWidthDp(null, widthSpec, WindowWidthClass.COMPACT))
        assertEquals(220, resolveResponsiveItemWidthDp(null, widthSpec, WindowWidthClass.MEDIUM))
        assertEquals(280, resolveResponsiveItemWidthDp(null, widthSpec, WindowWidthClass.EXPANDED))
    }

    private companion object {
        val CONTAINERS_PAYLOAD_JSON =
            """
            {
              "page_id": "containers",
              "title": "Container Showcase",
              "snippets": [
                {
                  "snippet_id": "hdr",
                  "snippet_type": "header_v1",
                  "data": {
                    "title": { "text": "SDUI Containers" },
                    "avatar": { "initials": "AC", "size_dp": 44 }
                  }
                },
                {
                  "snippet_id": "car",
                  "snippet_type": "carousel",
                  "data": {
                    "title": { "text": "Featured" },
                    "item_width_dp": 240,
                    "spacing_dp": 12,
                    "items": [
                      {
                        "snippet_id": "car_card",
                        "snippet_type": "card_v1",
                        "data": {
                          "title": { "text": "Card 1" },
                          "avatar": { "initials": "SR" },
                          "skeletons": [{ "kind": "circle", "width_dp": 36, "height_dp": 36 }]
                        }
                      }
                    ]
                  }
                },
                {
                  "snippet_id": "grd",
                  "snippet_type": "grid",
                  "data": {
                    "columns": 2,
                    "spacing_dp": 8,
                    "items": [
                      {
                        "snippet_id": "g1",
                        "snippet_type": "metric_card",
                        "data": { "label": { "text": "A" }, "value": { "text": "1" } }
                      },
                      {
                        "snippet_id": "g2",
                        "snippet_type": "metric_card",
                        "data": { "label": { "text": "B" }, "value": { "text": "2" } }
                      }
                    ]
                  }
                },
                {
                  "snippet_id": "tbs",
                  "snippet_type": "tabs",
                  "data": {
                    "selected_index": 0,
                    "tabs": [
                      {
                        "id": "overview",
                        "title": "Overview",
                        "badge": { "label": "LIVE" },
                        "snippets": [
                          {
                            "snippet_id": "t1",
                            "snippet_type": "spacer",
                            "data": { "spacer": { "height_dp": 8 } }
                          }
                        ]
                      },
                      {
                        "id": "details",
                        "title": "Details",
                        "snippets": []
                      }
                    ]
                  }
                },
                {
                  "snippet_id": "acc",
                  "snippet_type": "accordion",
                  "data": {
                    "title": { "text": "More Info" },
                    "initially_expanded": true,
                    "snippets": [
                      {
                        "snippet_id": "a1",
                        "snippet_type": "action_row",
                        "data": { "title": { "text": "Row" } }
                      }
                    ]
                  }
                },
                {
                  "snippet_id": "frm",
                  "snippet_type": "form_v1",
                  "data": {
                    "form_id": "controls",
                    "switches": [{ "field_id": "sw", "label": "Compact", "checked": true }],
                    "checkboxes": [{ "field_id": "cb", "label": "Strict", "checked": false }],
                    "sliders": [{ "field_id": "sl", "label": "Density", "value": 2, "min_value": 1, "max_value": 4 }]
                  }
                }
              ],
              "pagination": {
                "next_cursor": "c2",
                "has_more": true,
                "load_more_action": {
                  "id": "load_more",
                  "type": "show_toast",
                  "payload": { "message": "More" }
                }
              },
              "pull_to_refresh": {
                "id": "ptr",
                "type": "refresh_page",
                "payload": { "page_id": "containers", "show_loader": false }
              }
            }
            """.trimIndent()
    }
}
