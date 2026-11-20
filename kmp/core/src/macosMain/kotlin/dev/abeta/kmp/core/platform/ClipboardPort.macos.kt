package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString

private class MacOsClipboardPort : ClipboardPort {
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        val pasteboard = NSPasteboard.generalPasteboard
        pasteboard.clearContents()
        pasteboard.setString(text, forType = NSPasteboardTypeString)
        return AppResult.Success(Unit)
    }

    override fun readText(): AppResult<String?> {
        val value = NSPasteboard.generalPasteboard.stringForType(NSPasteboardTypeString)
        return AppResult.Success(value)
    }
}

/** Creates a macOS [ClipboardPort] backed by [NSPasteboard.generalPasteboard]. */
actual fun createPlatformClipboardPort(): ClipboardPort = MacOsClipboardPort()
