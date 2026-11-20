package dev.abeta.kmp.storybook

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window

private const val STORYBOOK_WINDOW_WIDTH_DP = 1240
private const val STORYBOOK_WINDOW_HEIGHT_DP = 860

/**
 * Opens a native Cocoa macOS [Window] running [StorybookApp] directly without JVM Swing dependencies.
 */
fun launchStorybookMacOsWindow() {
    Window(
        title = "Abeta KMP Design System Storybook",
        size = DpSize(width = STORYBOOK_WINDOW_WIDTH_DP.dp, height = STORYBOOK_WINDOW_HEIGHT_DP.dp),
    ) {
        StorybookApp()
    }
}
