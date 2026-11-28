package dev.abeta.kmp.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult

private class AndroidClipboardPort : ClipboardPort {
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> {
        val context =
            AbetaAndroidContext.applicationContextOrNull()
                ?: return missingContextFailure()
        return runCatching {
            val manager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: error("ClipboardManager unavailable.")
            manager.setPrimaryClip(ClipData.newPlainText(label ?: "abeta", text))
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { err ->
                AppResult.Failure(
                    UnexpectedError(message = "Android clipboard copy failed: ${err.message}", cause = err),
                )
            },
        )
    }

    override fun readText(): AppResult<String?> {
        val context =
            AbetaAndroidContext.applicationContextOrNull()
                ?: return missingContextFailure()
        return runCatching {
            val manager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: error("ClipboardManager unavailable.")
            manager.primaryClip
                ?.takeIf { clip -> clip.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { err ->
                AppResult.Failure(
                    UnexpectedError(message = "Android clipboard read failed: ${err.message}", cause = err),
                )
            },
        )
    }

    private fun <T> missingContextFailure(): AppResult<T> =
        AppResult.Failure(
            ForbiddenError(
                resource = "clipboard",
                message = "Android context is not initialized. Call AbetaAndroidContext.initialize(context) first.",
            ),
        )
}

/** Creates an Android [ClipboardPort] backed by [ClipboardManager] via [AbetaAndroidContext]. */
actual fun createPlatformClipboardPort(): ClipboardPort = AndroidClipboardPort()
