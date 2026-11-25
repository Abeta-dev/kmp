package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

private class JvmAwtClipboardPort : ClipboardPort {
    @Suppress("TooGenericExceptionCaught")
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        if (isJvmHeadlessSafely()) {
            return AppResult.Failure(
                ForbiddenError(resource = "clipboard", message = "System clipboard unavailable in headless JVM."),
            )
        }
        return try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            AppResult.Success(Unit)
        } catch (err: IllegalStateException) {
            AppResult.Failure(UnexpectedError(message = "Clipboard write failed: ${err.message}", cause = err))
        } catch (err: Throwable) {
            AppResult.Failure(
                ForbiddenError(
                    resource = "clipboard",
                    message = "System clipboard unavailable on this runtime: ${err.message}",
                    cause = err,
                ),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun readText(): AppResult<String?> {
        if (isJvmHeadlessSafely()) {
            return AppResult.Failure(
                ForbiddenError(resource = "clipboard", message = "System clipboard unavailable in headless JVM."),
            )
        }
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val value =
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    clipboard.getData(DataFlavor.stringFlavor) as? String
                } else {
                    null
                }
            AppResult.Success(value)
        } catch (err: IllegalStateException) {
            AppResult.Failure(UnexpectedError(message = "Clipboard read failed: ${err.message}", cause = err))
        } catch (err: UnsupportedFlavorException) {
            AppResult.Failure(UnexpectedError(message = "Clipboard flavor unsupported: ${err.message}", cause = err))
        } catch (err: IOException) {
            AppResult.Failure(UnexpectedError(message = "Clipboard I/O failed: ${err.message}", cause = err))
        } catch (err: Throwable) {
            AppResult.Failure(
                ForbiddenError(
                    resource = "clipboard",
                    message = "System clipboard unavailable on this runtime: ${err.message}",
                    cause = err,
                ),
            )
        }
    }
}

/** Creates an AWT [ClipboardPort] supporting macOS, Windows, and Linux JVM desktops. */
actual fun createPlatformClipboardPort(): ClipboardPort = JvmAwtClipboardPort()
