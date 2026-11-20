package dev.abeta.kmp.storybook

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * WebAssembly browser entry point mounting [StorybookApp] into `#storybookRoot`.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "storybookRoot") {
        StorybookApp()
    }
}
