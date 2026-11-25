package dev.abeta.kmp.storybook

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop

private const val HEX_RADIX = 16
private const val PERCENT_SEQ_LENGTH = 3

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.search || ''")
private external fun browserLocationSearch(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.hash || ''")
private external fun browserLocationHash(): String

private fun parseQueryParams(raw: String): Map<String, String> {
    val cleaned = raw.trim().trimStart('#').trimStart('?')
    if (cleaned.isEmpty()) return emptyMap()
    return buildMap {
        cleaned.split('&').forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val key = decodePercent(part.substring(0, idx)).trim().lowercase()
                val value = decodePercent(part.substring(idx + 1)).trim()
                if (key.isNotEmpty() && value.isNotEmpty() && key !in this) {
                    put(key, value)
                }
            }
        }
    }
}

private fun decodePercent(input: String): String {
    val normalized = input.replace('+', ' ')
    val out = StringBuilder(normalized.length)
    var index = 0
    while (index < normalized.length) {
        if (normalized[index] == '%' && index + 2 < normalized.length) {
            val bytes = mutableListOf<Byte>()
            var cursor = index
            while (cursor + 2 < normalized.length && normalized[cursor] == '%') {
                val decoded = normalized.substring(cursor + 1, cursor + PERCENT_SEQ_LENGTH).toIntOrNull(HEX_RADIX)
                if (decoded == null) break
                bytes.add(decoded.toByte())
                cursor += PERCENT_SEQ_LENGTH
            }
            if (bytes.isNotEmpty()) {
                out.append(bytes.toByteArray().decodeToString())
                index = cursor
                continue
            }
        }
        out.append(normalized[index])
        index += 1
    }
    return out.toString()
}

/**
 * WebAssembly browser entry point mounting [StorybookApp] into `#storybookRoot`,
 * honoring `?story=<id>&app=<screenId>` and `#story=<id>&app=<screenId>` deep links.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val searchParams = parseQueryParams(browserLocationSearch())
    val hashParams = parseQueryParams(browserLocationHash())
    val requestedStory = searchParams["story"] ?: hashParams["story"]
    val requestedApp = searchParams["app"] ?: hashParams["app"]
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
