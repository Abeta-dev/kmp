@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult

private const val LAUNCHER_UNAVAILABLE_MSG = "Opening external URLs is unavailable on watchOS."
private const val SHARE_UNAVAILABLE_MSG = "System share sheet is unavailable on watchOS."

private class WatchOsExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = false

    override suspend fun openUrl(url: String): AppResult<Unit> =
        AppResult.Failure(ForbiddenError(resource = "external_launcher", message = LAUNCHER_UNAVAILABLE_MSG))
}

private class WatchOsSharePort : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> =
        AppResult.Failure(ForbiddenError(resource = "share", message = SHARE_UNAVAILABLE_MSG))
}

/** Creates the watchOS [ExternalLauncherPort]. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = WatchOsExternalLauncherPort()

/** Creates the watchOS [SharePort]. */
actual fun createPlatformSharePort(): SharePort = WatchOsSharePort()
