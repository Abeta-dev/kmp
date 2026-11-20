@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val LA_ERROR_USER_CANCEL = -2L
private const val LA_ERROR_SYSTEM_CANCEL = -4L
private const val LA_ERROR_APP_CANCEL = -9L
private const val LA_ERROR_NOT_ENROLLED = -7L
private const val LA_ERROR_NOT_AVAILABLE = -6L

internal expect fun canEvaluateAppleBiometricPolicy(
    context: LAContext,
    allowDeviceCredential: Boolean,
): Boolean

internal expect fun evaluateAppleBiometricPolicy(
    context: LAContext,
    allowDeviceCredential: Boolean,
    reason: String,
    onResult: (success: Boolean, errorCode: Long?, description: String?) -> Unit,
)

private class AppleBiometricAuthenticator : BiometricAuthenticator {
    override fun availability(): BiometricAvailability {
        val context = LAContext()
        if (canEvaluateAppleBiometricPolicy(context, allowDeviceCredential = false)) {
            return BiometricAvailability.AVAILABLE
        }
        return if (canEvaluateAppleBiometricPolicy(context, allowDeviceCredential = true)) {
            BiometricAvailability.NOT_ENROLLED
        } else {
            BiometricAvailability.UNAVAILABLE
        }
    }

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult {
        val context = LAContext()
        if (!canEvaluateAppleBiometricPolicy(context, request.allowDeviceCredential)) {
            return BiometricResult.Unavailable("Biometric or device credential authentication is unavailable.")
        }

        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                evaluateAppleBiometricPolicy(
                    context = context,
                    allowDeviceCredential = request.allowDeviceCredential,
                    reason = request.reason,
                ) { success, errorCode, description ->
                    if (!continuation.isActive) return@evaluateAppleBiometricPolicy
                    val isCancelled =
                        errorCode in setOf(LA_ERROR_USER_CANCEL, LA_ERROR_SYSTEM_CANCEL, LA_ERROR_APP_CANCEL)
                    val isUnavailable = errorCode in setOf(LA_ERROR_NOT_AVAILABLE, LA_ERROR_NOT_ENROLLED)
                    val result =
                        when {
                            success -> {
                                BiometricResult.Authenticated
                            }

                            isCancelled -> {
                                BiometricResult.Cancelled
                            }

                            isUnavailable -> {
                                BiometricResult.Unavailable(description ?: "Unavailable")
                            }

                            else -> {
                                BiometricResult.Failed(description ?: "Authentication failed")
                            }
                        }
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation { context.invalidate() }
        }
    }
}

/** Creates an Apple `LocalAuthentication` ([LAContext]) [BiometricAuthenticator]. */
actual fun createPlatformBiometricAuthenticator(): BiometricAuthenticator = AppleBiometricAuthenticator()
