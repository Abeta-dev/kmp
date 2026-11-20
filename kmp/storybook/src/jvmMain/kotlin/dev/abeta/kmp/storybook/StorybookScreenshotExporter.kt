package dev.abeta.kmp.storybook

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.abeta.kmp.tokens.ThemePreset
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private const val RETINA_DENSITY = 2f
private const val STORY_WIDTH_DP = 1280
private const val STORY_HEIGHT_DP = 860
private const val SETTLE_FRAMES = 6
private const val FRAME_STEP_NANOS = 100_000_000L

private data class StoryCaptureSpec(
    val fileName: String,
    val storyId: String,
    val preset: ThemePreset,
    val darkTheme: Boolean,
)

private val STORY_CAPTURE_SPECS: List<StoryCaptureSpec> =
    listOf(
        StoryCaptureSpec(
            fileName = "storybook-design-tokens.png",
            storyId = StoryCatalog.STORY_DESIGN_TOKENS,
            preset = ThemePreset.INDIGO,
            darkTheme = false,
        ),
        StoryCaptureSpec(
            fileName = "storybook-controls-atoms.png",
            storyId = StoryCatalog.STORY_CONTROLS_DATA,
            preset = ThemePreset.EMERALD,
            darkTheme = false,
        ),
        StoryCaptureSpec(
            fileName = "storybook-snippets-dark.png",
            storyId = StoryCatalog.STORY_SNIPPETS,
            preset = ThemePreset.INDIGO,
            darkTheme = true,
        ),
        StoryCaptureSpec(
            fileName = "storybook-motion-store.png",
            storyId = StoryCatalog.STORY_MOTION_STORE,
            preset = ThemePreset.EMERALD,
            darkTheme = false,
        ),
        StoryCaptureSpec(
            fileName = "storybook-responsive-bridges.png",
            storyId = StoryCatalog.STORY_RESPONSIVE_BRIDGES,
            preset = ThemePreset.INDIGO,
            darkTheme = false,
        ),
    )

/**
 * Renders off-screen Retina PNG screenshots of key Storybook stories via Skiko [ImageComposeScene].
 */
internal fun exportStorybookScreenshots(outputDirPath: String) {
    val outputDir = File(outputDirPath).apply { mkdirs() }
    val widthPx = (STORY_WIDTH_DP * RETINA_DENSITY).toInt()
    val heightPx = (STORY_HEIGHT_DP * RETINA_DENSITY).toInt()
    STORY_CAPTURE_SPECS.forEach { spec ->
        val scene =
            ImageComposeScene(
                width = widthPx,
                height = heightPx,
                density = Density(RETINA_DENSITY),
            ) {
                StorybookApp(
                    initialStoryId = spec.storyId,
                    initialPreset = spec.preset,
                    initialDarkTheme = spec.darkTheme,
                )
            }
        try {
            var nanoTime = 0L
            repeat(SETTLE_FRAMES) {
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
    println("Exported ${STORY_CAPTURE_SPECS.size} storybook screenshots to ${outputDir.absolutePath}")
}
