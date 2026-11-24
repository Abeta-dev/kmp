package dev.abeta.kmp.actions.handler

import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionPayload
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OsPermissionKind
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

private const val DEFAULT_FEEDBACK_DURATION_MS = 1800L

/**
 * Strategy executing a decoded [ActionPayload] of type [P] against an [ActionContext].
 */
fun interface ActionHandler<in P : ActionPayload> {
    /** Executes [payload] and returns a typed [ActionOutcome] or [AppResult.Failure]. */
    suspend fun handle(
        payload: P,
        context: ActionContext,
    ): AppResult<ActionOutcome>
}

/**
 * Dependency-inverted port allowing host layers to supply HTTP execution for [CallApiPayload]
 * without introducing any networking dependency into `action-engine`.
 */
fun interface ApiCallExecutor {
    /** Executes the API call described by [payload] and returns an [ActionOutcome.ApiCallResult]. */
    suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult>
}

/**
 * Typed, UI-agnostic outcome emitted when an action handler executes.
 */
sealed interface ActionOutcome {
    /** Generic acknowledgement when a handler completes with no further host instruction. */
    data object Handled : ActionOutcome

    /** Requests navigation to [route]. */
    data class NavigationRequested(
        val route: String,
        val params: Map<String, String> = emptyMap(),
        val clearBackStack: Boolean = false,
        val launchMode: String? = null,
    ) : ActionOutcome

    /** Requests popping [steps] entries from the navigation stack with an optional [result]. */
    data class BackRequested(
        val result: Map<String, JsonElement> = emptyMap(),
        val steps: Int = 1,
    ) : ActionOutcome

    /**
     * Requests displaying a dialog or bottom-sheet overlay, optionally with rich SDUI [snippets]
     * or a remote [sheetScreenId].
     */
    data class OverlayRequested(
        val dialogId: String,
        val title: String,
        val message: String? = null,
        val dismissible: Boolean = true,
        val positiveAction: ActionRef? = null,
        val negativeAction: ActionRef? = null,
        val presentation: OverlayPresentation = OverlayPresentation.DIALOG,
        val snippets: List<SnippetEnvelope> = emptyList(),
        val sheetScreenId: String? = null,
    ) : ActionOutcome

    /** Requests dismissing an overlay by [overlayId] (or all overlays when [all] is `true`). */
    data class OverlayDismissed(
        val overlayId: String? = null,
        val all: Boolean = false,
    ) : ActionOutcome

    /** Requests displaying a transient toast message. */
    data class ToastRequested(
        val message: String,
        val durationMs: Long = 2500L,
        val variant: String = "info",
    ) : ActionOutcome

    /** Requests refreshing [pageId] (or the current page when `null`). */
    data class RefreshRequested(
        val pageId: String? = null,
        val showLoader: Boolean = false,
    ) : ActionOutcome

    /** Requests copying [text] to the system clipboard. */
    data class ClipboardCopyRequested(
        val text: String,
        val label: String? = null,
    ) : ActionOutcome

    /** Requests applying an incremental snippet mutation. */
    data class SnippetsUpdated(
        val operation: String,
        val targetSnippetId: String? = null,
        val snippets: List<SnippetEnvelope> = emptyList(),
    ) : ActionOutcome

    /** Requests emitting an analytics tracking event. */
    data class TrackingRequested(
        val eventName: String,
        val properties: Map<String, JsonElement> = emptyMap(),
    ) : ActionOutcome

    /** Requests mutating client state within [scope]. */
    data class StateMutated(
        val scope: String,
        val savedEntries: Map<String, JsonElement> = emptyMap(),
        val deletedKeys: List<String> = emptyList(),
    ) : ActionOutcome

    /** Requests triggering a visual or haptic feedback effect on the client UI. */
    data class FeedbackTriggered(
        val effect: FeedbackEffect,
        val title: String? = null,
        val subtitle: String? = null,
        val durationMs: Long = DEFAULT_FEEDBACK_DURATION_MS,
        val targetId: String? = null,
    ) : ActionOutcome

    /** Requests opening an external URL via the host OS launcher. */
    data class OpenUrlRequested(
        val url: String,
        val externalBrowser: Boolean = true,
        val fallbackRoute: String? = null,
    ) : ActionOutcome

