@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult

@JsFun(
    "(url) => { try { if (typeof window !== 'undefined' && window.open) { " +
        "window.open(url, '_blank', 'noopener,noreferrer'); return true; } } catch (e) {} return false; }",
)
private external fun jsWindowOpen(url: String): Boolean

@JsFun(
    "(title, text, url) => { try { if (typeof navigator !== 'undefined' && " +
        "typeof navigator.share === 'function') { navigator.share({ title: title, text: text, url: url }); " +
        "return true; } } catch (e) {} return false; }",
)
private external fun jsNavigatorShare(
    title: String,
    text: String,
    url: String,
): Boolean

private class WasmExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val res = validateExternalUrl(url)) {
            is AppResult.Failure -> {
                res
            }

            is AppResult.Success -> {
                if (jsWindowOpen(res.value)) {
                    AppResult.Success(Unit)
                } else {
                    AppResult.Failure(UnexpectedError("window.open unavailable in current WasmJS runtime."))
                }
            }
        }
}

private class WasmSharePort(
    private val clipboardPort: ClipboardPort = createPlatformClipboardPort(),
) : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> {
        val shared =
            jsNavigatorShare(
                title = request.title.orEmpty(),
                text = request.text,
                url = request.url.orEmpty(),
            )
        return if (shared) {
            AppResult.Success(Unit)
        } else {
            clipboardPort.copy(text = request.formatCombinedText(), label = request.title)
        }
    }
}

/** Creates a WasmJS browser [ExternalLauncherPort]. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = WasmExternalLauncherPort()

/** Creates a WasmJS Web Share / clipboard-fallback [SharePort]. */
actual fun createPlatformSharePort(): SharePort = WasmSharePort()
