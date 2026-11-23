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
    fun allSeventeenStoriesRenderCleanlyInStorybook() {
        assertEquals(17, StoryCatalog.entries.size)
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
