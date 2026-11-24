// Package actions defines pure wire-contract SDUI action types, triggers,
// and typed payload structs matching the KMP actions schema.
// This package contains zero execution logic.
package actions

import (
	"encoding/json"
	"fmt"
)

// ActionType identifies the client or server action kind on the wire.
type ActionType string

const (
	ActionNavigate          ActionType = "navigate"
	ActionBackPress         ActionType = "back_press"
	ActionOpenDialog        ActionType = "open_dialog"
	ActionDismissOverlay    ActionType = "dismiss_overlay"
	ActionShowToast         ActionType = "show_toast"
	ActionCallAPI           ActionType = "call_api"
	ActionSaveState         ActionType = "save_state"
	ActionDeleteState       ActionType = "delete_state"
	ActionTrackEvent        ActionType = "track_event"
	ActionRefreshPage       ActionType = "refresh_page"
	ActionCopyToClipboard   ActionType = "copy_to_clipboard"
	ActionUpdateSnippets    ActionType = "update_snippets"
	ActionBatch             ActionType = "batch"
	ActionTriggerFeedback   ActionType = "trigger_feedback"
	ActionOpenURL           ActionType = "open_url"
	ActionShare             ActionType = "share"
	ActionRequestPermission ActionType = "request_permission"
	ActionBiometricAuth     ActionType = "biometric_auth"
)

// ValidationRule defines a declarative client/server form validation rule
// attached to an ActionTrigger.
type ValidationRule struct {
	FieldID string `json:"field_id"`
	Rule    string `json:"rule"`
	Value   string `json:"value,omitempty"`
	Message string `json:"message"`
}

// TrackingSpec describes declarative analytics tracking metadata attached to an ActionTrigger.
// It matches KMP TrackingContract (event_name + string-only properties) 1:1 on the wire.
type TrackingSpec struct {
	EventName  string            `json:"event_name"`
	Properties map[string]string `json:"properties,omitempty"`
}

// ActionTrigger is the lightweight wire envelope embedded by SDUI atoms and pages.
type ActionTrigger struct {
	ID               string           `json:"id"`
	Type             ActionType       `json:"type"`
	Payload          json.RawMessage  `json:"payload,omitempty"`
	Validations      []ValidationRule `json:"validations,omitempty"`
	SecondaryActions []ActionTrigger  `json:"secondary_actions,omitempty"`
	Tracking         *TrackingSpec    `json:"tracking,omitempty"`
}

// WithValidations returns a copy of the trigger with validation rules appended.
func (t ActionTrigger) WithValidations(rules ...ValidationRule) ActionTrigger {
	t.Validations = append(append([]ValidationRule(nil), t.Validations...), rules...)
	return t
}

// WithSecondaryActions returns a copy of the trigger with secondary actions appended.
func (t ActionTrigger) WithSecondaryActions(actions ...ActionTrigger) ActionTrigger {
	t.SecondaryActions = append(append([]ActionTrigger(nil), t.SecondaryActions...), actions...)
	return t
}

// WithTracking returns a copy of the trigger with declarative tracking metadata attached.
func (t ActionTrigger) WithTracking(eventName string, props map[string]string) ActionTrigger {
	var clonedProps map[string]string
	if len(props) > 0 {
		clonedProps = make(map[string]string, len(props))
		for k, v := range props {
			clonedProps[k] = v
		}
	}
	t.Tracking = &TrackingSpec{
		EventName:  eventName,
		Properties: clonedProps,
	}
	return t
}

// NewTrigger constructs an ActionTrigger by encoding payload to JSON.
// If payload is nil, Payload is omitted.
func NewTrigger(id string, actionType ActionType, payload any) (ActionTrigger, error) {
	t := ActionTrigger{
		ID:   id,
		Type: actionType,
	}
	if payload == nil {
		return t, nil
	}
	raw, err := json.Marshal(payload)
	if err != nil {
		return ActionTrigger{}, fmt.Errorf("actions: marshal %s payload: %w", actionType, err)
	}
	t.Payload = raw
	return t, nil
}

// mustTrigger encodes a known-serializable typed payload struct into an ActionTrigger.
func mustTrigger(id string, actionType ActionType, payload any) ActionTrigger {
	t, err := NewTrigger(id, actionType, payload)
	if err != nil {
		panic(err)
	}
	return t
}

// DecodePayload unmarshals a trigger's raw JSON payload into T.
func DecodePayload[T any](trigger ActionTrigger) (T, error) {
	var zero T
	if len(trigger.Payload) == 0 {
		return zero, fmt.Errorf("actions: empty payload for action %q (%s)", trigger.ID, trigger.Type)
	}
	var out T
	if err := json.Unmarshal(trigger.Payload, &out); err != nil {
		return zero, fmt.Errorf("actions: decode %s payload: %w", trigger.Type, err)
	}
	return out, nil
}

// ── Typed Payload Structs ──────────────────────────────────────────────────────

// NavigatePayload navigates to a target route.
type NavigatePayload struct {
	Route          string            `json:"route"`
	Params         map[string]string `json:"params,omitempty"`
	ClearBackStack bool              `json:"clear_back_stack,omitempty"`
	LaunchMode     string            `json:"launch_mode,omitempty"`
}

