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
    val verticals: Boolean = false,
    val bffUrl: String? = null,
    val captureScreenshotsDir: String? = null,
    val captureVerticalsDir: String? = null,
)

@Suppress("CyclomaticComplexMethod")
internal fun parseCliOptions(args: Array<String>): SampleCliOptions {
    var headless = false
    var verticals = false
    var bffUrl: String? = null
    var captureDir: String? = null
    var captureVerticalsDir: String? = null
    var index = 0
    while (index < args.size) {
        val arg = args[index]
        when {
            arg == "--headless" -> {
                headless = true
            }

            arg == "--verticals" -> {
                verticals = true
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

            arg.startsWith("--capture-verticals=") -> {
                captureVerticalsDir = arg.substringAfter("--capture-verticals=").trim().takeIf { it.isNotEmpty() }
            }

            arg == "--capture-verticals" && index + 1 < args.size -> {
                captureVerticalsDir = args[index + 1].trim().takeIf { it.isNotEmpty() }
                index += 1
            }
        }
        index += 1
    }
    return SampleCliOptions(
        headless = headless,
        verticals = verticals,
        bffUrl = bffUrl,
        captureScreenshotsDir = captureDir,
        captureVerticalsDir = captureVerticalsDir,
    )
}

/**
 * Entry point for the Abeta KMP SDUI Super-App Showcase & Sample application.
 *
 * Supports `--headless`, `--verticals`, `--capture-screenshots <dir>`, `--capture-verticals <dir>`,
 * and `--bff-url <url>` to connect to a live Go BFF.
 */
fun main(args: Array<String>) {
    val options = parseCliOptions(args)
    when {
        options.captureVerticalsDir != null -> {
            exportVerticalScreenshots(options.captureVerticalsDir, options.bffUrl)
        }

        options.captureScreenshotsDir != null -> {
            exportDemoScreenshots(options.captureScreenshotsDir, options.bffUrl)
        }

        options.headless && options.verticals -> {
            runVerticalsHeadless(options.bffUrl)
        }

        options.headless -> {
            runHeadless(options.bffUrl)
        }

        else -> {
            launchDesktopWindow(options.bffUrl, initialVerticals = options.verticals)
        }
    }
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

private fun runVerticalsHeadless(bffUrl: String?) {
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
                created.runVerticalsHeadlessVerification()
            }
        check(summary.loadedVerticals.size == SampleContainer.VERTICAL_SCREENS.size) {
            "Expected ${SampleContainer.VERTICAL_SCREENS.size} verticals, got ${summary.loadedVerticals.size}"
        }
        check(summary.expressOrderCelebrationTriggered) { "Expected express order celebration to trigger" }
        check(summary.swipeCelebrationTriggered) { "Expected swipe celebration to trigger" }
        check(summary.bookingCelebrationTriggered) { "Expected booking celebration to trigger" }
        check(summary.rfqSubmitted) { "Expected B2B RFQ submission confirmation" }
        println("Abeta Sample Verticals Headless Verification: OK")
        println("  loadedVerticals=${summary.loadedVerticals}")
        println("  swipeCelebrationTriggered=${summary.swipeCelebrationTriggered}")
        println("  expressOrderCelebrationTriggered=${summary.expressOrderCelebrationTriggered}")
        println("  bookingCelebrationTriggered=${summary.bookingCelebrationTriggered}")
        println("  rfqSubmitted=${summary.rfqSubmitted}")
    } finally {
        container?.close()
        scope.cancel()
    }
}

private fun launchDesktopWindow(
    bffUrl: String?,
    initialVerticals: Boolean = false,
) {
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
        LaunchedEffect(container, initialVerticals) {
            if (initialVerticals) {
                container.navigateTo(SampleContainer.VERTICAL_SCREENS.first(), pushHistory = false)
            } else {
                container.loadInitialScreen()
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Abeta KMP SDUI Super-App Showcase",
            state = rememberWindowState(width = DEFAULT_WINDOW_WIDTH_DP.dp, height = DEFAULT_WINDOW_HEIGHT_DP.dp),
        ) {
            SampleRoot(container = container)
        }
    }
}
