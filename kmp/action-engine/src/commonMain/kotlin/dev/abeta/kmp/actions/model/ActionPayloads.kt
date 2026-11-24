package dev.abeta.kmp.actions.model

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OsPermissionKind
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.SnippetEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_FEEDBACK_DURATION_MS = 1800L

/**
 * Canonical action type identifiers matching the Go BFF `ActionRef.type` wire contract.
 */
object ActionTypes {
    const val NAVIGATE = "navigate"
    const val BACK_PRESS = "back_press"
    const val OPEN_DIALOG = "open_dialog"
    const val DISMISS_OVERLAY = "dismiss_overlay"
    const val SHOW_TOAST = "show_toast"
    const val CALL_API = "call_api"
    const val SAVE_STATE = "save_state"
    const val DELETE_STATE = "delete_state"
    const val TRACK_EVENT = "track_event"
    const val REFRESH_PAGE = "refresh_page"
    const val COPY_TO_CLIPBOARD = "copy_to_clipboard"
    const val UPDATE_SNIPPETS = "update_snippets"
    const val BATCH = "batch"
    const val TRIGGER_FEEDBACK = "trigger_feedback"
    const val OPEN_URL = "open_url"
    const val SHARE = "share"
    const val REQUEST_PERMISSION = "request_permission"
    const val BIOMETRIC_AUTH = "biometric_auth"
}

/**
 * Marker interface for decoded action payloads.
 *
 * Open by design so feature modules can define custom [ActionPayload] implementations
 * and register decoders/handlers without modifying the core `action-engine` module.
 * Non-sealed interfaces are polymorphically serializable by default in kotlinx.serialization.
 */
interface ActionPayload

/**
 * Closed hierarchy of standard BFF-defined action payloads.
 */
@Serializable
sealed interface BuiltInActionPayload : ActionPayload

/** Navigates to a destination [route]. */
@Serializable
data class NavigatePayload(
    val route: String,
    val params: Map<String, String> = emptyMap(),
    @SerialName("clear_back_stack")
    val clearBackStack: Boolean = false,
    @SerialName("launch_mode")
    val launchMode: String? = null,
) : BuiltInActionPayload

/** Pops one or more screens from the navigation back stack with an optional [result]. */
@Serializable
data class BackPressPayload(
    val result: Map<String, JsonElement> = emptyMap(),
    val steps: Int = 1,
) : BuiltInActionPayload

/** Opens a confirmation, informational, or rich SDUI snippet dialog/bottom-sheet overlay identified by [dialogId]. */
@Serializable
data class OpenDialogPayload(
    @SerialName("dialog_id")
    val dialogId: String,
    val title: String,
    val message: String? = null,
    val dismissible: Boolean = true,
    @SerialName("positive_action")
    val positiveAction: ActionRef? = null,
    @SerialName("negative_action")
    val negativeAction: ActionRef? = null,
    @SerialName("presentation")
    val presentation: OverlayPresentation = OverlayPresentation.DIALOG,
    val snippets: List<SnippetEnvelope> = emptyList(),
    @SerialName("sheet_screen_id")
    val sheetScreenId: String? = null,
) : BuiltInActionPayload

/** Dismisses an active overlay identified by [overlayId], or all overlays when [all] is `true`. */
@Serializable
data class DismissOverlayPayload(
    @SerialName("overlay_id")
    val overlayId: String? = null,
    val all: Boolean = false,
) : BuiltInActionPayload

/** Displays a transient toast/snackbar notification. */
@Serializable
data class ShowToastPayload(
    val message: String,
    @SerialName("duration_ms")
    val durationMs: Long = 2500L,
    val variant: String = "info",
) : BuiltInActionPayload

/** Requests an HTTP API call and optional follow-up actions on success or error. */
@Serializable
data class CallApiPayload(
    val endpoint: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: JsonObject? = null,
    @SerialName("on_success")
    val onSuccess: ActionRef? = null,
    @SerialName("on_error")
    val onError: ActionRef? = null,
) : BuiltInActionPayload

