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
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.snippet.canRenderComposedDepth
import dev.abeta.kmp.state.session.SessionStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object LayoutFormSeeder {
    fun extractInitialFormValues(
        page: PageResponse,
        sessionStore: SessionStore,
    ): Map<String, String> = extractInitialFormValuesFromSnippets(page.snippets, sessionStore)

    fun extractInitialFormValuesFromSnippets(
        snippets: List<SnippetEnvelope>,
        sessionStore: SessionStore,
    ): Map<String, String> {
        val values = linkedMapOf<String, String>()
        val existingState = sessionStore.state.value
        val allEnvelopes = collectAllEnvelopes(snippets, SduiJson)
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
                        seedComposedControls(block.root, 0, existingState, sessionStore, values)
                    }
                }
            }
        }
        return values
    }

    private fun seedComposedControls(
        node: ComposedNode,
        depth: Int,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        if (!canRenderComposedDepth(depth)) return
        seedComposedNodeValues(node, existingState, sessionStore, values)
        seedComposedFormAtomValues(node, existingState, sessionStore, values)
        node.children.forEach { child ->
            seedComposedControls(child, depth + 1, existingState, sessionStore, values)
        }
    }

    private fun seedComposedFormAtomValues(
        node: ComposedNode,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        node.textField?.let { field ->
            val stored = (existingState[field.fieldId] as? JsonPrimitive)?.contentOrNull
            val resolved = stored ?: field.initialValue
            if (resolved.isNotEmpty()) {
                values[field.fieldId] = resolved
                if (stored == null) {
                    sessionStore.put(field.fieldId, JsonPrimitive(resolved))
                }
            }
        }
        node.switch?.let { switchAtom ->
            seedControlValue(switchAtom.fieldId, switchAtom.checked.toString(), existingState, sessionStore, values)
        }
        node.checkbox?.let { checkboxAtom ->
            seedControlValue(checkboxAtom.fieldId, checkboxAtom.checked.toString(), existingState, sessionStore, values)
        }
        node.slider?.let { sliderAtom ->
            seedControlValue(sliderAtom.fieldId, sliderAtom.value.toString(), existingState, sessionStore, values)
        }
    }

    private fun seedComposedNodeValues(
        node: ComposedNode,
        existingState: Map<String, JsonElement>,
        sessionStore: SessionStore,
        values: MutableMap<String, String>,
    ) {
        node.stepper?.let { stepper ->
            val minQty = stepper.minQuantity.coerceAtLeast(0)
            val defaultQty = stepper.quantity.coerceAtLeast(minQty).toString()
            seedControlValue(stepper.fieldId, defaultQty, existingState, sessionStore, values)
        }
        node.radioGroup?.let { radio ->
            radio.selectedId?.takeIf { it.isNotBlank() }?.let { selected ->
                seedControlValue(radio.fieldId, selected, existingState, sessionStore, values)
            }
        }
        node.select?.let { select ->
            select.selectedId?.takeIf { it.isNotBlank() }?.let { selected ->
                seedControlValue(select.fieldId, selected, existingState, sessionStore, values)
            }
        }
        node.otpInput?.let { otp ->
            if (otp.initialValue.isNotEmpty()) {
                seedControlValue(otp.fieldId, otp.initialValue, existingState, sessionStore, values)
            }
        }
        node.segmentedControl?.let { segmented ->
            val defaultSelection =
                segmented.selectedId?.takeIf { it.isNotBlank() } ?: segmented.options.firstOrNull()?.id
            defaultSelection?.let { selected ->
                seedControlValue(segmented.fieldId, selected, existingState, sessionStore, values)
            }
        }
        node.rating?.let { rating ->
            rating.fieldId?.takeIf { it.isNotBlank() }?.let { fieldId ->
                seedControlValue(fieldId, rating.value.toString(), existingState, sessionStore, values)
            }
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
        return current
            .copy(
                validationErrors = updatedErrors,
                errorShakeSequence =
                    if (bumpShake) current.errorShakeSequence + 1L else current.errorShakeSequence,
                errorShakeTargetId = if (bumpShake) null else current.errorShakeTargetId,
            ).withToast(
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
            if (value is JsonPrimitive) {
                val content = value.contentOrNull
                if (content != null) {
                    updatedFormValues[key] = content
                } else {
                    updatedFormValues.remove(key)
                }
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
        if (!hasCustomErrorAction) {
            current
                .copy(pendingOfflineActions = pendingCount)
                .withToast(
                    ActionOutcome.ToastRequested(
                        message = "Saved offline ($pendingCount pending)",
                        variant = "warning",
                    ),
                )
        } else {
            current.copy(pendingOfflineActions = pendingCount)
        }

    fun applyOfflineFlushed(
        current: LayoutUiState.Ready,
        pendingCount: Int,
        succeededCount: Int,
    ): LayoutUiState.Ready =
        if (succeededCount > 0) {
            current
                .copy(pendingOfflineActions = pendingCount)
                .withToast(
                    ActionOutcome.ToastRequested(
                        message = "Synced $succeededCount offline action(s)",
                        variant = "success",
                    ),
                )
        } else {
            current.copy(pendingOfflineActions = pendingCount)
        }

    fun applyApiCallFailure(
        current: LayoutUiState.Ready,
        outcome: ActionOutcome.ApiCallResult,
    ): LayoutUiState.Ready =
        if (!outcome.isSuccess && outcome.followUpActions.isEmpty()) {
            val message = outcome.errorMessage ?: "Request failed for ${outcome.endpoint}"
            current.withToast(ActionOutcome.ToastRequested(message = message, variant = "error"))
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

    fun toAnalyticsProperties(properties: Map<String, JsonElement>): Map<String, String> =
        properties.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.toString()
                else -> value.toString()
            }
        }
}

internal object OfflinePayloadSelector {
    fun selectFailedNonGetCallApiPayloads(
        action: ActionRef,
        results: List<AppResult<ActionOutcome>>,
        transientFailureCount: Int,
    ): List<CallApiPayload> {
        val allPayloads = if (transientFailureCount > 0) extractCallApiPayloads(action) else emptyList()
        if (allPayloads.isEmpty()) return emptyList()

        val completedCounts = mutableMapOf<Pair<String, String>, Int>()
        results.forEach { result ->
            val outcome = (result as? AppResult.Success)?.value as? ActionOutcome.ApiCallResult
            if (outcome != null) {
                val key = outcome.endpoint to outcome.method.trim().uppercase()
                completedCounts[key] = (completedCounts[key] ?: 0) + 1
            }
        }

        val unmatched = mutableListOf<CallApiPayload>()
        for (payload in allPayloads) {
            val key = payload.endpoint to payload.method.trim().uppercase()
            val remainingCompleted = completedCounts[key] ?: 0
            if (remainingCompleted > 0) {
                completedCounts[key] = remainingCompleted - 1
            } else {
                unmatched.add(payload)
            }
        }

        return unmatched
            .take(transientFailureCount)
            .filterNot { it.method.trim().equals("GET", ignoreCase = true) }
    }

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
}

internal object SnippetListMutator {
    fun mutate(
        existing: List<SnippetEnvelope>,
        update: ActionOutcome.SnippetsUpdated,
    ): List<SnippetEnvelope> =
        when (update.operation.trim().lowercase()) {
            "append" -> append(existing, update.targetSnippetId, update.snippets)
            "prepend" -> prepend(existing, update.snippets)
            "replace" -> replace(existing, update.targetSnippetId, update.snippets)
            "remove" -> remove(existing, update.targetSnippetId, update.snippets)
            else -> existing
        }

    private fun prepend(
        existing: List<SnippetEnvelope>,
        additions: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> {
        if (additions.isEmpty()) return existing
        val deduped = deduplicateById(additions)
        val incomingIds = deduped.mapTo(hashSetOf()) { it.snippetId }
        return deduped + existing.filterNot { it.snippetId in incomingIds }
    }

    private fun append(
        existing: List<SnippetEnvelope>,
        targetId: String?,
        additions: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> {
        if (additions.isEmpty()) return existing
        val deduped = deduplicateById(additions)
        val targetIndex = targetId?.let { id -> existing.indexOfFirst { it.snippetId == id } } ?: -1
        return if (targetIndex < 0) {
            upsertAtEnd(existing, deduped)
        } else {
            val incomingIds = deduped.mapTo(hashSetOf()) { it.snippetId }
            val before = existing.subList(0, targetIndex + 1).filterNot { it.snippetId in incomingIds }
            val after = existing.subList(targetIndex + 1, existing.size).filterNot { it.snippetId in incomingIds }
            before + deduped + after
        }
    }

    private fun replace(
        existing: List<SnippetEnvelope>,
        targetId: String?,
        replacements: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> {
        val deduped = deduplicateById(replacements)
        return if (targetId != null) {
            val index = existing.indexOfFirst { it.snippetId == targetId }
            if (index < 0) {
                existing
            } else {
                val incomingIds = deduped.mapTo(hashSetOf()) { it.snippetId }
                val before = existing.subList(0, index).filterNot { it.snippetId in incomingIds }
                val after = existing.subList(index + 1, existing.size).filterNot { it.snippetId in incomingIds }
                before + deduped + after
            }
        } else {
            val byId = deduped.associateBy { it.snippetId }
            deduplicateById(existing.map { envelope -> byId[envelope.snippetId] ?: envelope })
        }
    }

    private fun upsertAtEnd(
        existing: List<SnippetEnvelope>,
        additions: List<SnippetEnvelope>,
    ): List<SnippetEnvelope> {
        val byId = additions.associateBy { it.snippetId }
        val existingIds = existing.mapTo(hashSetOf()) { it.snippetId }
        val updatedExisting = existing.map { envelope -> byId[envelope.snippetId] ?: envelope }
        val brandNew = additions.filterNot { it.snippetId in existingIds }
        return updatedExisting + brandNew
    }

    private fun deduplicateById(snippets: List<SnippetEnvelope>): List<SnippetEnvelope> {
        if (snippets.size <= 1) return snippets
        val unique = linkedMapOf<String, SnippetEnvelope>()
        snippets.forEach { unique[it.snippetId] = it }
        return unique.values.toList()
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
