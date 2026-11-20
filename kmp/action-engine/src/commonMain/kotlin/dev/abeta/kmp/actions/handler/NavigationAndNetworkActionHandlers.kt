package dev.abeta.kmp.actions.handler

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionPayload
import dev.abeta.kmp.actions.model.BackPressPayload
import dev.abeta.kmp.actions.model.BatchPayload
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.actions.model.NavigatePayload
import dev.abeta.kmp.actions.model.RefreshPagePayload
import dev.abeta.kmp.actions.model.TrackEventPayload
import dev.abeta.kmp.actions.validation.ActionValidator
import dev.abeta.kmp.actions.validation.DefaultActionValidator
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.runCatchingResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun registerNavigationAndNetworkHandlers(
    builder: ActionRegistry.Builder,
    apiExecutor: ApiCallExecutor?,
) {
    builder.register<NavigatePayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.NavigationRequested(
                route = payload.route,
                params = payload.params,
                clearBackStack = payload.clearBackStack,
                launchMode = payload.launchMode,
            ),
        )
    }
    builder.register<BackPressPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.BackRequested(
                result = payload.result,
                steps = payload.steps,
            ),
        )
    }
    builder.register<RefreshPagePayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.RefreshRequested(
                pageId = payload.pageId,
                showLoader = payload.showLoader,
            ),
        )
    }
    builder.register<TrackEventPayload> { payload, _ ->
        AppResult.Success(
            ActionOutcome.TrackingRequested(
                eventName = payload.eventName,
                properties = payload.properties,
            ),
        )
    }
    if (apiExecutor != null) {
        registerApiCallHandler(builder, apiExecutor)
    }
}

private fun registerApiCallHandler(
    builder: ActionRegistry.Builder,
    apiExecutor: ApiCallExecutor,
) {
    builder.register<CallApiPayload> { payload, context ->
        when (val result = apiExecutor.execute(payload, context)) {
            is AppResult.Failure -> {
                result
            }

            is AppResult.Success -> {
                val outcome = result.value
                val enriched =
                    if (outcome.followUpActions.isEmpty()) {
                        val next =
                            listOfNotNull(
                                if (outcome.isSuccess) payload.onSuccess else payload.onError,
                            )
                        outcome.copy(followUpActions = next)
                    } else {
                        outcome
                    }
                AppResult.Success(enriched)
            }
        }
    }
}

/**
 * Port responsible for validating, decoding, and executing [ActionRef] trees.
 */
interface ActionDispatcher {
    /**
     * Dispatches [action] in the given [context], returning an ordered list of
     * [AppResult] outcomes including any nested batch or secondary actions.
     */
    suspend fun dispatch(
        action: ActionRef,
        context: ActionContext = ActionContext(),
    ): List<AppResult<ActionOutcome>>
}

/**
 * Default [ActionDispatcher] pipeline:
 * 1. Guards against unbounded recursion ([maxDepth]).
 * 2. Validates [ActionRef.validations] via [validator].
 * 3. Decodes [ActionRef.payload] via [decoderRegistry].
 * 4. Executes the matching [ActionHandler] from [actionRegistry] (or expands [BatchPayload]).
 * 5. Safely dispatches follow-up and [ActionRef.secondaryActions] upon success.
 */
class DefaultActionDispatcher(
    private val decoderRegistry: ActionDecoderRegistry,
    private val actionRegistry: ActionRegistry,
    private val validator: ActionValidator = DefaultActionValidator(),
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) : ActionDispatcher {
    init {
        require(maxDepth >= 1) { "maxDepth must be >= 1." }
    }

    override suspend fun dispatch(
        action: ActionRef,
        context: ActionContext,
    ): List<AppResult<ActionOutcome>> = dispatchInternal(action, context, depth = 0)

    private suspend fun dispatchInternal(
        action: ActionRef,
        context: ActionContext,
        depth: Int,
    ): List<AppResult<ActionOutcome>> {
        val validationFailure =
            if (depth > maxDepth) {
                AppResult.Failure(
                    ValidationError(
                        field = "depth",
                        message = "Max action dispatch depth ($maxDepth) exceeded for '${action.type}'.",
                    ),
                )
            } else {
                validator.validate(action, context) as? AppResult.Failure
            }

        return if (validationFailure != null) {
            listOf(validationFailure)
        } else {
            when (val decodeResult = decoderRegistry.decode(action.type, action.payload)) {
                is AppResult.Failure -> listOf(decodeResult)
                is AppResult.Success -> executeDecoded(action, decodeResult.value, context, depth)
            }
        }
    }

    private suspend fun executeDecoded(
        action: ActionRef,
        decoded: ActionPayload,
        context: ActionContext,
        depth: Int,
    ): List<AppResult<ActionOutcome>> {
        val handler = actionRegistry.handlerFor(decoded::class)
        return when {
            handler != null -> {
                executeRegisteredHandler(action, decoded, handler, context, depth)
            }

            decoded is BatchPayload -> {
                dispatchBatch(action, decoded, context, depth)
            }

            else -> {
                listOf(
                    AppResult.Failure(
                        NotFoundError(
                            resource = action.type,
                            message = "No ActionHandler registered for payload '${decoded::class.simpleName}'.",
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun executeRegisteredHandler(
        action: ActionRef,
        decoded: ActionPayload,
        handler: ActionHandler<ActionPayload>,
        context: ActionContext,
        depth: Int,
    ): List<AppResult<ActionOutcome>> {
        val executionResult = executeSafely(handler, decoded, context)
        if (executionResult is AppResult.Failure) {
            return if (decoded is CallApiPayload && decoded.onError != null) {
                listOf(executionResult) + dispatchInternal(decoded.onError, context, depth + 1)
            } else {
                listOf(executionResult)
            }
        }

        val outcome = (executionResult as AppResult.Success).value
        val results = mutableListOf<AppResult<ActionOutcome>>(executionResult)
        if (outcome is ActionOutcome.ApiCallResult) {
            for (followUp in outcome.followUpActions) {
                results.addAll(dispatchInternal(followUp, context, depth + 1))
            }
        }
        for (secondary in action.secondaryActions) {
            results.addAll(dispatchInternal(secondary, context, depth + 1))
        }
        return results.toList()
    }

    private suspend fun dispatchBatch(
        action: ActionRef,
        batch: BatchPayload,
        context: ActionContext,
        depth: Int,
    ): List<AppResult<ActionOutcome>> {
        val results = mutableListOf<AppResult<ActionOutcome>>()
        var stoppedOnError = false

        if (batch.parallel && !batch.stopOnError) {
            val parallelResults =
                coroutineScope {
                    batch.actions
                        .map { child -> async { dispatchInternal(child, context, depth + 1) } }
                        .awaitAll()
                        .flatten()
                }
            results.addAll(parallelResults)
        } else {
            for (child in batch.actions) {
                val childResults = dispatchInternal(child, context, depth + 1)
                results.addAll(childResults)
                if (batch.stopOnError && childResults.any { it is AppResult.Failure }) {
                    stoppedOnError = true
                    break
                }
            }
        }

        if (!stoppedOnError) {
            for (secondary in action.secondaryActions) {
                results.addAll(dispatchInternal(secondary, context, depth + 1))
            }
        }
        return results.toList()
    }

    private suspend fun executeSafely(
        handler: ActionHandler<ActionPayload>,
        payload: ActionPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome> =
        when (val wrapped = runCatchingResult { handler.handle(payload, context) }) {
            is AppResult.Success -> wrapped.value
            is AppResult.Failure -> wrapped
        }

    companion object {
        const val DEFAULT_MAX_DEPTH: Int = 16
    }
}
