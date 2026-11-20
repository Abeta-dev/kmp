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
        DemoCaptureSpec(
            fileName = "demo-home-desktop.png",
            screenId = "home",
            widthDp = DESKTOP_WIDTH_DP,
            heightDp = DESKTOP_HEIGHT_DP,
            darkTheme = false,
            showTopBar = true,
        ),
        DemoCaptureSpec(
            fileName = "demo-store-home-desktop.png",
            screenId = "store-home",
            widthDp = DESKTOP_WIDTH_DP,
            heightDp = DESKTOP_HEIGHT_DP,
            darkTheme = false,
            showTopBar = true,
        ),
        DemoCaptureSpec(
            fileName = "demo-store-home-phone.png",
            screenId = "store-home",
            widthDp = PHONE_WIDTH_DP,
            heightDp = PHONE_HEIGHT_DP,
            darkTheme = false,
            showTopBar = false,
        ),
        DemoCaptureSpec(
            fileName = "demo-store-home-dark.png",
            screenId = "store-home",
            widthDp = PHONE_WIDTH_DP,
            heightDp = PHONE_HEIGHT_DP,
            darkTheme = true,
            showTopBar = false,
        ),
        DemoCaptureSpec(
            fileName = "demo-containers-desktop.png",
            screenId = "containers",
            widthDp = DESKTOP_WIDTH_DP,
            heightDp = DESKTOP_HEIGHT_DP,
            darkTheme = false,
            showTopBar = true,
        ),
        DemoCaptureSpec(
            fileName = "demo-form-demo-desktop.png",
            screenId = "form-demo",
            widthDp = DESKTOP_WIDTH_DP,
            heightDp = DESKTOP_HEIGHT_DP,
            darkTheme = false,
            showTopBar = true,
        ),
        DemoCaptureSpec(
            fileName = "demo-watch-status.png",
            screenId = "watch-status",
            widthDp = WATCH_WIDTH_DP,
            heightDp = WATCH_HEIGHT_DP,
            darkTheme = true,
            showTopBar = false,
        ),
    )

/**
 * Renders pixel-accurate off-screen Compose Multiplatform PNG screenshots of the demo screens
 * across Desktop, Phone, Watch, Light, and Dark modes.
 */
internal fun exportDemoScreenshots(
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
            DEMO_CAPTURE_SPECS.forEach { spec ->
                captureDemoScreen(created, spec, outputDir)
            }
        }
        println("Exported ${DEMO_CAPTURE_SPECS.size} demo screenshots to ${outputDir.absolutePath}")
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
