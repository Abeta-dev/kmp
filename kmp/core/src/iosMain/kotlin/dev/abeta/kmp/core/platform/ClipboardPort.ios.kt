package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult
import platform.UIKit.UIPasteboard

private class IosClipboardPort : ClipboardPort {
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        UIPasteboard.generalPasteboard.string = text
        return AppResult.Success(Unit)
    }

    override fun readText(): AppResult<String?> = AppResult.Success(UIPasteboard.generalPasteboard.string)
}

/** Creates an iOS/iPadOS [ClipboardPort] backed by [UIPasteboard.generalPasteboard]. */
actual fun createPlatformClipboardPort(): ClipboardPort = IosClipboardPort()
