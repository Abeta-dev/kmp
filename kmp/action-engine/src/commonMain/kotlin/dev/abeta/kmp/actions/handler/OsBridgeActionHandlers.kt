package dev.abeta.kmp.actions.handler

import dev.abeta.kmp.actions.model.BiometricAuthPayload
import dev.abeta.kmp.actions.model.CopyToClipboardPayload
import dev.abeta.kmp.actions.model.OpenUrlPayload
import dev.abeta.kmp.actions.model.RequestPermissionPayload
import dev.abeta.kmp.actions.model.SharePayload
import dev.abeta.kmp.core.result.AppResult

internal fun registerOsBridgeHandlers(builder: ActionRegistry.Builder) {
    builder.register<CopyToClipboardPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.ClipboardCopyRequested(
                text = payload.text,
                label = payload.label,
            ),
        )
    }
    builder.register<OpenUrlPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.OpenUrlRequested(
                url = payload.url,
                externalBrowser = payload.externalBrowser,
                fallbackRoute = payload.fallbackRoute,
            ),
        )
    }
    builder.register<SharePayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.ShareRequested(
                title = payload.title,
                text = payload.text,
                url = payload.url,
                chooserTitle = payload.chooserTitle,
            ),
        )
    }
    builder.register<RequestPermissionPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.PermissionRequested(
                permission = payload.permission,
                rationale = payload.rationale,
                onGranted = payload.onGranted,
                onDenied = payload.onDenied,
            ),
        )
    }
    builder.register<BiometricAuthPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.BiometricAuthRequested(
                reason = payload.reason,
                title = payload.title,
                allowDeviceCredential = payload.allowDeviceCredential,
                onAuthenticated = payload.onAuthenticated,
                onFailed = payload.onFailed,
            ),
        )
    }
}
