package dev.abeta.kmp.layout.ios

import androidx.compose.ui.window.ComposeUIViewController
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.layout.ui.SduiHostedRoot
import platform.UIKit.UIViewController

/**
 * Creates a self-contained iOS / iPadOS [UIViewController] hosting [dev.abeta.kmp.layout.ui.SduiScreen]
 * themed with [dev.abeta.kmp.tokens.DesignTheme] and wired via an isolated Koin application.
 */
fun createSduiViewController(
    screenId: String = "home",
    bffBaseUrl: String? = null,
    logger: AppLogger = KermitAppLogger(),
): UIViewController =
    ComposeUIViewController {
        SduiHostedRoot(
            screenId = screenId,
            bffBaseUrl = bffBaseUrl,
            logger = logger,
            platformLabel = "iOS",
        )
    }
