package dev.abeta.kmp.layout.macos

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.layout.ui.SduiHostedRoot

private const val DEFAULT_WINDOW_WIDTH_DP = 1040
private const val DEFAULT_WINDOW_HEIGHT_DP = 820

/**
 * Opens a native Cocoa macOS [Window] hosting [dev.abeta.kmp.layout.ui.SduiScreen] powered by
 * Koin orchestration and macOS [dev.abeta.kmp.layout.platform.PlatformBridgeBundle].
 */
fun launchMacOsSduiWindow(
    screenId: String = "home",
    bffBaseUrl: String? = null,
    title: String = "Abeta SDUI (macOS Native)",
    widthDp: Int = DEFAULT_WINDOW_WIDTH_DP,
    heightDp: Int = DEFAULT_WINDOW_HEIGHT_DP,
    logger: AppLogger = KermitAppLogger(),
) {
    Window(
        title = title,
        size = DpSize(width = widthDp.dp, height = heightDp.dp),
    ) {
        SduiHostedRoot(
            screenId = screenId,
            bffBaseUrl = bffBaseUrl,
            logger = logger,
            platformLabel = "macOS",
        )
    }
}
