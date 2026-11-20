package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult

private class WatchOsUnsupportedClipboardPort : ClipboardPort {
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> = unavailableFailure()

    override fun readText(): AppResult<String?> = unavailableFailure()

    private fun unavailableFailure(): AppResult.Failure =
        AppResult.Failure(
            ForbiddenError(
                resource = "clipboard",
                message = "Clipboard is unavailable on watchOS.",
            ),
        )
}

/** Creates the watchOS [ClipboardPort]. */
actual fun createPlatformClipboardPort(): ClipboardPort = WatchOsUnsupportedClipboardPort()
