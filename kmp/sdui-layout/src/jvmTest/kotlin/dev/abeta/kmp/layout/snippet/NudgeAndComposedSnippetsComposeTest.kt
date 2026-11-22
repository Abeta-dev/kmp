package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NudgeAndComposedSnippetsComposeTest {
    @Test
    fun nudgeBarRendersCountdownProgressCtaAndDismissesLocallyWithAction() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val ctaAction = ActionRef(id = "nudge_cta", type = "navigate")
            val dismissAction = ActionRef(id = "nudge_dismissed", type = "track_event")
            val nudgeData =
                NudgeBarSnippetData(
                    nudgeId = "flash_nudge",
                    title = TextAtom(text = "Flash savings active"),
                    subtitle = TextAtom(text = "Checkout before timer ends"),
                    icon = IconAtom(name = "star"),
                    badge = TagAtom(label = "HOT", variant = TagVariant.BRAND),
                    countdown = CountdownAtom(countdownId = "nudge_cd", remainingSeconds = 180),
                    progress = 0.65f,
                    cta = ButtonAtom(label = "Claim Now", onClick = ctaAction),
                    dismissible = true,
                    pointerEdge = "top",
                    onDismiss = dismissAction,
                )

            setContent {
                SnippetPageHost(
                    page =
                        PageResponse(
                            pageId = "nudge_test",
                            snippets = listOf(snippetEnvelope("nudge_1", SnippetTypes.NUDGE_BAR, nudgeData)),
                        ),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("nudge_bar_flash_nudge").assertIsDisplayed()
            onNodeWithText("Flash savings active").assertIsDisplayed()
            onNodeWithText("Checkout before timer ends").assertIsDisplayed()
            onNodeWithTag("countdown_nudge_cd").assertIsDisplayed()

            onNodeWithText("Claim Now").performClick()
            assertEquals("nudge_cta", capturedActions.last().id)

            onNodeWithTag("nudge_dismiss_flash_nudge").performClick()
            assertEquals("nudge_dismissed", capturedActions.last().id)
            onNodeWithTag("nudge_bar_flash_nudge").assertDoesNotExist()
        }

    @Test
    fun marqueeStripRendersItemsAndDispatchesItemClick() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val offerAction = ActionRef(id = "offer_fresh", type = "show_toast")
            val marqueeData =
                MarqueeStripSnippetData(
                    stripId = "promo_ticker",
                    items =
                        listOf(
                            MarqueeItem(
                                id = "fresh_20",
                                text = "20% OFF Fresh Produce",
                                icon = IconAtom(name = "star"),
                                badge = TagAtom(label = "LIVE", variant = TagVariant.SUCCESS),
                                onClick = offerAction,
                            ),
                            MarqueeItem(
                                id = "free_ship",
                                text = "Zero fee above $10",
                            ),
                        ),
                    velocityDpPerSec = 52,
                    separator = "•",
                )

            setContent {
                SnippetPageHost(
                    page =
                        PageResponse(
                            pageId = "marquee_test",
                            snippets = listOf(snippetEnvelope("m_1", SnippetTypes.MARQUEE_STRIP, marqueeData)),
                        ),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("marquee_strip_promo_ticker").assertIsDisplayed()
            onNodeWithText("20% OFF Fresh Produce").assertIsDisplayed()
            onNodeWithTag("marquee_item_fresh_20").performClick()
            assertEquals("offer_fresh", capturedActions.last().id)
        }

    @Test
    fun orderTrackerRendersTimelineStepsRiderCardAndActions() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val callAction = ActionRef(id = "call_rider", type = "open_url")
            val helpAction = ActionRef(id = "open_help", type = "open_dialog")
            val trackerData =
                OrderTrackerSnippetData(
                    trackerId = "live_order",
                    orderCode = "#AB-9021",
                    headline = TextAtom(text = "Arriving in 6 mins"),
                    etaCountdown = CountdownAtom(countdownId = "eta_timer", remainingSeconds = 360),
                    etaBadge = TagAtom(label = "EXPRESS", variant = TagVariant.SUCCESS),
                    riderName = "Rohan M.",
                    riderCaption = "EV Scooter · 4.95★",
                    steps =
                        listOf(
                            OrderTrackerStep(id = "placed", label = "Confirmed", completed = true),
                            OrderTrackerStep(id = "en_route", label = "On the way", caption = "400m", active = true),
                            OrderTrackerStep(id = "done", label = "Delivered"),
                        ),
                    primaryAction = ButtonAtom(label = "Call Rider", onClick = callAction),
                    secondaryAction =
                        ButtonAtom(
                            label = "Support",
                            variant = ButtonVariant.OUTLINE,
                            onClick = helpAction,
                        ),
                )

            setContent {
                SnippetPageHost(
                    page =
                        PageResponse(
                            pageId = "tracker_test",
                            snippets = listOf(snippetEnvelope("ot_1", SnippetTypes.ORDER_TRACKER, trackerData)),
                        ),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("order_tracker_live_order").assertIsDisplayed()
            onNodeWithText("#AB-9021").assertIsDisplayed()
            onNodeWithText("Arriving in 6 mins").assertIsDisplayed()
            onNodeWithTag("countdown_eta_timer").assertIsDisplayed()
            onNodeWithTag("order_step_placed").assertIsDisplayed()
            onNodeWithTag("order_step_en_route").assertIsDisplayed()
            onNodeWithTag("order_step_done").assertIsDisplayed()
            onNodeWithTag("order_tracker_rider_live_order").assertIsDisplayed()
            onNodeWithText("Rohan M.").assertIsDisplayed()

            onNodeWithText("Call Rider").performClick()
            assertEquals("call_rider", capturedActions.last().id)
            onNodeWithText("Support").performClick()
            assertEquals("open_help", capturedActions.last().id)
        }

    @Test
    fun composedBlockMoldsNestedAtomsSyncsStepperAndEnforcesMaxDepth() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val formState = mutableMapOf<String, String>()
            val blockData =
                sampleComposedBundleBlock(
                    incAction = ActionRef(id = "bundle_inc", type = "track_event"),
                    ctaAction = ActionRef(id = "bundle_cta", type = "show_toast"),
                )

            setContent {
                SnippetPageHost(
                    page =
                        PageResponse(
                            pageId = "composed_test",
                            snippets = listOf(snippetEnvelope("cb_1", SnippetTypes.COMPOSED_BLOCK, blockData)),
                        ),
                    onAction = capturedActions::add,
                    onFormUpdate = { k, v -> formState[k] = v },
                )
            }

            onNodeWithTag("composed_block_molded_bundle").assertIsDisplayed()
            onNodeWithText("AB").assertIsDisplayed()
            onNodeWithText("$14.99").assertIsDisplayed()
            onNodeWithTag("countdown_cb_cd").assertIsDisplayed()
            onNodeWithText("MOLDED").assertIsDisplayed()
            onNodeWithText("Depth 6 OK").assertIsDisplayed()
            onNodeWithText("Too Deep").assertDoesNotExist()

            onNodeWithContentDescription("Add item").performClick()
            assertEquals("1", formState["bundle_qty"])
            assertEquals("bundle_inc", capturedActions.last().id)

            onNodeWithText("Apply Bundle").performClick()
            assertEquals("bundle_cta", capturedActions.last().id)
        }

    private fun sampleDepthChain(): ComposedNode {
        val depth7Node =
            ComposedNode(nodeId = "depth_7", kind = ComposedNodeKind.TEXT, text = TextAtom(text = "Too Deep"))
        val depth6Node =
            ComposedNode(nodeId = "depth_6", kind = ComposedNodeKind.COLUMN, children = listOf(depth7Node))
        var chain =
            ComposedNode(
                nodeId = "depth_5",
                kind = ComposedNodeKind.COLUMN,
                children =
                    listOf(
                        ComposedNode(
                            nodeId = "depth_6_ok",
                            kind = ComposedNodeKind.TEXT,
                            text = TextAtom(text = "Depth 6 OK"),
                        ),
                        depth6Node,
                    ),
            )
        repeat(4) { idx ->
            chain = ComposedNode(nodeId = "wrap_$idx", kind = ComposedNodeKind.BOX, children = listOf(chain))
        }
        return chain
    }

    private fun sampleComposedTopRow(): ComposedNode =
        ComposedNode(
            nodeId = "top_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.AVATAR,
                        avatar = AvatarAtom(initials = "AB", sizeDp = 32),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = "$14.99",
                        counterTypography = TypographyTokenRef(token = "title.medium"),
                        counterColor = ColorTokenRef(token = "brand.primary"),
                        weight = 1f,
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.COUNTDOWN,
                        countdown = CountdownAtom(countdownId = "cb_cd", remainingSeconds = 95),
                    ),
                ),
        )

    private fun sampleComposedBundleBlock(
        incAction: ActionRef,
        ctaAction: ActionRef,
    ): ComposedBlockSnippetData =
        ComposedBlockSnippetData(
            blockId = "molded_bundle",
            recipeName = "flash_bundle_v1",
            root =
                ComposedNode(
                    nodeId = "root_card",
                    kind = ComposedNodeKind.CARD,
                    children =
                        listOf(
                            sampleComposedTopRow(),
                            ComposedNode(
                                kind = ComposedNodeKind.FLOW_ROW,
                                children =
                                    listOf(
                                        ComposedNode(kind = ComposedNodeKind.TAG, tag = TagAtom(label = "MOLDED")),
                                        ComposedNode(kind = ComposedNodeKind.ICON, icon = IconAtom(name = "star")),
                                    ),
                            ),
                            ComposedNode(kind = ComposedNodeKind.PROGRESS, progress = ProgressAtom(progress = 0.8f)),
                            ComposedNode(kind = ComposedNodeKind.DIVIDER),
                            ComposedNode(kind = ComposedNodeKind.SPACER, spacingDp = 4),
                            ComposedNode(
                                kind = ComposedNodeKind.STEPPER,
                                stepper =
                                    StepperAtom(
                                        fieldId = "bundle_qty",
                                        label = "Bundle Pack",
                                        quantity = 0,
                                        onIncrement = incAction,
                                    ),
                            ),
                            ComposedNode(
                                kind = ComposedNodeKind.BUTTON,
                                button = ButtonAtom(label = "Apply Bundle", onClick = ctaAction),
                            ),
                            sampleDepthChain(),
                        ),
                ),
        )

    @Test
    fun floatingCartDockShowsFreeDeliveryProgressAndUnlocksWhenThresholdMet() =
        runComposeUiTest {
            val product =
                ProductCardSnippetData(
                    productId = "berries",
                    title = TextAtom(text = "Organic Berries"),
                    price = TextAtom(text = "$3.50"),
                    unitPriceCents = 350,
                    stepper = StepperAtom(fieldId = "qty_berries", quantity = 2, minQuantity = 0, maxQuantity = 10),
                )
            val page =
                PageResponse(
                    pageId = "cart_nudge_test",
                    snippets = listOf(snippetEnvelope("p_1", SnippetTypes.PRODUCT_CARD, product)),
                    floatingCart =
                        FloatingCartSpec(
                            label = "Cart",
                            cta = ButtonAtom(label = "Checkout"),
                            freeDeliveryThresholdCents = 1000,
                            nudgePrefix = "Add",
                            unlockedLabel = "FREE delivery unlocked!",
                        ),
                )

            setContent {
                SnippetPageHost(page = page, onAction = {})
            }

            onNodeWithTag("floating_cart_dock").assertIsDisplayed()
            onNodeWithTag("floating_cart_nudge").assertIsDisplayed()
            onNodeWithText("2 items · $7.00").assertIsDisplayed()
            onNodeWithText("Add $3.00 more for FREE delivery").assertIsDisplayed()

            onNodeWithContentDescription("Increase quantity").performClick()
            onNodeWithText("3 items · $10.50").assertIsDisplayed()
            onNodeWithText("FREE").assertIsDisplayed()
            onNodeWithText("FREE delivery unlocked!").assertIsDisplayed()
        }

    private inline fun <reified T> snippetEnvelope(
        id: String,
        type: String,
        model: T,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = id,
            snippetType = type,
            data = SduiJson.encodeToJsonElement(serializer<T>(), model) as JsonObject,
        )

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun SnippetPageHost(
        page: PageResponse,
        onAction: (ActionRef) -> Unit,
        onFormUpdate: (String, String) -> Unit = { _, _ -> },
        reducedMotion: Boolean = true,
    ) {
        val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
        val formValues = remember { mutableStateMapOf<String, String>() }
        DesignTheme(darkTheme = false, reducedMotion = reducedMotion) {
            SduiScreen(
                state =
                    LayoutUiState.Ready(
                        page = page,
                        formValues = formValues.toMap(),
                    ),
                snippetRegistry = registry,
                onAction = onAction,
                onFormValueChange = { fieldId, value ->
                    formValues[fieldId] = value
                    onFormUpdate(fieldId, value)
                },
            )
        }
    }
}
