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

            onNodeWithText("All 14 Snippets").performScrollTo().performClick()
            onNodeWithText("SDUI Snippet Registry Gallery").assertIsDisplayed()
            onNodeWithText("14 / 14 Registered").assertIsDisplayed()
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
        }

    @Test
    fun allTwelveStoriesRenderCleanlyInStorybook() {
        StoryCatalog.entries.forEach { entry ->
            runComposeUiTest {
                setContent {
                    StorybookApp(initialStoryId = entry.id)
                }
                onNodeWithText(entry.subtitle).assertIsDisplayed()
            }
        }
    }
}
