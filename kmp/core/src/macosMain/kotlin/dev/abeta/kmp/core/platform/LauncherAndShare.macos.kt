@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

private class MacOsExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val res = validateExternalUrl(url)) {
            is AppResult.Failure -> {
                res
            }

            is AppResult.Success -> {
                val nsUrl = NSURL.URLWithString(res.value)
                when {
                    nsUrl == null -> {
                        AppResult.Failure(ValidationError(field = "url", message = "Malformed URL: ${res.value}"))
                    }

                    NSWorkspace.sharedWorkspace.openURL(nsUrl) -> {
                        AppResult.Success(Unit)
                    }

                    else -> {
                        AppResult.Failure(UnexpectedError("NSWorkspace failed to open URL: ${res.value}"))
                    }
                }
            }
        }
}

private class MacOsSharePort(
    private val clipboardPort: ClipboardPort = createPlatformClipboardPort(),
) : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> =
        clipboardPort.copy(
            text = request.formatCombinedText(),
            label = request.chooserTitle ?: request.title,
        )
}

/** Creates a macOS native [ExternalLauncherPort] backed by [NSWorkspace.sharedWorkspace]. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = MacOsExternalLauncherPort()

/** Creates a macOS [SharePort] copying formatted share payloads to the pasteboard. */
actual fun createPlatformSharePort(): SharePort = MacOsSharePort()