    /** Requests presenting the OS share sheet or desktop share fallback. */
    data class ShareRequested(
        val title: String? = null,
        val text: String,
        val url: String? = null,
        val chooserTitle: String? = null,
    ) : ActionOutcome

    /** Requests an OS runtime permission check/prompt with optional follow-up actions. */
    data class PermissionRequested(
        val permission: OsPermissionKind,
        val rationale: String? = null,
        val onGranted: ActionRef? = null,
        val onDenied: ActionRef? = null,
    ) : ActionOutcome

    /** Requests OS biometric or device credential authentication with follow-up actions. */
    data class BiometricAuthRequested(
        val reason: String,
        val title: String? = null,
        val allowDeviceCredential: Boolean = true,
        val onAuthenticated: ActionRef? = null,
        val onFailed: ActionRef? = null,
    ) : ActionOutcome

    /** Captures the result of an API call along with follow-up actions to dispatch. */
    data class ApiCallResult(
        val endpoint: String,
        val method: String,
        val isSuccess: Boolean,
        val statusCode: Int? = null,
        val responseBody: JsonElement? = null,
        val errorMessage: String? = null,
        val followUpActions: List<ActionRef> = emptyList(),
    ) : ActionOutcome
}

/**
 * Immutable, OCP-compliant registry mapping [ActionPayload] subclasses to [ActionHandler]s.
 */
class ActionRegistry private constructor(
    private val handlers: Map<KClass<out ActionPayload>, ActionHandler<ActionPayload>>,
) {
    /** Returns the handler registered for [payloadClass], or `null` if none is registered. */
    fun handlerFor(payloadClass: KClass<out ActionPayload>): ActionHandler<ActionPayload>? = handlers[payloadClass]

    /** Returns all [ActionPayload] classes currently registered in this snapshot. */
    fun registeredPayloads(): Set<KClass<out ActionPayload>> = handlers.keys

    /** Creates a mutable [Builder] initialized with this registry's handlers. */
    fun toBuilder(): Builder = Builder(handlers)

    /** Mutable builder for constructing an immutable [ActionRegistry] snapshot. */
    class Builder internal constructor(
        initial: Map<KClass<out ActionPayload>, ActionHandler<ActionPayload>> = emptyMap(),
    ) {
        private val entries = initial.toMutableMap()

        /** Registers or overrides the [handler] for [payloadClass]. */
        fun <P : ActionPayload> register(
            payloadClass: KClass<P>,
            handler: ActionHandler<P>,
        ): Builder =
            apply {
                @Suppress("UNCHECKED_CAST")
                entries[payloadClass] = handler as ActionHandler<ActionPayload>
            }

        /** Reified convenience helper for registering a [handler] for [P]. */
        inline fun <reified P : ActionPayload> register(handler: ActionHandler<P>): Builder {
            val payloadClass = P::class
            return register(payloadClass, handler)
        }

        /** Builds an immutable [ActionRegistry] snapshot. */
        fun build(): ActionRegistry = ActionRegistry(entries.toMap())
    }

    companion object {
        /** Returns a new empty [Builder]. */
        fun builder(): Builder = Builder()
    }
}

/**
 * Registers default pure handlers translating standard [ActionPayload]s into [ActionOutcome]s.
 */
object BuiltInActionHandlers {
    /**
     * Populates [builder] with default handlers for built-in payloads.
     *
     * When [apiExecutor] is provided, [CallApiPayload] is also registered and automatically
     * populates [ActionOutcome.ApiCallResult.followUpActions] with [CallApiPayload.onSuccess]
     * or [CallApiPayload.onError] when empty.
     */
    fun registerDefaults(
        builder: ActionRegistry.Builder,
        apiExecutor: ApiCallExecutor? = null,
    ): ActionRegistry.Builder =
        builder.apply {
            registerNavigationAndNetworkHandlers(this, apiExecutor)
            registerUiAndStateHandlers(this)
            registerOsBridgeHandlers(this)
        }

    /** Convenience factory creating an [ActionRegistry] with all built-in handlers. */
    fun createDefaultRegistry(apiExecutor: ApiCallExecutor? = null): ActionRegistry =
        registerDefaults(ActionRegistry.builder(), apiExecutor).build()
}
