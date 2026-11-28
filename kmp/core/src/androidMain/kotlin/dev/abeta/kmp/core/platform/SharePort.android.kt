package dev.abeta.kmp.core.platform

import android.content.Intent
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult

private class AndroidSharePort : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> {
        val context =
            AbetaAndroidContext.applicationContextOrNull()
                ?: return AppResult.Failure(
                    ForbiddenError(
                        resource = "share",
                        message = "Android context is not initialized. Call AbetaAndroidContext.initialize(context).",
                    ),
                )
        return runCatching {
            val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, request.formatCombinedText())
                    request.title?.let { title -> putExtra(Intent.EXTRA_SUBJECT, title) }
                }
            val chooser =
                Intent.createChooser(sendIntent, request.chooserTitle ?: request.title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { err ->
                AppResult.Failure(
                    UnexpectedError(message = "Unable to present Android share sheet: ${err.message}", cause = err),
                )
            },
        )
    }
}

/** Creates an Android [SharePort] dispatching `Intent.ACTION_SEND` through a system chooser. */
actual fun createPlatformSharePort(): SharePort = AndroidSharePort()
