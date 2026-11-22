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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StorybookComposeTest {
    @Test
    fun switchingToControlsSnippetsAndLiveBffStoriesRendersWithoutCrash() =
        runComposeUiTest {
            setContent {
                StorybookApp()
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
            onNodeWithText("Core OS Bridges & Capabilities").performScrollTo().assertIsDisplayed()
            onNodeWithText("Shake Error Test").performScrollTo().performClick()

            onNodeWithText("Discovery, Search & Swipe").performScrollTo().performClick()
            onNodeWithText("Search Bar & Live Query Binding").performScrollTo().assertIsDisplayed()
            onNodeWithText("Category & Filter Chip Groups").performScrollTo().assertIsDisplayed()
            onNodeWithText("Abeta Spark · Swipe Discovery Deck").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Like").performScrollTo().performClick()
            onNodeWithText("It's a Match!").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("It's a Match!").assertDoesNotExist()
        }

    @Test
    fun allSixteenStoriesRenderCleanlyInStorybook() {
        assertEquals(16, StoryCatalog.entries.size)
        StoryCatalog.entries.forEach { entry ->
            runComposeUiTest {
                setContent {
                    StorybookApp(initialStoryId = entry.id)
                }
                onNodeWithText(entry.subtitle).assertIsDisplayed()
            }
        }
    }

    @Test
    fun animationNudgesStoryAdvancesTrackerUpdatesCounterAndTogglesUrgency() =
        runComposeUiTest {
            setContent {
                StorybookApp(initialStoryId = StoryCatalog.STORY_ANIMATION_NUDGES)
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
                StorybookApp(initialStoryId = StoryCatalog.STORY_SNIPPET_CRAFTER)
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
                StorybookApp(initialStoryId = StoryCatalog.STORY_E2E_JOURNEYS)
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
