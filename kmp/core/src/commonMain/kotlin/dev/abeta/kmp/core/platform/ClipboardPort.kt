package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult

/**
 * Unified OS clipboard port reading and writing plain text across mobile, desktop, and web.
 */
interface ClipboardPort {
    /** Writes [text] (with optional OS-visible [label]) to the system clipboard. */
    fun copy(
        text: String,
        label: String? = null,
    ): AppResult<Unit>

    /** Reads plain text currently held by the system clipboard, or `null` when unavailable/empty. */
    fun readText(): AppResult<String?>
}

/**
 * Deterministic in-memory [ClipboardPort] suitable for unit tests and headless verification.
 */
class InMemoryClipboardPort(
    initialText: String? = null,
) : ClipboardPort {
    private var content: String? = initialText
    private var lastLabelValue: String? = null

    /** Last label passed to [copy]. */
    val lastLabel: String?
        get() = lastLabelValue

    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        content = text
        lastLabelValue = label
        return AppResult.Success(Unit)
    }

    override fun readText(): AppResult<String?> = AppResult.Success(content)
}

/** Creates the platform-native [ClipboardPort] for the current KMP target. */
expect fun createPlatformClipboardPort(): ClipboardPort
