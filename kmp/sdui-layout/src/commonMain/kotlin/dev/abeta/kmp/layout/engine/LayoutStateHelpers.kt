package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.BatchPayload
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.layout.snippet.canRenderComposedDepth
import dev.abeta.kmp.state.session.SessionStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object LayoutFormSeeder {
    fun extractInitialFormValues(
        page: PageResponse,
        sessionStore: SessionStore,
    ): Map<String, String> {
        val values = linkedMapOf<String, String>()
        val existingState = sessionStore.state.value
        val allEnvelopes = collectAllEnvelopes(page.snippets, SduiJson)
        for (snippet in allEnvelopes) {
            when (snippet.snippetType) {
                SnippetTypes.FORM -> {
                    runCatching {
                        SduiJson.decodeFromJsonElement(FormSnippetData.serializer(), snippet.data)
                    }.getOrNull()?.let { form ->
                        seedFormValues(form, existingState, sessionStore, values)
                    }
                }

                SnippetTypes.PRODUCT_CARD -> {
                    runCatching {
                        SduiJson.decodeFromJsonElement(ProductCardSnippetData.serializer(), snippet.data)
                    }.getOrNull()?.let { card ->
                        val minQty = card.stepper.minQuantity.coerceAtLeast(0)
                        val defaultQty =
                            card.stepper.quantity
                                .coerceAtLeast(minQty)
                                .toString()
                        seedControlValue(card.stepper.fieldId, defaultQty, existingState, sessionStore, values)
                    }
                }

                SnippetTypes.COMPOSED_BLOCK -> {
                    runCatching {
                        SduiJson.decodeFromJsonElement(ComposedBlockSnippetData.serializer(), snippet.data)
                    }.getOrNull()?.let { block ->
                        seedComposedSteppers(block.root, 0, existingState, sessionStore, values)
                    }
                }
            }
        }
        return values
    }

    private fun seedComposedSteppers(
        node: ComposedNode,
        depth: Int,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        if (!canRenderComposedDepth(depth)) return
        node.stepper?.let { stepper ->
            val minQty = stepper.minQuantity.coerceAtLeast(0)
            val defaultQty = stepper.quantity.coerceAtLeast(minQty).toString()
            seedControlValue(stepper.fieldId, defaultQty, existingState, sessionStore, values)
        }
        node.children.forEach { child ->
            seedComposedSteppers(child, depth + 1, existingState, sessionStore, values)
        }
    }

    private fun seedFormValues(
        form: FormSnippetData,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        form.fields.forEach { field ->
            val stored = (existingState[field.fieldId] as? JsonPrimitive)?.contentOrNull
            val resolved = stored ?: field.initialValue
            if (resolved.isNotEmpty()) {
                values[field.fieldId] = resolved
                if (stored == null) {
                    sessionStore.put(field.fieldId, JsonPrimitive(resolved))
                }
            }
        }
        form.switches.forEach { switchAtom ->
            seedControlValue(switchAtom.fieldId, switchAtom.checked.toString(), existingState, sessionStore, values)
        }
        form.checkboxes.forEach { checkboxAtom ->
            seedControlValue(checkboxAtom.fieldId, checkboxAtom.checked.toString(), existingState, sessionStore, values)
        }
        form.sliders.forEach { sliderAtom ->
            seedControlValue(sliderAtom.fieldId, sliderAtom.value.toString(), existingState, sessionStore, values)
        }
    }

    private fun seedControlValue(
        fieldId: String,
        defaultValue: String,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        val stored = (existingState[fieldId] as? JsonPrimitive)?.contentOrNull
        val resolved = stored ?: defaultValue
        values[fieldId] = resolved
        if (stored == null) {
            sessionStore.put(fieldId, JsonPrimitive(resolved))
        }
    }
}

internal object LayoutStateReducer {
    fun buildActionContext(
        ready: LayoutUiState.Ready?,
        sessionState: Map<String, JsonElement>,
    ): ActionContext {
        val metadata =
            buildMap {
                ready?.page?.pageId?.let { put("page_id", it) }
                ready?.formValues?.forEach { (key, value) -> put(key, value) }
            }
        return ActionContext(
            sessionState = sessionState,
            metadata = metadata,
        )
    }

    fun applyFailure(
        current: LayoutUiState.Ready,
        error: AppError,
    ): LayoutUiState.Ready {
        val fieldName = (error as? ValidationError)?.field?.takeIf { it.isNotBlank() }
        val updatedErrors =
            if (fieldName != null) {
                current.validationErrors + (fieldName to error.message)
            } else {
                current.validationErrors
            }
        val bumpShake = error is ValidationError
        return current.copy(
            validationErrors = updatedErrors,
            errorShakeSequence =
                if (bumpShake) current.errorShakeSequence + 1L else current.errorShakeSequence,
            activeToast =
                ActionOutcome.ToastRequested(
                    message = error.message,
                    variant = "error",
                ),
        )
    }

    fun applyFeedback(
        current: LayoutUiState.Ready,
        outcome: ActionOutcome.FeedbackTriggered,
    ): LayoutUiState.Ready {
        val isShakeError = outcome.effect == FeedbackEffect.SHAKE_ERROR
        return current.copy(
            activeFeedback = outcome,
            feedbackSequence = current.feedbackSequence + 1L,
            errorShakeSequence =
                if (isShakeError) current.errorShakeSequence + 1L else current.errorShakeSequence,
            errorShakeTargetId =
                if (isShakeError) outcome.targetId else current.errorShakeTargetId,
        )
    }

