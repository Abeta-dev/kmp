package dev.abeta.kmp.actions.handler

import dev.abeta.kmp.actions.model.DeleteStatePayload
import dev.abeta.kmp.actions.model.DismissOverlayPayload
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.actions.model.SaveStatePayload
import dev.abeta.kmp.actions.model.ShowToastPayload
import dev.abeta.kmp.actions.model.TriggerFeedbackPayload
import dev.abeta.kmp.actions.model.UpdateSnippetsPayload
import dev.abeta.kmp.core.result.AppResult

internal fun registerUiAndStateHandlers(builder: ActionRegistry.Builder) {
    registerOverlayAndToastHandlers(builder)
    registerStateAndFeedbackHandlers(builder)
}

private fun registerOverlayAndToastHandlers(builder: ActionRegistry.Builder) {
    builder.register<OpenDialogPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.OverlayRequested(
                dialogId = payload.dialogId,
                title = payload.title,
                message = payload.message,
                dismissible = payload.dismissible,
                positiveAction = payload.positiveAction,
                negativeAction = payload.negativeAction,
                presentation = payload.presentation,
            ),
        )
    }
    builder.register<DismissOverlayPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.OverlayDismissed(
                overlayId = payload.overlayId,
                all = payload.all,
            ),
        )
    }
    builder.register<ShowToastPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.ToastRequested(
                message = payload.message,
                durationMs = payload.durationMs,
                variant = payload.variant,
            ),
        )
    }
}

private fun registerStateAndFeedbackHandlers(builder: ActionRegistry.Builder) {
    builder.register<SaveStatePayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.StateMutated(
                scope = payload.scope,
                savedEntries = mapOf(payload.key to payload.value),
            ),
        )
    }
    builder.register<DeleteStatePayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.StateMutated(
                scope = payload.scope,
                deletedKeys = listOf(payload.key),
            ),
        )
    }
    builder.register<UpdateSnippetsPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.SnippetsUpdated(
                operation = payload.operation,
                targetSnippetId = payload.targetSnippetId,
                snippets = payload.snippets,
            ),
        )
    }
    builder.register<TriggerFeedbackPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.FeedbackTriggered(
                effect = payload.effect,
                title = payload.title,
                subtitle = payload.subtitle,
                durationMs = payload.durationMs,
                targetId = payload.targetId,
            ),
        )
    }
}
