---
name: abeta-kmp-actions
description: Design and implement SDUI actions in abeta-dev/kmp using the Open/Closed Principle (ActionPayloadDecoder, ActionDecoderRegistry, ActionHandler, ActionRegistry, DefaultActionDispatcher) in KMP and matching ActionTrigger emitters in bff/pkg/sdui/actions. Use when adding or modifying action types or dispatch behavior.
---

# Abeta KMP Actions Engine (OCP)

Use this skill when adding new SDUI action types, payload decoders, handlers, validation rules, or modifying `DefaultActionDispatcher`.

## Core Architecture (Open/Closed Principle)

- **Layer 1 (`sdui-contracts` / `ui-atoms`)**: Emits pure `ActionRef(id, type, payload, validations, secondaryActions)` via `on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, `on_decrement`, `on_complete`, `on_remove`, `on_rate`, `on_action`, `on_dismiss`, and `on_expire`. Never imports `core` or `action-engine`.
- **Layer 2 (`kmp/action-engine`)**: Depends only on `core` and `sdui-contracts`. Key abstractions:
  1. `ActionPayload` (open marker interface) & `BuiltInActionPayload` (sealed hierarchy for all **18 `ActionTypes`**: `navigate`, `back_press`, `open_dialog` with `OverlayPresentation` `"dialog"` \| `"bottom_sheet"`, `dismiss_overlay`, `show_toast`, `call_api`, `save_state`, `delete_state`, `track_event`, `refresh_page`, `copy_to_clipboard`, `update_snippets`, `batch`, `trigger_feedback` (`TriggerFeedbackPayload` / `FeedbackEffect`), `open_url` (`OpenUrlPayload`), `share` (`SharePayload`), `request_permission` (`RequestPermissionPayload` / `OsPermissionKind`), `biometric_auth` (`BiometricAuthPayload`)).
  2. `ActionPayloadDecoder<out P : ActionPayload>` + `ActionDecoderRegistry` (`BuiltInActionDecoders`).
  3. `ActionValidator` (`DefaultActionValidator`) enforcing `ActionRef.validations` (`[{field_id, rule, value, message}]`).
  4. `ActionHandler<in P : ActionPayload>` + `ActionRegistry` (`BuiltInActionHandlers`) returning `AppResult<ActionOutcome>` (including `OpenUrlRequested`, `ShareRequested`, `PermissionRequested`, `BiometricAuthRequested`, `FeedbackTriggered`, `OverlayRequested`).
  5. `ApiCallExecutor` port (`fun interface`) implemented at Layer 3 by `RemoteApiCallExecutor` (`kmp/sdui-layout`) and `WatchRemoteApiCallExecutor` (`kmp/watch-companion`).
  6. `ActionDispatcher` / `DefaultActionDispatcher` (depth-guarded dispatch up to `DEFAULT_MAX_DEPTH = 16`, batch execution, follow-up & `secondary_actions` chaining).
- **Layer 3 Host Orchestration (`kmp/sdui-layout` & `kmp/watch-companion`)**:
  - `LayoutController` routes OS outcomes (`OpenUrlRequested`, `ShareRequested`, `PermissionRequested`, `BiometricAuthRequested`, `ClipboardCopyRequested`) through `PlatformBridgeOutcomeExecutor` + `PlatformBridgeBundle`, maps `OverlayRequested` to `ModalBottomSheet` / `AlertDialog`, routes `FeedbackTriggered` to `FloatingCartDockHost` (`cart_bounce`), `UiCelebrationOverlay` (`celebration`), `errorShakeSequence` / `errorShakeTargetId` (`shake_error`), and `HapticEngine`, and delegates transient offline `call_api` failures to `OfflineActionCoordinator`.
  - `WatchCompanionController` (`kmp/watch-companion`) dispatches actions on wearables without Compose UI.

## Step-by-Step: Adding a New Action

1. **Define Payload**: `@Serializable data class MyPayload(...) : ActionPayload` (outside `BuiltInActionPayload` if custom).
2. **Register Decoder**: Register `kotlinxActionDecoder<MyPayload>()` for `"my_action"` in `ActionDecoderRegistry.toBuilder()`.
3. **Register Handler**: Register `ActionHandler<MyPayload>` in `ActionRegistry.toBuilder()` returning `ActionOutcome`.
4. **Mirror in Go BFF (`bff/pkg/sdui/actions` & `bff/internal/infra/action_executor.go`)**: Add `ActionType` constant, typed payload struct, executor branch, and golden/unit tests.
5. **Test Both Sides**: Cover decoder, validator, handler, `DefaultActionDispatcher`, `apiCheck`, and Go `ActionService`/`DefaultActionExecutor`.

## Verification

```bash
(cd kmp && ./gradlew :action-engine:check :sdui-contracts:check :sdui-layout:check :watch-companion:check spotlessCheck detekt apiCheck)
(cd bff && go test -race -cover ./pkg/sdui/actions/... ./internal/usecase/... ./internal/infra/...)
```
