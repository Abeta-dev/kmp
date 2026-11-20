package infra

import (
	"context"
	"fmt"
	"strings"
	"sync"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
)

const maxBatchDepth = 5

// DefaultActionExecutor executes SDUI actions in-process and manages
// lightweight session state for save_state/delete_state actions.
type DefaultActionExecutor struct {
	mu    sync.RWMutex
	state map[string]any
}

// NewDefaultActionExecutor constructs a DefaultActionExecutor.
func NewDefaultActionExecutor() *DefaultActionExecutor {
	return &DefaultActionExecutor{
		state: make(map[string]any),
	}
}

// Execute processes input.Trigger and returns an ActionExecutionResult.
func (e *DefaultActionExecutor) Execute(ctx context.Context, input domain.ActionExecutionInput) (domain.ActionExecutionResult, error) {
	return e.executeWithDepth(ctx, input, 0)
}

func (e *DefaultActionExecutor) executeWithDepth(ctx context.Context, input domain.ActionExecutionInput, depth int) (domain.ActionExecutionResult, error) {
	if err := ctx.Err(); err != nil {
		return domain.ActionExecutionResult{}, apperror.Wrap(err, apperror.CodeUnavailable, "request cancelled")
	}

	t := input.Trigger
	res := domain.ActionExecutionResult{
		ActionID:   t.ID,
		ActionType: t.Type,
		Status:     "executed",
	}

	switch t.Type {
	case actions.ActionNavigate:
		p, err := actions.DecodePayload[actions.NavigatePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Route == "" {
			return res, apperror.BadRequest("navigate.route must not be empty")
		}
		res.Message = fmt.Sprintf("navigated to %s", p.Route)

	case actions.ActionBackPress:
		if len(t.Payload) > 0 {
			if _, err := actions.DecodePayload[actions.BackPressPayload](t); err != nil {
				return res, apperror.BadRequest(err.Error())
			}
		}
		res.Message = "back navigation acknowledged"

	case actions.ActionOpenDialog:
		p, err := actions.DecodePayload[actions.OpenDialogPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.DialogID == "" || p.Title == "" {
			return res, apperror.BadRequest("open_dialog requires dialog_id and title")
		}
		res.Message = fmt.Sprintf("dialog %s opened", p.DialogID)

	case actions.ActionDismissOverlay:
		if len(t.Payload) > 0 {
			if _, err := actions.DecodePayload[actions.DismissOverlayPayload](t); err != nil {
				return res, apperror.BadRequest(err.Error())
			}
		}
		res.Message = "overlay dismissed"

	case actions.ActionShowToast:
		p, err := actions.DecodePayload[actions.ShowToastPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Message == "" {
			return res, apperror.BadRequest("show_toast.message must not be empty")
		}
		res.Message = p.Message

	case actions.ActionCallAPI:
		p, err := actions.DecodePayload[actions.CallAPIPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Endpoint == "" || p.Method == "" {
			return res, apperror.BadRequest("call_api requires endpoint and method")
		}
		res.Message = fmt.Sprintf("executed %s %s", p.Method, p.Endpoint)
		if p.Endpoint == "/api/v1/orders/express" {
			res.FollowUpActions = append(res.FollowUpActions,
				actions.NewTriggerFeedbackAction("express_order_celebration", actions.TriggerFeedbackPayload{
					Effect:     actions.FeedbackCelebration,
					Title:      "Order Confirmed!",
					Subtitle:   "Abeta Fresh rider arriving in 8 minutes",
					DurationMs: 2400,
				}),
				actions.NewShowToastAction("express_order_toast", actions.ShowToastPayload{
					Message:    "Express order confirmed!",
					DurationMs: 2500,
					Variant:    "success",
				}),
			)
		}
		if p.OnSuccess != nil {
			res.FollowUpActions = append(res.FollowUpActions, *p.OnSuccess)
		}
		if len(input.FormValues) > 0 {
			res.UpdatedState = map[string]any{"last_submitted_form": input.FormValues}
		}

	case actions.ActionSaveState:
		p, err := actions.DecodePayload[actions.SaveStatePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Key == "" {
			return res, apperror.BadRequest("save_state.key must not be empty")
		}
		e.mu.Lock()
		e.state[p.Key] = p.Value
		e.mu.Unlock()
		res.UpdatedState = map[string]any{p.Key: p.Value}
		res.Message = fmt.Sprintf("saved state key %s", p.Key)

	case actions.ActionDeleteState:
		p, err := actions.DecodePayload[actions.DeleteStatePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Key == "" {
			return res, apperror.BadRequest("delete_state.key must not be empty")
		}
		e.mu.Lock()
		delete(e.state, p.Key)
		e.mu.Unlock()
		res.UpdatedState = map[string]any{p.Key: nil}
		res.Message = fmt.Sprintf("deleted state key %s", p.Key)

	case actions.ActionTrackEvent:
		p, err := actions.DecodePayload[actions.TrackEventPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.EventName == "" {
			return res, apperror.BadRequest("track_event.event_name must not be empty")
		}
		res.Message = fmt.Sprintf("tracked event %s", p.EventName)

	case actions.ActionRefreshPage:
		if len(t.Payload) > 0 {
			if _, err := actions.DecodePayload[actions.RefreshPagePayload](t); err != nil {
				return res, apperror.BadRequest(err.Error())
			}
		}
		res.Message = "page refresh triggered"

	case actions.ActionCopyToClipboard:
		p, err := actions.DecodePayload[actions.CopyToClipboardPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Text == "" {
			return res, apperror.BadRequest("copy_to_clipboard.text must not be empty")
		}
		res.Message = "copied to clipboard"

	case actions.ActionUpdateSnippets:
		p, err := actions.DecodePayload[actions.UpdateSnippetsPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Operation == "" {
			return res, apperror.BadRequest("update_snippets.operation must not be empty")
		}
		res.Message = fmt.Sprintf("snippet operation %s queued", p.Operation)

	case actions.ActionBatch:
		if depth >= maxBatchDepth {
			return res, apperror.BadRequest("batch nesting depth exceeded")
		}
		p, err := actions.DecodePayload[actions.BatchPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if len(p.Actions) == 0 {
			return res, apperror.BadRequest("batch.actions must not be empty")
		}
		mergedState := make(map[string]any)
		for _, child := range p.Actions {
			childInput := domain.ActionExecutionInput{
				Trigger:    child,
				FormValues: input.FormValues,
				ScreenID:   input.ScreenID,
				Metadata:   input.Metadata,
			}
			childRes, err := e.executeWithDepth(ctx, childInput, depth+1)
			if err != nil {
				if p.StopOnError {
					return res, err
				}
				continue
			}
			res.FollowUpActions = append(res.FollowUpActions, childRes.FollowUpActions...)
			for k, v := range childRes.UpdatedState {
				mergedState[k] = v
			}
		}
		if len(mergedState) > 0 {
			res.UpdatedState = mergedState
		}
		res.Message = fmt.Sprintf("executed batch of %d actions", len(p.Actions))

	case actions.ActionTriggerFeedback:
		p, err := actions.DecodePayload[actions.TriggerFeedbackPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if strings.TrimSpace(p.Effect) == "" {
			return res, apperror.BadRequest("trigger_feedback.effect must not be empty")
		}
		switch p.Effect {
		case actions.FeedbackCelebration, actions.FeedbackCartBounce, actions.FeedbackShakeError, actions.FeedbackHapticTick:
		default:
			return res, apperror.BadRequest(fmt.Sprintf("unsupported feedback effect: %s", p.Effect))
		}
		if p.DurationMs < 0 {
			return res, apperror.BadRequest("trigger_feedback.duration_ms must be non-negative")
		}
		res.Message = fmt.Sprintf("triggered %s feedback", p.Effect)

	case actions.ActionOpenURL:
		p, err := actions.DecodePayload[actions.OpenURLPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if strings.TrimSpace(p.URL) == "" {
			return res, apperror.BadRequest("open_url.url must not be empty")
		}
		res.Message = fmt.Sprintf("opened external url %s", p.URL)

	case actions.ActionShare:
		p, err := actions.DecodePayload[actions.SharePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if strings.TrimSpace(p.Text) == "" && strings.TrimSpace(p.URL) == "" {
			return res, apperror.BadRequest("share requires text or url")
		}
		res.Message = "share sheet dispatched"

	case actions.ActionRequestPermission:
		p, err := actions.DecodePayload[actions.RequestPermissionPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		switch p.Permission {
		case actions.PermissionLocation, actions.PermissionNotifications, actions.PermissionCamera, actions.PermissionStorage:
		default:
			return res, apperror.BadRequest(fmt.Sprintf("unsupported permission: %s", p.Permission))
		}
		res.Message = fmt.Sprintf("requested permission %s", p.Permission)

	case actions.ActionBiometricAuth:
		p, err := actions.DecodePayload[actions.BiometricAuthPayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if strings.TrimSpace(p.Reason) == "" {
			return res, apperror.BadRequest("biometric_auth.reason must not be empty")
		}
		res.Message = "biometric authentication requested"

	default:
		return res, apperror.BadRequest(fmt.Sprintf("unsupported action type: %s", t.Type))
	}

	if len(t.SecondaryActions) > 0 {
		res.FollowUpActions = append(res.FollowUpActions, t.SecondaryActions...)
	}

	return res, nil
}

// GetState returns a snapshot value from the executor's in-memory state store.
func (e *DefaultActionExecutor) GetState(key string) (any, bool) {
	e.mu.RLock()
	defer e.mu.RUnlock()
	v, ok := e.state[key]
	return v, ok
}
