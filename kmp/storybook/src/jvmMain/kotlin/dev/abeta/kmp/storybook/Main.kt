package dev.abeta.kmp.storybook

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private const val WINDOW_WIDTH_DP = 1240
private const val WINDOW_HEIGHT_DP = 860

/**
 * Desktop JVM entry point launching the Abeta KMP Storybook window or exporting screenshots
 * when passed `--capture-screenshots=<dir>`.
 */
fun main(args: Array<String>) {
    val captureDir = parseCaptureDir(args)
    if (captureDir != null) {
        exportStorybookScreenshots(captureDir)
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Abeta KMP Storybook",
            state =
                rememberWindowState(
                    width = WINDOW_WIDTH_DP.dp,
                    height = WINDOW_HEIGHT_DP.dp,
                ),
        ) {
            StorybookApp()
        }
    }
}

private fun parseCaptureDir(args: Array<String>): String? {
    var result: String? = null
    var idx = 0
    while (idx < args.size && result == null) {
        val arg = args[idx]
        result =
            when {
                arg.startsWith("--capture-screenshots=") -> {
                    arg.substringAfter("--capture-screenshots=").trim().takeIf { it.isNotEmpty() }
                }

                arg == "--capture-screenshots" && idx + 1 < args.size -> {
                    idx += 1
                    args[idx].trim().takeIf { it.isNotEmpty() }
                }

                else -> {
                    null
                }
            }
        idx += 1
    }
    return result
}
