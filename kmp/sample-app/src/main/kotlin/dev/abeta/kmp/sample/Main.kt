package dev.abeta.kmp.sample

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

private const val DEFAULT_WINDOW_WIDTH_DP = 1040
private const val DEFAULT_WINDOW_HEIGHT_DP = 820

internal data class SampleCliOptions(
    val headless: Boolean = false,
    val bffUrl: String? = null,
    val captureScreenshotsDir: String? = null,
)

internal fun parseCliOptions(args: Array<String>): SampleCliOptions {
    var headless = false
    var bffUrl: String? = null
    var captureDir: String? = null
    var index = 0
    while (index < args.size) {
        val arg = args[index]
        when {
            arg == "--headless" -> {
                headless = true
            }

            arg.startsWith("--bff-url=") -> {
                bffUrl = arg.substringAfter("--bff-url=").trim().takeIf { it.isNotEmpty() }
            }

            arg == "--bff-url" && index + 1 < args.size -> {
                bffUrl = args[index + 1].trim().takeIf { it.isNotEmpty() }
                index += 1
            }

            arg.startsWith("--capture-screenshots=") -> {
                captureDir = arg.substringAfter("--capture-screenshots=").trim().takeIf { it.isNotEmpty() }
            }

            arg == "--capture-screenshots" && index + 1 < args.size -> {
                captureDir = args[index + 1].trim().takeIf { it.isNotEmpty() }
                index += 1
            }
        }
        index += 1
    }
    return SampleCliOptions(
        headless = headless,
        bffUrl = bffUrl,
        captureScreenshotsDir = captureDir,
    )
}

/**
 * Entry point for the Abeta KMP SDUI Sample application.
 *
 * Supports `--headless` for CI/smoke verification, `--capture-screenshots <dir>` for off-screen
 * PNG exports, and `--bff-url <url>` to connect to a live Go BFF.
 */
fun main(args: Array<String>) {
    val options = parseCliOptions(args)
    if (options.captureScreenshotsDir != null) {
        exportDemoScreenshots(options.captureScreenshotsDir, options.bffUrl)
        return
    }
    if (options.headless) {
        runHeadless(options.bffUrl)
        return
    }
    launchDesktopWindow(options.bffUrl)
}

private fun runHeadless(bffUrl: String?) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var container: SampleContainer? = null
    try {
        val summary =
            runBlocking {
                val created =
                    SampleFactory.create(
                        scope = scope,
                        bffBaseUrl = bffUrl,
                    )
                container = created
                created.runHeadlessVerification()
            }
        println("Abeta Sample Headless Verification: OK")
        println("  loadedPageId=${summary.loadedPageId}")
        println("  snippetCount=${summary.snippetCount}")
        println("  formSubmittedPageId=${summary.formSubmittedPageId}")
        println("  containersPageId=${summary.containersPageId}")
        println("  containersSnippetTypes=${summary.containersSnippetTypes}")
        println("  storePageId=${summary.storePageId}")
        println("  watchPageId=${summary.watchPageId}")
        println("  celebrationTriggered=${summary.celebrationTriggered}")
        println("  toastMessage=${summary.toastMessage}")
        println("  recordedEvents=${summary.recordedEvents}")
        println("  recordedImpressions=${summary.recordedImpressions}")
        println("  sessionKeys=${summary.sessionKeys}")
    } finally {
        container?.close()
        scope.cancel()
    }
}

private fun launchDesktopWindow(bffUrl: String?) {
    application {
        val scope = rememberCoroutineScope()
        val container =
            remember(bffUrl) {
                SampleFactory.create(
                    scope = scope,
                    bffBaseUrl = bffUrl,
                    platformBridges = PlatformBridgeBundle.createPlatformDefault(),
                )
            }
        DisposableEffect(container) {
            onDispose { container.close() }
        }
        LaunchedEffect(container) {
            container.loadInitialScreen()
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Abeta KMP SDUI Sample App",
            state = rememberWindowState(width = DEFAULT_WINDOW_WIDTH_DP.dp, height = DEFAULT_WINDOW_HEIGHT_DP.dp),
        ) {
            SampleRoot(container = container)
        }
    }
}
