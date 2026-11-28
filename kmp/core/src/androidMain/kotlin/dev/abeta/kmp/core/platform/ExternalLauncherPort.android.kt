package dev.abeta.kmp.core.platform

import android.content.Intent
import android.net.Uri
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult

private class AndroidExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val validated = validateExternalUrl(url)) {
            is AppResult.Failure -> {
                validated
            }

            is AppResult.Success -> {
                val context = AbetaAndroidContext.applicationContextOrNull()
                if (context == null) {
                    AppResult.Failure(
                        ForbiddenError(
                            resource = "external_launcher",
                            message =
                                "Android context is not initialized. Call AbetaAndroidContext.initialize(context).",
                        ),
                    )
                } else {
                    runCatching {
                        val intent =
                            Intent(Intent.ACTION_VIEW, Uri.parse(validated.value)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    }.fold(
                        onSuccess = { AppResult.Success(Unit) },
                        onFailure = { err ->
                            AppResult.Failure(
                                UnexpectedError(message = "Unable to open external URL: ${err.message}", cause = err),
                            )
                        },
                    )
                }
            }
        }
}

/** Creates an Android [ExternalLauncherPort] opening validated URLs via `Intent.ACTION_VIEW`. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = AndroidExternalLauncherPort()
