package dev.abeta.kmp.sample

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.abeta.kmp.layout.engine.LayoutUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private const val RETINA_DENSITY = 2f
private const val DESKTOP_WIDTH_DP = 1280
private const val DESKTOP_HEIGHT_DP = 860
private const val PHONE_WIDTH_DP = 412
private const val PHONE_HEIGHT_DP = 915
private const val WATCH_WIDTH_DP = 320
private const val WATCH_HEIGHT_DP = 390
private const val SETTLE_FRAME_COUNT = 16
private const val FRAME_STEP_NANOS = 100_000_000L

private data class DemoCaptureSpec(
    val fileName: String,
    val screenId: String,
    val widthDp: Int,
    val heightDp: Int,
    val darkTheme: Boolean,
    val showTopBar: Boolean,
)

private val DEMO_CAPTURE_SPECS: List<DemoCaptureSpec> =
    listOf(
        DemoCaptureSpec("demo-home-desktop.png", "home", DESKTOP_WIDTH_DP, DESKTOP_HEIGHT_DP, false, true),
        DemoCaptureSpec("demo-store-home-desktop.png", "store-home", DESKTOP_WIDTH_DP, DESKTOP_HEIGHT_DP, false, true),
        DemoCaptureSpec("demo-store-home-phone.png", "store-home", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, false, false),
        DemoCaptureSpec("demo-store-home-dark.png", "store-home", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, true, false),
        DemoCaptureSpec("demo-containers-desktop.png", "containers", DESKTOP_WIDTH_DP, DESKTOP_HEIGHT_DP, false, true),
        DemoCaptureSpec("demo-form-demo-desktop.png", "form-demo", DESKTOP_WIDTH_DP, DESKTOP_HEIGHT_DP, false, true),
        DemoCaptureSpec("demo-watch-status.png", "watch-status", WATCH_WIDTH_DP, WATCH_HEIGHT_DP, true, false),
    )

private val VERTICAL_CAPTURE_SPECS: List<DemoCaptureSpec> =
    listOf(
        "fresh" to "fresh-home",
        "bites" to "bites-home",
        "pulse" to "pulse-home",
        "mart" to "mart-home",
        "spark" to "spark-deck",
        "trade" to "trade-home",
        "pay" to "pay-home",
    ).flatMap { (slug, screenId) ->
        listOf(
            DemoCaptureSpec("vertical-$slug-phone.png", screenId, PHONE_WIDTH_DP, PHONE_HEIGHT_DP, false, false),
            DemoCaptureSpec("vertical-$slug-desktop.png", screenId, DESKTOP_WIDTH_DP, DESKTOP_HEIGHT_DP, false, true),
        )
    }

/**
 * Renders pixel-accurate off-screen Compose Multiplatform PNG screenshots of the demo screens
 * across Desktop, Phone, Watch, Light, and Dark modes.
 */
internal fun exportDemoScreenshots(
    outputDirPath: String,
    bffUrl: String?,
) {
    exportCaptureSpecs(
        specs = DEMO_CAPTURE_SPECS,
        label = "demo",
        outputDirPath = outputDirPath,
        bffUrl = bffUrl,
    )
}

/**
 * Renders off-screen Phone (412x915) and Desktop (1280x860) PNG screenshots of all seven vertical
 * showcase apps (`fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`,
 * `pay-home`).
 */
internal fun exportVerticalScreenshots(
    outputDirPath: String,
    bffUrl: String?,
) {
    exportCaptureSpecs(
        specs = VERTICAL_CAPTURE_SPECS,
        label = "vertical showcase",
        outputDirPath = outputDirPath,
        bffUrl = bffUrl,
    )
}

private fun exportCaptureSpecs(
    specs: List<DemoCaptureSpec>,
    label: String,
    outputDirPath: String,
    bffUrl: String?,
) {
    val outputDir = File(outputDirPath).apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var container: SampleContainer? = null
    try {
        runBlocking {
            val created = SampleFactory.create(scope = scope, bffBaseUrl = bffUrl)
            container = created
            specs.forEach { spec ->
                captureDemoScreen(created, spec, outputDir)
            }
        }
        println("Exported ${specs.size} $label screenshots to ${outputDir.absolutePath}")
    } finally {
        container?.close()
        scope.cancel()
    }
}

private suspend fun captureDemoScreen(
    container: SampleContainer,
    spec: DemoCaptureSpec,
    outputDir: File,
) {
    container.navigateTo(spec.screenId, clearBackStack = true).join()
    check(container.controller.uiState.value is LayoutUiState.Ready) {
        "Expected Ready state for '${spec.screenId}', got ${container.controller.uiState.value}"
    }
    val widthPx = (spec.widthDp * RETINA_DENSITY).toInt()
    val heightPx = (spec.heightDp * RETINA_DENSITY).toInt()
    val scene =
        ImageComposeScene(
            width = widthPx,
            height = heightPx,
            density = Density(RETINA_DENSITY),
        ) {
            SampleRoot(
                container = container,
                initialDarkTheme = spec.darkTheme,
                showTopBar = spec.showTopBar,
            )
        }
    try {
        var nanoTime = 0L
        repeat(SETTLE_FRAME_COUNT) {
            scene.render(nanoTime)
            nanoTime += FRAME_STEP_NANOS
        }
        val image = scene.render(nanoTime)
        val pngBytes =
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)?.bytes) {
                "Failed to encode PNG for ${spec.fileName}"
            }
        File(outputDir, spec.fileName).writeBytes(pngBytes)
    } finally {
        scene.close()
    }
}
