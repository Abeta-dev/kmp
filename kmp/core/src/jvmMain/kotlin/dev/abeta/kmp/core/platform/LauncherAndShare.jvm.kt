@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

private class JvmExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val result = validateExternalUrl(url)) {
            is AppResult.Failure -> {
                result
            }

            is AppResult.Success -> {
                if (isJvmHeadlessSafely()) {
                    AppResult.Failure(
                        ForbiddenError(
                            resource = "external_launcher",
                            message = "Cannot launch browser in headless JVM.",
                        ),
                    )
                } else {
                    openUriOnDesktop(result.value)
                }
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun openUriOnDesktop(url: String): AppResult<Unit> {
        var fallbackToCommand = false
        val browseResult: AppResult<Unit>? =
            try {
                val uri = URI(url)
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri)
                    AppResult.Success(Unit)
                } else {
                    fallbackToCommand = true
                    null
                }
            } catch (err: URISyntaxException) {
                AppResult.Failure(UnexpectedError(message = "Invalid URI: ${err.message}", cause = err))
            } catch (err: IOException) {
                AppResult.Failure(UnexpectedError(message = "Failed to open URI: ${err.message}", cause = err))
            } catch (_: Throwable) {
                fallbackToCommand = true
                null
            }
        return if (fallbackToCommand || browseResult == null) launchViaOsCommand(url) else browseResult
    }

    private fun launchViaOsCommand(url: String): AppResult<Unit> {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val command =
            when {
                osName.contains("mac") -> listOf("open", url)
                osName.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                else -> listOf("xdg-open", url)
            }
        return try {
            ProcessBuilder(command).start()
            AppResult.Success(Unit)
        } catch (err: IOException) {
            AppResult.Failure(UnexpectedError(message = "OS browser command failed: ${err.message}", cause = err))
        } catch (err: SecurityException) {
            AppResult.Failure(UnexpectedError(message = "OS browser command denied: ${err.message}", cause = err))
        }
    }
}

private class JvmSharePort(
    private val clipboardPort: ClipboardPort = createPlatformClipboardPort(),
) : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> =
        clipboardPort.copy(
            text = request.formatCombinedText(),
            label = request.chooserTitle ?: request.title,
        )
}

/** Creates a JVM desktop [ExternalLauncherPort] supporting macOS, Windows, and Linux. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = JvmExternalLauncherPort()

/** Creates a JVM desktop [SharePort] copying formatted share payloads to the clipboard. */
actual fun createPlatformSharePort(): SharePort = JvmSharePort()