// BackPressPayload pops one or more screens from the navigation back stack.
type BackPressPayload struct {
	Result map[string]any `json:"result,omitempty"`
	Steps  int            `json:"steps,omitempty"`
}

// OverlayPresentation identifiers for OpenDialogPayload.Presentation.
const (
	PresentationDialog      = "dialog"
	PresentationBottomSheet = "bottom_sheet"
)

// OpenDialogPayload opens a confirmation or informational dialog/bottom-sheet overlay.
type OpenDialogPayload struct {
	DialogID       string         `json:"dialog_id"`
	Title          string         `json:"title"`
	Message        string         `json:"message,omitempty"`
	Presentation   string         `json:"presentation,omitempty"`
	Dismissible    *bool          `json:"dismissible,omitempty"`
	PositiveAction *ActionTrigger `json:"positive_action,omitempty"`
	NegativeAction *ActionTrigger `json:"negative_action,omitempty"`
	Snippets       []any          `json:"snippets,omitempty"`
	SheetScreenID  string         `json:"sheet_screen_id,omitempty"`
}

// DismissOverlayPayload closes an active dialog or bottom sheet.
type DismissOverlayPayload struct {
	OverlayID string `json:"overlay_id,omitempty"`
	All       bool   `json:"all,omitempty"`
}

// ShowToastPayload displays a transient notification banner/toast.
type ShowToastPayload struct {
	Message    string `json:"message"`
	DurationMs int    `json:"duration_ms,omitempty"`
	Variant    string `json:"variant,omitempty"`
}

// CallAPIPayload instructs the client or BFF to invoke a backend endpoint.
type CallAPIPayload struct {
	Endpoint  string            `json:"endpoint"`
	Method    string            `json:"method"`
	Headers   map[string]string `json:"headers,omitempty"`
	Body      map[string]any    `json:"body,omitempty"`
	OnSuccess *ActionTrigger    `json:"on_success,omitempty"`
	OnError   *ActionTrigger    `json:"on_error,omitempty"`
}

// SaveStatePayload persists a key-value entry in local or session state.
type SaveStatePayload struct {
	Key   string `json:"key"`
	Value any    `json:"value"`
	Scope string `json:"scope,omitempty"`
}

// DeleteStatePayload removes a key from local or session state.
type DeleteStatePayload struct {
	Key   string `json:"key"`
	Scope string `json:"scope,omitempty"`
}

// TrackEventPayload emits a structured analytics event.
type TrackEventPayload struct {
	EventName  string         `json:"event_name"`
	Properties map[string]any `json:"properties,omitempty"`
}

// RefreshPagePayload triggers a reload of the current or specified screen.
type RefreshPagePayload struct {
	PageID     string `json:"page_id,omitempty"`
	ShowLoader bool   `json:"show_loader,omitempty"`
}

// CopyToClipboardPayload copies text to the device clipboard.
type CopyToClipboardPayload struct {
	Text  string `json:"text"`
	Label string `json:"label,omitempty"`
}

// UpdateSnippetsPayload mutates visible snippets on the current page.
// Snippets remain raw JSON messages here so actions stays decoupled from snippets.
type UpdateSnippetsPayload struct {
	Operation       string            `json:"operation"`
	TargetSnippetID string            `json:"target_snippet_id,omitempty"`
	Snippets        []json.RawMessage `json:"snippets,omitempty"`
}

// BatchPayload groups multiple ActionTriggers for sequential or parallel execution.
type BatchPayload struct {
	Actions     []ActionTrigger `json:"actions"`
	StopOnError bool            `json:"stop_on_error,omitempty"`
	Parallel    bool            `json:"parallel,omitempty"`
}

// FeedbackEffect wire identifiers for TriggerFeedbackPayload.Effect.
const (
	FeedbackCelebration = "celebration"
	FeedbackCartBounce  = "cart_bounce"
	FeedbackShakeError  = "shake_error"
	FeedbackHapticTick  = "haptic_tick"
)

// TriggerFeedbackPayload triggers celebratory, cart bounce, error shake, or haptic feedback on the client.
type TriggerFeedbackPayload struct {
	Effect     string `json:"effect"`
	Title      string `json:"title,omitempty"`
	Subtitle   string `json:"subtitle,omitempty"`
	DurationMs int    `json:"duration_ms,omitempty"`
	TargetID   string `json:"target_id,omitempty"`
}

// OsPermission identifiers for RequestPermissionPayload.Permission.
const (
	PermissionLocation      = "location"
	PermissionNotifications = "notifications"
	PermissionCamera        = "camera"
	PermissionStorage       = "storage"
)

// OpenURLPayload opens a validated external URL via the host OS launcher.
type OpenURLPayload struct {
	URL             string `json:"url"`
	ExternalBrowser *bool  `json:"external_browser,omitempty"`
	FallbackRoute   string `json:"fallback_route,omitempty"`
}

