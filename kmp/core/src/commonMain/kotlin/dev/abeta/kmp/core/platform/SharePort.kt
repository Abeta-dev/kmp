package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult

/**
 * Payload describing content passed to the native OS share sheet or clipboard share fallback.
 */
data class ShareRequest(
    val text: String,
    val title: String? = null,
    val url: String? = null,
    val chooserTitle: String? = null,
) {
    /** Combines [title], [text], and [url] cleanly into plain share text for clipboard/fallback channels. */
    fun formatCombinedText(): String =
        buildList {
            title?.takeIf { it.isNotBlank() }?.let(::add)
            if (text.isNotBlank()) add(text)
            url?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(separator = "\n")
}

/**
 * Cross-platform OS sharing port (UIActivityViewController, Android chooser, Web Share API, desktop fallback).
 */
interface SharePort {
    /** Opens the host share sheet (or copies share payload on desktop fallback). */
    suspend fun share(request: ShareRequest): AppResult<Unit>
}

/**
 * Test double capturing all [ShareRequest] calls.
 */
class RecordingSharePort : SharePort {
    private val _requests = mutableListOf<ShareRequest>()

    /** Immutable snapshot of recorded share requests. */
    val requests: List<ShareRequest>
        get() = _requests.toList()

    override suspend fun share(request: ShareRequest): AppResult<Unit> {
        _requests.add(request)
        return AppResult.Success(Unit)
    }
}

/** Creates the platform-native [SharePort] for the current KMP target. */
expect fun createPlatformSharePort(): SharePort
