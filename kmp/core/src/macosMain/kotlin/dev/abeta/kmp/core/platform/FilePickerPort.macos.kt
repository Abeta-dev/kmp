@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AppKit.NSApp
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private class MacOsFilePickerPort : FilePickerPort {
    override suspend fun pickFiles(request: FilePickerRequest): AppResult<List<PickedFile>> {
        if (NSApp == null) {
            return AppResult.Failure(
                ForbiddenError(
                    resource = "file_picker",
                    message = "AppKit NSOpenPanel requires an active macOS application session.",
                ),
            )
        }
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                val panel =
                    NSOpenPanel.openPanel().apply {
                        canChooseFiles = true
                        canChooseDirectories = false
                        allowsMultipleSelection = request.allowMultiple
                    }
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        panel.cancel(null)
                    }
                }
                panel.beginWithCompletionHandler { response ->
                    if (!continuation.isActive) return@beginWithCompletionHandler
                    if (response == NSModalResponseOK) {
                        val picked =
                            panel.URLs.mapNotNull { entry ->
                                (entry as? NSURL)?.let(::toPickedFile)
                            }
                        continuation.resume(AppResult.Success(picked))
                    } else {
                        continuation.resume(AppResult.Success(emptyList()))
                    }
                }
            }
        }
    }

    private fun toPickedFile(url: NSURL): PickedFile {
        val filePath = url.path
        val sizeBytes =
            filePath?.let { path ->
                val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
                (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue
            }
        return PickedFile(
            name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "file",
            mimeType = inferMacOsMimeType(url.pathExtension),
            sizeBytes = sizeBytes,
            bytes = null,
            uriString = url.absoluteString,
        )
    }

    private fun inferMacOsMimeType(extension: String?): String? =
        when (extension?.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            else -> null
        }
}

/** Creates a macOS AppKit `NSOpenPanel`-backed [FilePickerPort]. */
actual fun createPlatformFilePickerPort(): FilePickerPort = MacOsFilePickerPort()