// SharePayload presents the OS share sheet (or clipboard fallback on desktop).
type SharePayload struct {
	Title        string `json:"title,omitempty"`
	Text         string `json:"text"`
	URL          string `json:"url,omitempty"`
	ChooserTitle string `json:"chooser_title,omitempty"`
}

// RequestPermissionPayload requests an OS runtime permission with follow-up triggers.
type RequestPermissionPayload struct {
	Permission string         `json:"permission"`
	Rationale  string         `json:"rationale,omitempty"`
	OnGranted  *ActionTrigger `json:"on_granted,omitempty"`
	OnDenied   *ActionTrigger `json:"on_denied,omitempty"`
}

// BiometricAuthPayload prompts OS biometric or device credential authentication.
type BiometricAuthPayload struct {
	Reason                string         `json:"reason"`
	Title                 string         `json:"title,omitempty"`
	AllowDeviceCredential *bool          `json:"allow_device_credential,omitempty"`
	OnAuthenticated       *ActionTrigger `json:"on_authenticated,omitempty"`
	OnFailed              *ActionTrigger `json:"on_failed,omitempty"`
}

// ── Constructors ───────────────────────────────────────────────────────────────

// NewNavigateAction constructs a navigate ActionTrigger.
func NewNavigateAction(id string, payload NavigatePayload) ActionTrigger {
	return mustTrigger(id, ActionNavigate, payload)
}

// NewBackPressAction constructs a back_press ActionTrigger.
func NewBackPressAction(id string, payload BackPressPayload) ActionTrigger {
	return mustTrigger(id, ActionBackPress, payload)
}

// NewOpenDialogAction constructs an open_dialog ActionTrigger.
func NewOpenDialogAction(id string, payload OpenDialogPayload) ActionTrigger {
	return mustTrigger(id, ActionOpenDialog, payload)
}

// NewDismissOverlayAction constructs a dismiss_overlay ActionTrigger.
func NewDismissOverlayAction(id string, payload DismissOverlayPayload) ActionTrigger {
	return mustTrigger(id, ActionDismissOverlay, payload)
}

// NewShowToastAction constructs a show_toast ActionTrigger.
func NewShowToastAction(id string, payload ShowToastPayload) ActionTrigger {
	return mustTrigger(id, ActionShowToast, payload)
}

// NewCallAPIAction constructs a call_api ActionTrigger.
func NewCallAPIAction(id string, payload CallAPIPayload) ActionTrigger {
	return mustTrigger(id, ActionCallAPI, payload)
}

// NewSaveStateAction constructs a save_state ActionTrigger.
func NewSaveStateAction(id string, payload SaveStatePayload) ActionTrigger {
	return mustTrigger(id, ActionSaveState, payload)
}

// NewDeleteStateAction constructs a delete_state ActionTrigger.
func NewDeleteStateAction(id string, payload DeleteStatePayload) ActionTrigger {
	return mustTrigger(id, ActionDeleteState, payload)
}

// NewTrackEventAction constructs a track_event ActionTrigger.
func NewTrackEventAction(id string, payload TrackEventPayload) ActionTrigger {
	return mustTrigger(id, ActionTrackEvent, payload)
}

// NewRefreshPageAction constructs a refresh_page ActionTrigger.
func NewRefreshPageAction(id string, payload RefreshPagePayload) ActionTrigger {
	return mustTrigger(id, ActionRefreshPage, payload)
}

// NewCopyToClipboardAction constructs a copy_to_clipboard ActionTrigger.
func NewCopyToClipboardAction(id string, payload CopyToClipboardPayload) ActionTrigger {
	return mustTrigger(id, ActionCopyToClipboard, payload)
}

// NewUpdateSnippetsAction constructs an update_snippets ActionTrigger.
func NewUpdateSnippetsAction(id string, payload UpdateSnippetsPayload) ActionTrigger {
	return mustTrigger(id, ActionUpdateSnippets, payload)
}

// NewBatchAction constructs a batch ActionTrigger.
func NewBatchAction(id string, payload BatchPayload) ActionTrigger {
	return mustTrigger(id, ActionBatch, payload)
}

// NewTriggerFeedbackAction constructs a trigger_feedback ActionTrigger.
func NewTriggerFeedbackAction(id string, payload TriggerFeedbackPayload) ActionTrigger {
	return mustTrigger(id, ActionTriggerFeedback, payload)
}

// NewOpenURLAction constructs an open_url ActionTrigger.
func NewOpenURLAction(id string, payload OpenURLPayload) ActionTrigger {
	return mustTrigger(id, ActionOpenURL, payload)
}

// NewShareAction constructs a share ActionTrigger.
func NewShareAction(id string, payload SharePayload) ActionTrigger {
	return mustTrigger(id, ActionShare, payload)
}

// NewRequestPermissionAction constructs a request_permission ActionTrigger.
func NewRequestPermissionAction(id string, payload RequestPermissionPayload) ActionTrigger {
	return mustTrigger(id, ActionRequestPermission, payload)
}

// NewBiometricAuthAction constructs a biometric_auth ActionTrigger.
func NewBiometricAuthAction(id string, payload BiometricAuthPayload) ActionTrigger {
	return mustTrigger(id, ActionBiometricAuth, payload)
}