    fun applyStateMutation(
        current: LayoutUiState.Ready,
        outcome: ActionOutcome.StateMutated,
    ): LayoutUiState.Ready {
        val updatedFormValues = current.formValues.toMutableMap()
        outcome.deletedKeys.forEach(updatedFormValues::remove)
        outcome.savedEntries.forEach { (key, value) ->
            if (key in updatedFormValues && value is JsonPrimitive && value.isString) {
                updatedFormValues[key] = value.content
            }
        }
        return current.copy(formValues = updatedFormValues.toMap())
    }

    fun applyOverlayDismissed(
        current: LayoutUiState.Ready,
        outcome: ActionOutcome.OverlayDismissed,
    ): LayoutUiState.Ready {
        val overlay = current.activeOverlay
        val shouldDismiss =
            overlay != null &&
                (outcome.all || outcome.overlayId == null || overlay.dialogId == outcome.overlayId)
        return if (shouldDismiss) current.copy(activeOverlay = null) else current
    }

    fun applyOfflineEnqueued(
        current: LayoutUiState.Ready,
        pendingCount: Int,
        hasCustomErrorAction: Boolean,
    ): LayoutUiState.Ready =
        current.copy(
            pendingOfflineActions = pendingCount,
            activeToast =
                if (!hasCustomErrorAction) {
                    ActionOutcome.ToastRequested(
                        message = "Saved offline ($pendingCount pending)",
                        variant = "warning",
                    )
                } else {
                    current.activeToast
                },
        )

    fun applyOfflineFlushed(
        current: LayoutUiState.Ready,
        pendingCount: Int,
        succeededCount: Int,
    ): LayoutUiState.Ready =
        current.copy(
            pendingOfflineActions = pendingCount,
            activeToast =
                if (succeededCount > 0) {
                    ActionOutcome.ToastRequested(
                        message = "Synced $succeededCount offline action(s)",
                        variant = "success",
                    )
                } else {
                    current.activeToast
                },
        )

    fun applyApiCallFailure(
        current: LayoutUiState.Ready,
        outcome: ActionOutcome.ApiCallResult,
    ): LayoutUiState.Ready =
        if (!outcome.isSuccess && outcome.followUpActions.isEmpty()) {
            val message = outcome.errorMessage ?: "Request failed for ${outcome.endpoint}"
            current.copy(activeToast = ActionOutcome.ToastRequested(message = message, variant = "error"))
        } else {
            current
        }

    fun isOfflineOrTransientNetworkError(
        error: AppError,
        httpServerErrorMin: Int,
    ): Boolean =
        error is OfflineError ||
            error is TimeoutError ||
            (error is NetworkError && (error.statusCode == null || error.statusCode!! >= httpServerErrorMin))

    fun extractCallApiPayloads(action: ActionRef): List<CallApiPayload> =
        when (action.type) {
            ActionTypes.CALL_API -> {
                listOfNotNull(
                    runCatching {
                        SduiJson.decodeFromJsonElement(CallApiPayload.serializer(), action.payload)
                    }.getOrNull(),
                )
            }

            ActionTypes.BATCH -> {
                val batch =
                    runCatching {
                        SduiJson.decodeFromJsonElement(BatchPayload.serializer(), action.payload)
                    }.getOrNull()
                batch?.actions?.flatMap(::extractCallApiPayloads).orEmpty()
            }

            else -> {
                emptyList()
            }
        }

    fun toAnalyticsProperties(properties: Map<String, JsonElement>): Map<String, String> =
        properties.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                else -> value.toString()
            }
        }
}

internal object SnippetListMutator {
    fun mutate(
        existing: List<SnippetEnvelope>,
        update: ActionOutcome.SnippetsUpdated,
    ): List<SnippetEnvelope> =
        when (update.operation.trim().lowercase()) {
            "append" -> append(existing, update.targetSnippetId, update.snippets)
            "prepend" -> update.snippets + existing
            "replace" -> replace(existing, update.targetSnippetId, update.snippets)
            "remove" -> remove(existing, update.targetSnippetId, update.snippets)
            else -> existing
        }

    private fun append(
        existing: List<SnippetEnvelope>,
        targetId: String?,
        additions: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> =
        when {
            additions.isEmpty() -> {
                existing
            }

            targetId == null -> {
                existing + additions
            }

            else -> {
                val index = existing.indexOfFirst { it.snippetId == targetId }
                if (index < 0) {
                    existing + additions
                } else {
                    existing.toMutableList().apply { addAll(index + 1, additions) }.toList()
                }
            }
        }

    private fun replace(
        existing: List<SnippetEnvelope>,
        targetId: String?,
        replacements: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> =
        if (targetId != null) {
            val index = existing.indexOfFirst { it.snippetId == targetId }
            if (index < 0) {
                existing
            } else {
                existing
                    .toMutableList()
                    .apply {
                        removeAt(index)
                        addAll(index, replacements)
                    }.toList()
            }
        } else {
            val byId = replacements.associateBy { it.snippetId }
            existing.map { envelope -> byId[envelope.snippetId] ?: envelope }
        }

    private fun remove(
        existing: List<SnippetEnvelope>,
        targetId: String?,
        toRemove: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> {
        val idsToRemove =
            buildSet {
                if (targetId != null) add(targetId)
                toRemove.forEach { add(it.snippetId) }
            }
        if (idsToRemove.isEmpty()) return existing
        return existing.filterNot { it.snippetId in idsToRemove }
    }
}
