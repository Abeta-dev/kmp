package dev.abeta.kmp.storybook

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StorybookComposeTest {
    @Test
    fun switchingToControlsSnippetsAndLiveBffStoriesRendersWithoutCrash() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialReducedMotion = true)
            }

            onNodeWithText("Welcome to Abeta KMP Storybook").assertIsDisplayed()

            onNodeWithText("Controls, Avatar & Skeleton").performScrollTo().performClick()
            onNodeWithText("Interactive Form Controls").assertIsDisplayed()
            onNodeWithText("Auto-Sync SDUI Cache").assertIsDisplayed()
            onNodeWithText("Loading Skeletons").performScrollTo().assertIsDisplayed()

            onNodeWithText("A+").performClick()
            onNodeWithText("115%").assertIsDisplayed()
            onNodeWithText("115%").performClick()
            onNodeWithText("1.0x").assertIsDisplayed()

            onNodeWithText("All 21 Snippets").performScrollTo().performClick()
            onNodeWithText("SDUI Snippet Registry Gallery").assertIsDisplayed()
            onNodeWithText("21 / 21 Registered").assertIsDisplayed()
            onNode(hasText("Accordion") and hasClickAction()).performClick()
            onNodeWithText("SnippetEnvelope Wire JSON (accordion)").performScrollTo().assertIsDisplayed()

            onNodeWithText("Motion & Quick-Commerce").performScrollTo().performClick()
            onNodeWithText("Declarative MotionSpec Presets").assertIsDisplayed()
            onNodeWithText("Cart: 1 items · $3.49").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Add item").performClick()
            onNodeWithText("Cart: 2 items · $8.48").assertIsDisplayed()
            onNodeWithText("Simulate Express Checkout").performClick()
            onNodeWithText("Order Confirmed!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("Order Confirmed!").assertDoesNotExist()

            onNodeWithText("Live Go BFF Explorer").performScrollTo().performClick()
            onNodeWithText("Live Go BFF Screen Explorer").assertIsDisplayed()
            onNodeWithText("Fetch Screen").assertIsDisplayed()
            onNodeWithText("Preview BottomSheet").performClick()
            onNodeWithText("BottomSheet Overlay Preview").assertIsDisplayed()
            onNodeWithText("OK").performClick()
            onNodeWithText("BottomSheet Overlay Preview").assertDoesNotExist()

            onNodeWithText("Responsive & OS Bridges").performScrollTo().performClick()
            onNodeWithText("Adaptive Form-Factor Breakpoints").assertIsDisplayed()
            onNodeWithText("Watch (260dp)").performClick()
            onNodeWithText("Core OS Bridges & Capabilities").assertIsDisplayed()
            onNodeWithText("Shake Error Test").performScrollTo().performClick()

            onNodeWithText("Discovery, Search & Swipe").performScrollTo().performClick()
            onNodeWithText("Search Bar & Live Query Binding").assertIsDisplayed()
            onNodeWithText("Category & Filter Chip Groups").assertIsDisplayed()
            onNodeWithText("Abeta Spark · Swipe Discovery Deck").assertIsDisplayed()
            onNodeWithContentDescription("Like").performScrollTo().performClick()
            onNodeWithText("It's a Match!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("It's a Match!").assertDoesNotExist()
        }

    @Test
    fun allTwentyStoriesRenderCleanlyInStorybook() {
        assertEquals(20, StoryCatalog.entries.size)
        StoryCatalog.entries.forEach { entry ->
            runComposeUiTest {
                setContent {
                    StorybookApp(initialStoryId = entry.id, initialReducedMotion = true)
                }
                onNodeWithText(entry.subtitle).assertIsDisplayed()
            }
        }
    }

    @Test
    fun extendedAtomSelectionStoryInteractsWithRadioSelectOtpChipsAndSegmentedControl() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_SELECTION_OTP, initialReducedMotion = true)
            }

            onNodeWithText("UiRadioGroup · Delivery Slot").assertIsDisplayed()
            onNodeWithText("Scheduled 6–7 PM").performScrollTo().performClick()
            onNodeWithText("SCHEDULED").assertIsDisplayed()

            onNodeWithText("UiSelect · Bank & Unit Dropdown").performScrollTo().assertIsDisplayed()
            onNodeWithText("Abeta Wallet · $2,480.50").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiOtpInput · OTP & Masked PIN").performScrollTo().assertIsDisplayed()
            onNode(hasText("4-Digit") and hasClickAction()).performScrollTo().performClick()
            onNode(hasText("Masked PIN") and hasClickAction()).performScrollTo().performClick()
            onNode(hasText("Error State") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("4-Digit Security PIN").performScrollTo().assertIsDisplayed()
            onNodeWithText("Invalid verification code — please retry").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiChip · Selectable & Removable").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Remove Organic").performScrollTo().performClick()
            onNodeWithText("Restore Chips").performScrollTo().performClick()
            onNodeWithText("Organic").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiSegmentedControl · Sliding Pill").performScrollTo().assertIsDisplayed()
            onNodeWithText("Bank Rail").performScrollTo().performClick()
            onNodeWithText("BANK").performScrollTo().assertIsDisplayed()
            onNodeWithText("OtpInputAtom Wire JSON").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun extendedAtomCommerceStoryInteractsWithPriceRatingBillQrAndStepper() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_COMMERCE_FINTECH, initialReducedMotion = true)
            }

            onNodeWithText("UiPrice · MRP, Discount & Unit").assertIsDisplayed()
            onNodeWithText("$3.49").assertIsDisplayed()
            onNodeWithText("30% OFF").assertIsDisplayed()

            onNodeWithText("UiRating · Half-Star & Interactive").performScrollTo().assertIsDisplayed()
            onNodeWithText("4.8 (1420)").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Rate 5 of 5").performScrollTo().performClick()
            onNodeWithText("5 / 5 ★").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiKeyValueRow · Checkout Bill Breakdown").performScrollTo().assertIsDisplayed()
            onNodeWithText("Grand Total").performScrollTo().assertIsDisplayed()
            onNodeWithText("$14.25").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiQrCode · Deterministic QR Generator").performScrollTo().assertIsDisplayed()
            onNodeWithText("QR · PLS-7712 · ARENA GOLD (2x)").performScrollTo().assertIsDisplayed()
            onNode(hasText("Pay Scan") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("Scan to Receive · @abeta.user").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiStepper · ADD ↔ Quantity Morph").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Add item").performScrollTo().performClick()
            onNodeWithText("Basket $10.07").performScrollTo().assertIsDisplayed()
            onNodeWithText("QrCodeAtom Wire JSON").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun extendedAtomTelemetryStoryInteractsWithBadgesAlertsStatsCountdownAndCounter() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_FEEDBACK_TELEMETRY, initialReducedMotion = true)
            }

            onNodeWithText("UiBadge · Counts, Overflow & Live Dot").assertIsDisplayed()
            onNodeWithText("99+").assertIsDisplayed()
            onNodeWithText("+1").performScrollTo().performClick()
            onNodeWithText("Count (4)").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiAlertBanner · 4 Semantic Variants").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Dismiss Info · Express Slot Reserved").performScrollTo().performClick()
            onNodeWithText("Restore Banners").performScrollTo().performClick()
            onNodeWithText("Info · Express Slot Reserved").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiStatTile · Positive & Negative KPIs").performScrollTo().assertIsDisplayed()
            onNodeWithText("▲ +18.4%").performScrollTo().assertIsDisplayed()
            onNodeWithText("▼ -2.1%").performScrollTo().assertIsDisplayed()

            onNodeWithText("UiCountdown · Live Urgency Timer").performScrollTo().assertIsDisplayed()
            onNodeWithText("12s Urgent").performScrollTo().performClick()

            onNodeWithText("UiRollingCounter · Tabular Odometer").performScrollTo().assertIsDisplayed()
            onNodeWithText("+$1.50").performScrollTo().performClick()
            onNodeWithText("$13.90").performScrollTo().assertIsDisplayed()
            onNodeWithText("CountdownAtom Wire JSON").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun superAppDemosStorySwitchesAcrossAllSevenVerticalsAndWatchInteractively() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_SUPER_APP_DEMOS, initialReducedMotion = true)
            }

            onNodeWithText("/screens/fresh-home").assertIsDisplayed()
            onNode(hasText("Abeta Bites") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/bites-home").assertIsDisplayed()
            onNode(hasText("Abeta Pulse") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/pulse-home").assertIsDisplayed()
            onNode(hasText("Abeta Mart") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/mart-home").assertIsDisplayed()
            onNode(hasText("Abeta Spark") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/spark-deck").assertIsDisplayed()
            onNode(hasText("Abeta Trade") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/trade-home").assertIsDisplayed()
            onNode(hasText("Abeta Pay") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/pay-home").assertIsDisplayed()
            onNode(hasText("Abeta Watch") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/watch-status").assertIsDisplayed()
        }

    @Test
    fun superAppDemoRepositoryAndApiExecutorServeAllMultiStepWorkflowRoutes() =
        runBlocking {
            val repo = StorybookDemoScreenRepository()
            val routes =
                listOf(
                    "fresh-item-detail",
                    "fresh-cart?slot=scheduled&promo=FRESH20",
                    "fresh-order-track?stage=2",
                    "bites-cart?promo=BITES50",
                    "bites-order-track?stage=3",
                    "mart-checkout?shipping=standard",
                    "mart-order-track?stage=2",
                    "pulse-checkout?tier=arena_gold",
                    "pulse-ticket-pass",
                    "spark-chat",
                    "spark-preferences",
                    "trade-quotes",
                    "trade-po-track?stage=2",
                    "pay-home",
                    "pay-transfer?amount=100",
                    "pay-cards",
                    "pay-receipt?stage=2",
                )
            for (route in routes) {
                val res = repo.fetchScreen(route)
                assertTrue(res is AppResult.Success, "Failed to fetch $route")
            }

            val executor = StorybookDemoApiExecutor()
            val endpoints =
                listOf(
                    "/api/v1/fresh/orders/express" to "fresh-order-track?stage=2",
                    "/api/v1/bites/orders" to "bites-order-track?stage=2",
                    "/api/v1/mart/orders" to "mart-order-track?stage=2",
                    "/api/v1/pulse/checkout" to "pulse-ticket-pass",
                    "/api/v1/trade/rfq" to "trade-quotes",
                    "/api/v1/trade/po" to "trade-po-track?stage=2",
                    "/api/v1/pay/transfer" to "pay-receipt?stage=2",
                )
            for ((endpoint, expectedRoute) in endpoints) {
                val out = executor.execute(CallApiPayload(endpoint = endpoint), ActionContext())
                assertTrue(out is AppResult.Success, "Failed endpoint $endpoint")
                val followUpRoutes = out.value.followUpActions.map { it.payload.toString() }
                assertTrue(followUpRoutes.any { expectedRoute in it }, "Missing $expectedRoute for $endpoint")
            }
            val chatOut = executor.execute(CallApiPayload(endpoint = "/api/v1/spark/chat"), ActionContext())
            assertTrue(chatOut is AppResult.Success && chatOut.value.followUpActions.isNotEmpty())
        }

    @Test
    fun superAppPayAndFreshMultiStepFlowsNavigateInteractively() =
        runComposeUiTest {
            setContent {
                StorybookApp(
                    initialStoryId = StoryCatalog.STORY_SUPER_APP_DEMOS,
                    initialAppScreenId = "pay-home",
                    initialReducedMotion = true,
                )
            }

            onNodeWithText("Hide").performClick()
            onNodeWithText("/screens/pay-home").assertIsDisplayed()
            onNodeWithText("•••• 4829 · ACTIVE").assertIsDisplayed()
            onNodeWithText("Manage Card").assertIsDisplayed().performClick()
            onNodeWithText("/screens/pay-cards").assertIsDisplayed()
            onNodeWithText("Back").performScrollTo().performClick()
            onNodeWithText("/screens/pay-home").assertIsDisplayed()
            onNodeWithContentDescription("Last Receipt").performClick()
            onNodeWithText("/screens/pay-receipt?stage=2").assertIsDisplayed()
            onNodeWithText("Advance Settlement").assertIsDisplayed().performClick()
            onNodeWithText("/screens/pay-receipt?stage=3").assertIsDisplayed()

            onNode(hasText("Abeta Fresh") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("/screens/fresh-home").assertIsDisplayed()
            onNodeWithText("Review Cart").assertIsDisplayed().performClick()
            onNodeWithText("/screens/fresh-cart").assertIsDisplayed()
            onNodeWithContentDescription("Navigate back").performClick()
            onNodeWithText("/screens/fresh-home").assertIsDisplayed()
            onNodeWithContentDescription("Track Fresh order").performClick()
            onNodeWithText("/screens/fresh-order-track?stage=3").assertIsDisplayed()
            onNodeWithText("Advance Delivery Stage").assertIsDisplayed().performClick()
            onNodeWithText("/screens/fresh-order-track?stage=4").assertIsDisplayed()
        }

    @Test
    fun animationNudgesStoryAdvancesTrackerUpdatesCounterAndTogglesUrgency() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_ANIMATION_NUDGES, initialReducedMotion = true)
            }

            onNodeWithText("Animation & Nudges Test Bench").assertIsDisplayed()
            onNodeWithText("PULSE").performScrollTo().assertIsDisplayed()
            onNodeWithText("GLOW_RING").performScrollTo().assertIsDisplayed()

            onNodeWithText("+$1.50").performScrollTo().performClick()
            onNodeWithText("$4.99").assertIsDisplayed()

            onNodeWithText("Set 12s Urgent").performScrollTo().performClick()
            onNodeWithText("STAGE 1 / 4").performScrollTo().assertIsDisplayed()
            onNodeWithText("Advance Rider Step").performScrollTo().performClick()
            onNodeWithText("STAGE 2 / 4").performScrollTo().assertIsDisplayed()
            onNodeWithText("Fresh produce packed & quality checked").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun snippetCrafterStoryTogglesAtomsAndUpdatesLivePreview() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_SNIPPET_CRAFTER, initialReducedMotion = true)
            }

            onNodeWithText("Preset Snippet Molds").assertIsDisplayed()
            onNodeWithText("FLASH COMBO").performScrollTo().assertIsDisplayed()

            onNode(hasText("Badge Tag") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("FLASH COMBO").assertDoesNotExist()

            onNode(hasText("Loyalty Stamp Card") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("Abeta Rewards · 6 of 8 Stamps").performScrollTo().assertIsDisplayed()
            onNode(hasText("Row") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("Live SduiJson Wire Payload (composed_block · ready for sdui-bff)")
                .performScrollTo()
                .assertIsDisplayed()
        }

    @Test
    fun endToEndJourneysStoryWalksFreshSparkAndTradeFlows() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_E2E_JOURNEYS, initialReducedMotion = true)
            }

            onNodeWithText("Super-App End-to-End Journeys").assertIsDisplayed()
            onNodeWithText("Place Express Order").performScrollTo().performClick()
            onNodeWithText("Express Order Placed!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("STEP 1 OF 4").performScrollTo().assertIsDisplayed()
            onNodeWithText("Advance Step").performScrollTo().performClick()
            onNodeWithText("STEP 2 OF 4").performScrollTo().assertIsDisplayed()

            onNode(hasText("Abeta Spark Match") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("Matches Inbox (0)").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Like").performScrollTo().performClick()
            onNodeWithText("It's a Match!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("Matches Inbox (1)").performScrollTo().assertIsDisplayed()

            onNode(hasText("Abeta Trade B2B RFQ") and hasClickAction()).performScrollTo().performClick()
            onNodeWithText("Submit Verified RFQ").performScrollTo().performClick()
            onNodeWithText("RFQ Dispatched!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("RFQ #TRD-1041 confirmed for 10 cartons · Sent to 3 GST suppliers")
                .performScrollTo()
                .assertIsDisplayed()
        }
}
