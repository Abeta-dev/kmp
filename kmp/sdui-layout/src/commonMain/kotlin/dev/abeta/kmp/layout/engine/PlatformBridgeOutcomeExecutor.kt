package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.core.platform.PermissionStatus
import dev.abeta.kmp.core.platform.ShareRequest
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.BiometricPromptRequest
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.platform.toCorePermission

internal class PlatformBridgeOutcomeExecutor(
    private val bridges: PlatformBridgeBundle,
    private val navigationListener: (ActionOutcome.NavigationRequested) -> Unit,
    private val clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit,
    private val showToast: (message: String, variant: String) -> Unit,
    private val dispatchFollowUp: suspend (ActionRef) -> Unit,
) {
    suspend fun executeIfBridgeOutcome(outcome: ActionOutcome): Boolean =
        when (outcome) {
            is ActionOutcome.ClipboardCopyRequested -> {
                bridges.clipboardPort.copy(text = outcome.text, label = outcome.label)
                clipboardListener(outcome)
                true
            }

            is ActionOutcome.OpenUrlRequested -> {
                handleOpenUrl(outcome)
                true
            }

            is ActionOutcome.ShareRequested -> {
                handleShare(outcome)
                true
            }

            is ActionOutcome.PermissionRequested -> {
                handlePermission(outcome)
                true
            }

            is ActionOutcome.BiometricAuthRequested -> {
                handleBiometric(outcome)
                true
            }

            else -> {
                false
            }
        }

    private suspend fun handleOpenUrl(outcome: ActionOutcome.OpenUrlRequested) {
        val result = bridges.externalLauncher.openUrl(outcome.url)
        if (result is AppResult.Failure) {
            val fallback = outcome.fallbackRoute?.takeIf { it.isNotBlank() }
            if (fallback != null) {
                navigationListener(ActionOutcome.NavigationRequested(route = fallback))
            } else {
                showToast(result.error.message, "error")
            }
        }
    }

    private suspend fun handleShare(outcome: ActionOutcome.ShareRequested) {
        val request =
            ShareRequest(
                text = outcome.text,
                title = outcome.title,
                url = outcome.url,
                chooserTitle = outcome.chooserTitle,
            )
        if (bridges.sharePort.share(request) is AppResult.Failure) {
            val combined = request.formatCombinedText()
            if (bridges.clipboardPort.copy(text = combined, label = outcome.title) is AppResult.Success) {
                showToast("Copied share details to clipboard", "info")
            } else {
                showToast("Unable to share content on this host", "error")
            }
        }
    }

    private suspend fun handlePermission(outcome: ActionOutcome.PermissionRequested) {
        val status =
            bridges.permissionPort.request(
                permission = outcome.permission.toCorePermission(),
                rationale = outcome.rationale,
            )
        val followUp = if (status == PermissionStatus.GRANTED) outcome.onGranted else outcome.onDenied
        if (followUp != null) {
            dispatchFollowUp(followUp)
        }
    }

    private suspend fun handleBiometric(outcome: ActionOutcome.BiometricAuthRequested) {
        val bioResult =
            bridges.biometricAuthenticator.authenticate(
                BiometricPromptRequest(
                    reason = outcome.reason,
                    title = outcome.title,
                    allowDeviceCredential = outcome.allowDeviceCredential,
                ),
            )
        val followUp =
            if (bioResult is BiometricResult.Authenticated) {
                outcome.onAuthenticated
            } else {
                outcome.onFailed
            }
        if (followUp != null) {
            dispatchFollowUp(followUp)
        }
    }
}
