@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult

@JsFun(
    "(text) => { try { if (typeof navigator !== 'undefined' && navigator.clipboard && " +
        "navigator.clipboard.writeText) { navigator.clipboard.writeText(text); return true; } } " +
        "catch (e) {} return false; }",
)
private external fun jsWriteClipboard(text: String): Boolean

private class WasmClipboardPort : ClipboardPort {
    private var lastCopiedText: String? = null

    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        lastCopiedText = text
        return if (jsWriteClipboard(text)) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                ForbiddenError(
                    resource = "clipboard",
                    message = "Browser clipboard API unavailable in current context.",
                ),
            )
        }
    }

    override fun readText(): AppResult<String?> = AppResult.Success(lastCopiedText)
}

/** Creates a WasmJS browser [ClipboardPort]. */
actual fun createPlatformClipboardPort(): ClipboardPort = WasmClipboardPort()
