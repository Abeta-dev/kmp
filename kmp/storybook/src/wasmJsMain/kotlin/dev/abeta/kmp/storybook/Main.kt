package dev.abeta.kmp.storybook

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.search || ''")
private external fun browserLocationSearch(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.hash || ''")
private external fun browserLocationHash(): String

private fun parseQueryParam(
    raw: String,
    key: String,
): String? =
    raw
        .trimStart('?', '#')
        .split('&')
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) {
                null
            } else {
                part.substring(0, idx).lowercase() to part.substring(idx + 1)
            }
        }.firstOrNull { it.first == key.lowercase() }
        ?.second
        ?.takeIf { it.isNotBlank() }

/**
 * WebAssembly browser entry point mounting [StorybookApp] into `#storybookRoot`,
 * honoring `?story=<id>&app=<screenId>` and `#story=<id>&app=<screenId>` deep links.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val combinedParams = "${browserLocationSearch()}&${browserLocationHash().trimStart('#')}"
    val requestedStory = parseQueryParam(combinedParams, "story")
    val requestedApp = parseQueryParam(combinedParams, "app")
    val initialStoryId =
        when {
            requestedStory != null -> StoryCatalog.findById(requestedStory).id
            requestedApp != null -> StoryCatalog.STORY_SUPER_APP_DEMOS
            else -> StoryCatalog.STORY_SUPER_APP_DEMOS
        }
    val initialAppScreen = SuperAppDemoCatalog.resolveInitialScreen(requestedApp)
    ComposeViewport(viewportContainerId = "storybookRoot") {
        StorybookApp(
            initialStoryId = initialStoryId,
            initialAppScreenId = initialAppScreen,
        )
    }
}
