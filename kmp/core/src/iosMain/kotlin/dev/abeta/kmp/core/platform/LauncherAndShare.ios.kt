@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private class IosExternalLauncherPort : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean {
        val validated = (validateExternalUrl(url) as? AppResult.Success)?.value
        val nsUrl = validated?.let { NSURL.URLWithString(it) }
        return nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)
    }

    override suspend fun openUrl(url: String): AppResult<Unit> {
        val validatedResult = validateExternalUrl(url)
        val validated = (validatedResult as? AppResult.Success)?.value
        val nsUrl = validated?.let { NSURL.URLWithString(it) }
        return when {
            validatedResult is AppResult.Failure -> {
                validatedResult
            }

            nsUrl == null -> {
                AppResult.Failure(ValidationError(field = "url", message = "Malformed URL: $validated"))
            }

            else -> {
                dispatchOpenUrl(validated, nsUrl)
            }
        }
    }

    private suspend fun dispatchOpenUrl(
        validated: String,
        nsUrl: NSURL,
    ): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                UIApplication.sharedApplication.openURL(
                    nsUrl,
                    options = emptyMap<Any?, Any?>(),
                ) { opened ->
                    if (!continuation.isActive) return@openURL
                    val outcome =
                        if (opened) {
                            AppResult.Success(Unit)
                        } else {
                            AppResult.Failure(UnexpectedError("Unable to open URL in iOS: $validated"))
                        }
                    continuation.resume(outcome)
                }
            }
        }
}

private class IosSharePort : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> {
        val items =
            buildList<Any> {
                request.title?.takeIf { it.isNotBlank() }?.let(::add)
                if (request.text.isNotBlank()) add(request.text)
                request.url?.let { rawUrl -> NSURL.URLWithString(rawUrl) }?.let(::add)
            }
        if (items.isEmpty()) {
            return AppResult.Failure(ValidationError(field = "share", message = "Nothing to share."))
        }
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                val rootController = findTopViewController()
                if (rootController == null) {
                    if (continuation.isActive) {
                        val message = "No active UIViewController available to present share sheet."
                        continuation.resume(AppResult.Failure(UnexpectedError(message)))
                    }
                    return@dispatch_async
                }
                val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
                sheet.popoverPresentationController?.let { popover ->
                    val anchorView = rootController.view
                    popover.sourceView = anchorView
                    popover.sourceRect =
                        CGRectMake(
                            x = CGRectGetMidX(anchorView.bounds),
                            y = CGRectGetMidY(anchorView.bounds),
                            width = 0.0,
                            height = 0.0,
                        )
                    popover.permittedArrowDirections = 0uL
                }
                rootController.presentViewController(sheet, animated = true) {
                    if (continuation.isActive) {
                        continuation.resume(AppResult.Success(Unit))
                    }
                }
            }
        }
    }

    private fun findTopViewController(): UIViewController? {
        val windowScene =
            UIApplication.sharedApplication.connectedScenes
                .firstNotNullOfOrNull { it as? UIWindowScene }
        val keyWindow =
            windowScene
                ?.windows
                ?.filterIsInstance<UIWindow>()
                ?.firstOrNull { it.isKeyWindow() }
                ?: windowScene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull()
        var current = keyWindow?.rootViewController
        while (current?.presentedViewController != null) {
            current = current.presentedViewController
        }
        return current
    }
}

/** Creates an iOS/iPadOS [ExternalLauncherPort] via [UIApplication.sharedApplication]. */
actual fun createPlatformExternalLauncher(): ExternalLauncherPort = IosExternalLauncherPort()

/** Creates an iOS/iPadOS [SharePort] presenting [UIActivityViewController]. */
actual fun createPlatformSharePort(): SharePort = IosSharePort()
