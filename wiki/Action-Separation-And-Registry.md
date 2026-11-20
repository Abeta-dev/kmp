# Action Separation & Registry

`abeta-dev/kmp` isolates UI rendering from action execution using **wire descriptors (`ActionRef` / `ActionTrigger`)** in Layer 1 and an **Open/Closed action engine (`action-engine`)** in Layer 2.

---

## How It Works

1. **Server emits `ActionTrigger` (`bff/pkg/sdui/actions`)** -> deserialized as `ActionRef` (`id`, `type`, `payload`, `validations`, `secondary_actions`) in `sdui-contracts`.
2. **Atom emits `ActionRef`** via `onAction: (ActionRef) -> Unit` (attached to `on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, `on_decrement`) without interpreting `type` or `payload`.
3. **Orchestrator (`LayoutController` or `WatchCompanionController`) forwards `ActionRef`** to `DefaultActionDispatcher.dispatch(actionRef, context)`.
4. **Dispatcher pipeline**:
   - Guards recursion depth (`DEFAULT_MAX_DEPTH = 16`).
   - Runs `ActionValidator` (`DefaultActionValidator`) over `ActionRef.validations` (`[{field_id, rule, value, message}]`).
   - Decodes `ActionRef.payload` via `ActionDecoderRegistry` (`BuiltInActionDecoders`).
   - Executes the matching `ActionHandler` from `ActionRegistry` (`BuiltInActionHandlers`) or expands `BatchPayload`.
   - Dispatches follow-up actions (`ApiCallResult.followUpActions`, `CallApiPayload.onError`) and `secondary_actions`.
5. **Layer 3 Host & Bridge Handling (`sdui-layout`, `watch-companion`)**:
   - `PlatformBridgeOutcomeExecutor` routes `ClipboardCopyRequested`, `OpenUrlRequested`, `ShareRequested`, `PermissionRequested` (`onGranted` / `onDenied` follow-ups), and `BiometricAuthRequested` (`onAuthenticated` / `onFailed` follow-ups) to `PlatformBridgeBundle` (`core` OS ports).
   - `OverlayRequested` supports `OverlayPresentation` (`"dialog"` -> `AlertDialog` or `"bottom_sheet"` -> `ModalBottomSheet` in `SduiOverlays.kt`).
   - `FeedbackTriggered` routes `FeedbackEffect.CART_BOUNCE` to `FloatingCartDockHost`, `FeedbackEffect.CELEBRATION` to `UiCelebrationOverlay` (dismissed via `LayoutController.dismissFeedback()`), `FeedbackEffect.SHAKE_ERROR` to `errorShakeSequence` / `errorShakeTargetId` (driving `Modifier.shakeOnError`), and triggers `HapticEngine` feedback.
   - Failed `call_api` actions due to offline or transient network failures are queued by `OfflineActionCoordinator` (`OfflineMutationQueue`) for replay via `LayoutController.flushOfflineActions()`.

---

## All 18 Built-In Action Types (`ActionTypes`)

| Action Type | Payload Class (`action-engine`) | Outcome (`ActionOutcome`) |
| :--- | :--- | :--- |
| `navigate` | `NavigatePayload` | `NavigationRequested` |
| `back_press` | `BackPressPayload` | `BackRequested` |
| `open_dialog` | `OpenDialogPayload` (`OverlayPresentation`) | `OverlayRequested` |
| `dismiss_overlay` | `DismissOverlayPayload` | `OverlayDismissed` |
| `show_toast` | `ShowToastPayload` | `ToastRequested` |
| `call_api` | `CallApiPayload` | `ApiCallResult` |
| `save_state` | `SaveStatePayload` | `StateMutated` |
| `delete_state` | `DeleteStatePayload` | `StateMutated` |
| `track_event` | `TrackEventPayload` | `TrackingRequested` |
| `refresh_page` | `RefreshPagePayload` | `RefreshRequested` |
| `copy_to_clipboard` | `CopyToClipboardPayload` | `ClipboardCopyRequested` |
| `update_snippets` | `UpdateSnippetsPayload` | `SnippetsUpdated` |
| `batch` | `BatchPayload` | Aggregated child outcomes |
| `trigger_feedback` | `TriggerFeedbackPayload` (`FeedbackEffect`) | `FeedbackTriggered` |
| `open_url` | `OpenUrlPayload` | `OpenUrlRequested` |
| `share` | `SharePayload` | `ShareRequested` |
| `request_permission` | `RequestPermissionPayload` (`OsPermissionKind`) | `PermissionRequested` |
| `biometric_auth` | `BiometricAuthPayload` | `BiometricAuthRequested` |

---

## Extending with a New Action (OCP Walkthrough)

1. **Define Payload** (`@Serializable` implementing open marker interface `ActionPayload`):
   ```kotlin
   @Serializable
   data class RateOrderPayload(
       val orderId: String,
       val stars: Int,
   ) : ActionPayload
   ```
2. **Register Decoder** in `ActionDecoderRegistry`:
   ```kotlin
   val decoders =
       BuiltInActionDecoders.createDefaultRegistry().toBuilder()
           .register("rate_order", kotlinxActionDecoder<RateOrderPayload>())
           .build()
   ```
3. **Register Handler** in `ActionRegistry`:
   ```kotlin
   val handlers =
       BuiltInActionHandlers.createDefaultRegistry(apiExecutor).toBuilder()
           .register<RateOrderPayload> { payload, _ ->
               AppResult.Success(ActionOutcome.ToastRequested("Rated ${payload.orderId}: ${payload.stars}★"))
           }
           .build()
   ```

Notice **zero changes** are required in `design-tokens`, `sdui-contracts`, `ui-atoms`, or `DefaultActionDispatcher`.