/** Persists a [key]-[value] entry in the given [scope] (e.g. `"session"` or `"disk"`). */
@Serializable
data class SaveStatePayload(
    val key: String,
    val value: JsonElement,
    val scope: String = "session",
) : BuiltInActionPayload

/** Removes a [key] from the given [scope]. */
@Serializable
data class DeleteStatePayload(
    val key: String,
    val scope: String = "session",
) : BuiltInActionPayload

/** Emits a structured analytics event with [eventName] and typed JSON [properties]. */
@Serializable
data class TrackEventPayload(
    @SerialName("event_name")
    val eventName: String,
    val properties: Map<String, JsonElement> = emptyMap(),
) : BuiltInActionPayload

/** Requests a refresh of [pageId] (or the current active page when `null`). */
@Serializable
data class RefreshPagePayload(
    @SerialName("page_id")
    val pageId: String? = null,
    @SerialName("show_loader")
    val showLoader: Boolean = false,
) : BuiltInActionPayload

/** Copies [text] to the system clipboard with an optional user-facing [label]. */
@Serializable
data class CopyToClipboardPayload(
    val text: String,
    val label: String? = null,
) : BuiltInActionPayload

/** Applies an incremental snippet mutation (e.g. `"replace"`, `"append"`, `"remove"`). */
@Serializable
data class UpdateSnippetsPayload(
    val operation: String,
    @SerialName("target_snippet_id")
    val targetSnippetId: String? = null,
    val snippets: List<SnippetEnvelope> = emptyList(),
) : BuiltInActionPayload

/** Composite payload grouping multiple child [actions] for sequential or parallel execution. */
@Serializable
data class BatchPayload(
    val actions: List<ActionRef> = emptyList(),
    @SerialName("stop_on_error")
    val stopOnError: Boolean = false,
    val parallel: Boolean = false,
) : BuiltInActionPayload

/** Triggers a transient visual or tactile feedback effect on the client UI. */
@Serializable
data class TriggerFeedbackPayload(
    val effect: FeedbackEffect = FeedbackEffect.CELEBRATION,
    val title: String? = null,
    val subtitle: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long = DEFAULT_FEEDBACK_DURATION_MS,
    @SerialName("target_id")
    val targetId: String? = null,
) : BuiltInActionPayload

/** Opens an external HTTP/HTTPS/mailto/tel URL using the host OS browser or app launcher. */
@Serializable
data class OpenUrlPayload(
    val url: String,
    @SerialName("external_browser")
    val externalBrowser: Boolean = true,
    @SerialName("fallback_route")
    val fallbackRoute: String? = null,
) : BuiltInActionPayload

/** Triggers the host OS share sheet (or desktop/web fallback) with formatted share text. */
@Serializable
data class SharePayload(
    val title: String? = null,
    val text: String,
    val url: String? = null,
    @SerialName("chooser_title")
    val chooserTitle: String? = null,
) : BuiltInActionPayload

/** Requests a runtime OS permission with optional grant/denial follow-up actions. */
@Serializable
data class RequestPermissionPayload(
    val permission: OsPermissionKind,
    val rationale: String? = null,
    @SerialName("on_granted")
    val onGranted: ActionRef? = null,
    @SerialName("on_denied")
    val onDenied: ActionRef? = null,
) : BuiltInActionPayload

/** Prompts OS biometric / device credential authentication with follow-up actions. */
@Serializable
data class BiometricAuthPayload(
    val reason: String,
    val title: String? = null,
    @SerialName("allow_device_credential")
    val allowDeviceCredential: Boolean = true,
    @SerialName("on_authenticated")
    val onAuthenticated: ActionRef? = null,
    @SerialName("on_failed")
    val onFailed: ActionRef? = null,
) : BuiltInActionPayload
