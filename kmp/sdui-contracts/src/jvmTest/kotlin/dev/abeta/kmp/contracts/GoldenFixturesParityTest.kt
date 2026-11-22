package dev.abeta.kmp.contracts

import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoldenFixturesParityTest {
    private fun loadGoldenJson(filename: String): String {
        var dir: File? = File(".").absoluteFile
        repeat(5) {
            val candidate = File(dir, "bff/testdata/golden/$filename")
            if (candidate.isFile) {
                return candidate.readText()
            }
            dir = dir?.parentFile
        }
        error("Could not locate bff/testdata/golden/$filename from working directory ${File(".").absolutePath}")
    }

    @Test
    fun decodesHomeScreenGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("home_screen.json"))
        assertHomeToolbarAndMeta(page)
        assertEquals(8, page.snippets.size)
        assertHomeLeadSnippets(page.snippets)
        assertHomeTailSnippets(page.snippets)
    }

    private fun assertHomeToolbarAndMeta(page: PageResponse) {
        assertEquals("home", page.pageId)
        assertEquals("Abeta Workspace", page.title)
        val toolbar = assertNotNull(page.toolbar)
        assertEquals("title.large", toolbar.title?.typography?.token)
        assertEquals("surface.primary", toolbar.bgColor?.token)
        assertEquals(2, toolbar.trailingActions.size)
        assertEquals(22, toolbar.trailingActions.first().sizeDp)
        assertEquals(
            "text.primary",
            toolbar.trailingActions
                .first()
                .color
                ?.token,
        )
        val bottomBar = assertNotNull(page.bottomBar)
        assertEquals("Refresh Dashboard", bottomBar.primaryButton?.label)
        assertEquals("Synced via Abeta BFF", bottomBar.helperText?.text)
        assertEquals("caption", bottomBar.helperText?.typography?.token)
        assertEquals(1, page.pageActions.size)
        assertEquals("page_loaded", page.tracking?.eventName)
    }

    private fun assertHomeLeadSnippets(snippets: List<SnippetEnvelope>) {
        val header = SduiJson.decodeFromJsonElement<HeaderSnippetData>(snippets[0].data)
        assertEquals(SnippetTypes.HEADER, snippets[0].snippetType)
        assertEquals("Welcome back, Builder", header.title.text)
        assertEquals("headline.small", header.title.typography?.token)
        assertEquals("sparkles", header.leadingIcon?.name)
        assertEquals("LIVE", header.trailingTag?.label)
        assertEquals("Catalog", header.trailingAction?.label)
        assertEquals(ButtonSize.SMALL, header.trailingAction?.size)
        assertEquals(ButtonVariant.OUTLINE, header.trailingAction?.variant)

        val banner = SduiJson.decodeFromJsonElement<BannerSnippetData>(snippets[1].data)
        assertEquals(SnippetTypes.BANNER, snippets[1].snippetType)
        assertEquals("Zero-Release UI Updates", banner.title.text)
        assertNotNull(banner.body)
        assertEquals("v1.0", banner.tag?.label)
        assertEquals("Open Form Demo", banner.cta?.label)
        assertEquals(1f, banner.layout?.border?.widthDp)

        val metric = SduiJson.decodeFromJsonElement<MetricCardSnippetData>(snippets[2].data)
        assertEquals(SnippetTypes.METRIC_CARD, snippets[2].snippetType)
        assertEquals("99.98%", metric.value.text)
        assertEquals("+0.04%", metric.delta?.label)
        assertEquals(ProgressKind.LINEAR, metric.progress?.kind)
        assertEquals("30-day SLO target", metric.progress?.label)
    }

    private fun assertHomeTailSnippets(snippets: List<SnippetEnvelope>) {
        val divider = SduiJson.decodeFromJsonElement<DividerSnippetData>(snippets[3].data)
        assertEquals(SnippetTypes.DIVIDER, snippets[3].snippetType)
        assertEquals(1f, divider.divider.thicknessDp)
        assertEquals(16, divider.divider.insetStartDp)
        assertEquals("divider.default", divider.divider.color?.token)

        val card = SduiJson.decodeFromJsonElement<CardSnippetData>(snippets[4].data)
        assertEquals(SnippetTypes.CARD, snippets[4].snippetType)
        assertEquals("Atomic Design System", card.title.text)
        assertNotNull(card.description)
        assertEquals(140, card.media?.heightDp)
        assertEquals(ImageScaleMode.CROP, card.media?.scaleMode)
        assertEquals(8, card.media?.cornerRadius?.all)
        assertEquals("STABLE", card.badge?.label)
        assertEquals("Explore Tokens", card.primaryButton?.label)
        assertEquals("Copy Endpoint", card.secondaryButton?.label)
        assertEquals(
            1,
            card.secondaryButton
                ?.onClick
                ?.secondaryActions
                ?.size,
        )

        val spacer = SduiJson.decodeFromJsonElement<SpacerSnippetData>(snippets[5].data)
        assertEquals(SnippetTypes.SPACER, snippets[5].snippetType)
        assertEquals(12, spacer.spacer.heightDp)

        val rowProfile = SduiJson.decodeFromJsonElement<ActionRowSnippetData>(snippets[6].data)
        assertEquals(SnippetTypes.ACTION_ROW, snippets[6].snippetType)
        assertEquals("PRO", rowProfile.trailingTag?.label)

        val rowForm = SduiJson.decodeFromJsonElement<ActionRowSnippetData>(snippets[7].data)
        assertEquals(SnippetTypes.ACTION_ROW, snippets[7].snippetType)
        assertEquals("edit_note", rowForm.leadingIcon?.name)
    }

    @Test
    fun decodesFormScreenGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("form_screen.json"))
        assertEquals("form-demo", page.pageId)
        assertEquals("arrow_back", page.toolbar?.navigationIcon?.name)
        assertEquals(2, page.snippets.size)

        val header = SduiJson.decodeFromJsonElement<HeaderSnippetData>(page.snippets[0].data)
        assertEquals(SnippetTypes.HEADER, page.snippets[0].snippetType)
        assertEquals("VALIDATED", header.trailingTag?.label)

        val form = SduiJson.decodeFromJsonElement<FormSnippetData>(page.snippets[1].data)
        assertEquals(SnippetTypes.FORM, page.snippets[1].snippetType)
        assertEquals("workspace_onboarding", form.formId)
        assertEquals(3, form.fields.size)

        assertEquals("full_name", form.fields[0].fieldId)
        assertTrue(form.fields[0].required)
        assertEquals(KeyboardKind.TEXT, form.fields[0].keyboardType)

        assertEquals("work_email", form.fields[1].fieldId)
        assertEquals(KeyboardKind.EMAIL, form.fields[1].keyboardType)
        assertNotNull(form.fields[1].regex)

        assertEquals("team_size", form.fields[2].fieldId)
        assertEquals(KeyboardKind.NUMBER, form.fields[2].keyboardType)

        val submit = assertNotNull(form.submitButton)
        assertEquals("Submit Setup", submit.label)
        assertEquals(3, submit.onClick?.validations?.size)
        assertEquals(
            "full_name",
            submit.onClick
                ?.validations
                ?.get(0)
                ?.fieldId,
        )
        assertEquals(
            "required",
            submit.onClick
                ?.validations
                ?.get(0)
                ?.rule,
        )
        assertEquals(1, submit.onClick?.secondaryActions?.size)

        val cancel = assertNotNull(form.cancelButton)
        assertEquals("Cancel", cancel.label)
        assertEquals(ButtonVariant.OUTLINE, cancel.variant)
    }

    @Test
    fun decodesActionBatchGoldenFixture() {
        val batch = SduiJson.decodeFromString<ActionRef>(loadGoldenJson("action_batch.json"))
        assertEquals("batch_onboarding_submit", batch.id)
        assertEquals("batch", batch.type)
        assertEquals(1, batch.secondaryActions.size)
        assertEquals("track_onboarding_batch", batch.secondaryActions.first().id)

        val nestedActions = SduiJson.decodeFromJsonElement<List<ActionRef>>(batch.payload.getValue("actions"))
        assertEquals(2, nestedActions.size)
        assertEquals("save_state", nestedActions[0].type)
        assertEquals("call_api", nestedActions[1].type)
        assertEquals(2, nestedActions[1].validations.size)
        assertEquals("work_email", nestedActions[1].validations[1].fieldId)
        assertEquals("regex", nestedActions[1].validations[1].rule)
        assertNotNull(nestedActions[1].validations[1].value)
    }

    @Test
    fun decodesAllNineSnippetDataDtosFromWireJson() {
        val emptyStateJson =
            """
            {
              "title": { "text": "Nothing here", "typography": { "token": "title.medium" } },
              "message": { "text": "Create your first workflow.", "typography": { "token": "body.medium" } },
              "icon": { "name": "info", "size_dp": 32, "color": { "token": "text.tertiary" } },
              "action_button": { "id": "btn_create", "label": "Create Workflow", "variant": "primary", "size": "md" }
            }
            """.trimIndent()
        val emptyState = SduiJson.decodeFromString<EmptyStateSnippetData>(emptyStateJson)
        assertEquals("Nothing here", emptyState.title.text)
        assertEquals("Create your first workflow.", emptyState.message?.text)
        assertEquals(32, emptyState.icon?.sizeDp)
        assertEquals("Create Workflow", emptyState.actionButton?.label)
        assertEquals(ButtonSize.MEDIUM, emptyState.actionButton?.size)
    }

    @Test
    fun decodesContainersScreenGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("containers_screen.json"))
        assertEquals("containers", page.pageId)
        assertEquals("cursor_page_2", page.pagination?.nextCursor)
        assertEquals(true, page.pagination?.hasMore)
        assertEquals("refresh_page", page.pullToRefresh?.type)

        val header = SduiJson.decodeFromJsonElement<HeaderSnippetData>(page.snippets[0].data)
        assertEquals("AC", header.avatar?.initials)

        val carousel = SduiJson.decodeFromJsonElement<CarouselSnippetData>(page.snippets[1].data)
        assertEquals(SnippetTypes.CAROUSEL, page.snippets[1].snippetType)
        assertEquals(2, carousel.items.size)

        val grid = SduiJson.decodeFromJsonElement<GridSnippetData>(page.snippets[2].data)
        assertEquals(SnippetTypes.GRID, page.snippets[2].snippetType)
        assertEquals(2, grid.columns)

        val tabs = SduiJson.decodeFromJsonElement<TabsSnippetData>(page.snippets[3].data)
        assertEquals(SnippetTypes.TABS, page.snippets[3].snippetType)
        assertEquals("Overview", tabs.tabs.first().title)

        val accordion = SduiJson.decodeFromJsonElement<AccordionSnippetData>(page.snippets[4].data)
        assertEquals(SnippetTypes.ACCORDION, page.snippets[4].snippetType)
        assertTrue(accordion.snippets.isNotEmpty())

        val skeletonCard = SduiJson.decodeFromJsonElement<CardSnippetData>(page.snippets[5].data)
        assertEquals(3, skeletonCard.skeletons.size)

        val form = SduiJson.decodeFromJsonElement<FormSnippetData>(page.snippets[6].data)
        assertEquals(1, form.switches.size)
        assertEquals(1, form.checkboxes.size)
        assertEquals(1, form.sliders.size)
    }

    @Test
    fun decodesStoreHomeGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("store_home.json"))
        assertEquals("store-home", page.pageId)
        assertEquals("Abeta Fresh", page.title)
        assertEquals(MotionTransition.SLIDE_UP, page.floatingCart?.motion?.enter)
        assertEquals(MotionTransition.SLIDE_DOWN, page.floatingCart?.motion?.exit)
        assertEquals(
            MotionLoopEffect.PULSE,
            page.floatingCart
                ?.etaBadge
                ?.motion
                ?.loopEffect,
        )

        val bannerEnvelope = page.snippets[0]
        assertEquals(SnippetTypes.BANNER, bannerEnvelope.snippetType)
        assertEquals(MotionLoopEffect.PULSE, bannerEnvelope.motion?.loopEffect)

        val tabsEnvelope = page.snippets[2]
        assertEquals(SnippetTypes.TABS, tabsEnvelope.snippetType)
        val tabs = SduiJson.decodeFromJsonElement<TabsSnippetData>(tabsEnvelope.data)
        assertEquals("Fruits & Veg", tabs.tabs.first().title)

        val firstGridEnvelope =
            tabs.tabs
                .first()
                .snippets
                .first()
        val firstGrid = SduiJson.decodeFromJsonElement<GridSnippetData>(firstGridEnvelope.data)
        assertEquals(2, firstGrid.columnsByWidth?.compact)
        assertEquals(3, firstGrid.columnsByWidth?.medium)
        assertEquals(4, firstGrid.columnsByWidth?.expanded)
        assertEquals(1120, page.adaptiveLayout?.maxWidthDp)
        assertEquals(true, page.adaptiveLayout?.splitPaneEnabled)
        val productEnvelope = firstGrid.items.first()
        assertEquals(SnippetTypes.PRODUCT_CARD, productEnvelope.snippetType)
        val product = SduiJson.decodeFromJsonElement<ProductCardSnippetData>(productEnvelope.data)
        assertEquals("avocados", product.productId)
        assertEquals(349, product.unitPriceCents)
        assertEquals("cart_qty_avocados", product.stepper.fieldId)
        assertEquals(1, product.stepper.quantity)
    }

    @Test
    fun decodesInteractiveScreenGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("interactive_screen.json"))
        assertEquals("interactive-demo", page.pageId)
        assertEquals("Abeta Discovery", page.title)
        assertEquals(3, page.snippets.size)

        val searchEnvelope = page.snippets[0]
        assertEquals(SnippetTypes.SEARCH_BAR, searchEnvelope.snippetType)
        val search = SduiJson.decodeFromJsonElement<SearchBarSnippetData>(searchEnvelope.data)
        assertEquals("discovery_search", search.searchId)
        assertEquals("compose", search.query)
        assertEquals("Search", search.submitLabel)
        assertTrue(search.clearable)
        assertNotNull(search.onSubmit)
        assertNotNull(search.onClear)
        assertNotNull(search.onTrailingClick)

        val chipsEnvelope = page.snippets[1]
        assertEquals(SnippetTypes.CHIP_GROUP, chipsEnvelope.snippetType)
        val chips = SduiJson.decodeFromJsonElement<ChipGroupSnippetData>(chipsEnvelope.data)
        assertEquals("discovery_categories", chips.groupId)
        assertTrue(chips.multiSelect)
        assertEquals(3, chips.chips.size)
        assertEquals("chip_kmp", chips.chips.first().id)
        assertTrue(chips.chips.first().selected)

        val deckEnvelope = page.snippets[2]
        assertEquals(SnippetTypes.SWIPE_DECK, deckEnvelope.snippetType)
        val deck = SduiJson.decodeFromJsonElement<SwipeDeckSnippetData>(deckEnvelope.data)
        assertEquals("builders_deck", deck.deckId)
        assertEquals("Connect", deck.likeLabel)
        assertEquals("Skip", deck.nopeLabel)
        assertEquals("Boost", deck.superLikeLabel)
        assertEquals(2, deck.cards.size)
        assertEquals("card_maya_chen", deck.cards.first().cardId)
        assertEquals(
            "2 km away",
            deck.cards
                .first()
                .distanceText
                ?.text,
        )
        assertNotNull(deck.onUndo)
    }

    @Test
    fun decodesNudgesAndComposedGoldenFixture() {
        val page = SduiJson.decodeFromString<PageResponse>(loadGoldenJson("nudges_and_composed_screen.json"))
        assertEquals("nudges-and-composed-demo", page.pageId)
        assertEquals("Abeta Live Deals & Tracker", page.title)
        assertEquals(6, page.snippets.size)
        assertEquals(2500, page.floatingCart?.freeDeliveryThresholdCents)
        assertEquals("FREE express delivery unlocked!", page.floatingCart?.unlockedLabel)
        assertEquals(
            MotionLoopEffect.FLOAT,
            page.floatingCart
                ?.etaBadge
                ?.motion
                ?.loopEffect,
        )

        val nudge = SduiJson.decodeFromJsonElement<NudgeBarSnippetData>(page.snippets[0].data)
        assertEquals(SnippetTypes.NUDGE_BAR, page.snippets[0].snippetType)
        assertEquals("free_delivery_nudge", nudge.nudgeId)
        assertEquals("bottom", nudge.pointerEdge)
        assertEquals(MotionLoopEffect.WIGGLE, nudge.badge?.motion?.loopEffect)
        assertEquals(MotionLoopEffect.HEARTBEAT, nudge.countdown?.motion?.loopEffect)
        assertEquals(420, nudge.countdown?.remainingSeconds)
        assertTrue(nudge.dismissible)

        val marquee = SduiJson.decodeFromJsonElement<MarqueeStripSnippetData>(page.snippets[1].data)
        assertEquals(SnippetTypes.MARQUEE_STRIP, page.snippets[1].snippetType)
        assertEquals("fresh_live_ticker", marquee.stripId)
        assertEquals(3, marquee.items.size)
        assertEquals(48, marquee.velocityDpPerSec)
        assertEquals(MotionLoopEffect.MARQUEE, page.snippets[1].motion?.loopEffect)

        val tracker = SduiJson.decodeFromJsonElement<OrderTrackerSnippetData>(page.snippets[2].data)
        assertEquals(SnippetTypes.ORDER_TRACKER, page.snippets[2].snippetType)
        assertEquals("order_af_4092", tracker.trackerId)
        assertEquals("#AF-4092", tracker.orderCode)
        assertEquals(4, tracker.steps.size)
        assertEquals("Aarav K.", tracker.riderName)
        assertEquals(480, tracker.etaCountdown?.remainingSeconds)

        val flashDeal = SduiJson.decodeFromJsonElement<ComposedBlockSnippetData>(page.snippets[3].data)
        assertEquals(SnippetTypes.COMPOSED_BLOCK, page.snippets[3].snippetType)
        assertEquals("flash_deal_card", flashDeal.recipeName)
        assertEquals("deal_hass_avocados_root", flashDeal.root.nodeId)
        assertEquals(ComposedNodeKind.CARD, flashDeal.root.kind)
        assertEquals(MotionLoopEffect.GLOW_RING, flashDeal.root.motion?.loopEffect)

        val loyalty = SduiJson.decodeFromJsonElement<ComposedBlockSnippetData>(page.snippets[4].data)
        assertEquals("loyalty_stamp_card", loyalty.recipeName)
        val promo = SduiJson.decodeFromJsonElement<ComposedBlockSnippetData>(page.snippets[5].data)
        assertEquals("live_eta_promo_card", promo.recipeName)
    }
}
