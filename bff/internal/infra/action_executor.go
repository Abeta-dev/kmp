package infra

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"sync"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
)

const (
	maxBatchDepth        = 5
	maxTotalBatchActions = 50
	maxExecutorStateKeys = 512
)

// DefaultActionExecutor executes SDUI actions in-process and manages
// bounded session state for save_state/delete_state actions.
type DefaultActionExecutor struct {
	mu         sync.RWMutex
	state      map[string]any
	stateOrder []string
}

// NewDefaultActionExecutor constructs a DefaultActionExecutor.
func NewDefaultActionExecutor() *DefaultActionExecutor {
	return &DefaultActionExecutor{
		state: make(map[string]any),
	}
}

// Execute processes input.Trigger and returns an ActionExecutionResult.
func (e *DefaultActionExecutor) Execute(ctx context.Context, input domain.ActionExecutionInput) (domain.ActionExecutionResult, error) {
	executed := 0
	return e.executeWithBudget(ctx, input, 0, &executed)
}

func (e *DefaultActionExecutor) executeWithBudget(
	ctx context.Context,
	input domain.ActionExecutionInput,
	depth int,
	executed *int,
) (domain.ActionExecutionResult, error) {
	if err := ctx.Err(); err != nil {
		return domain.ActionExecutionResult{}, apperror.Wrap(err, apperror.CodeUnavailable, "request cancelled")
	}

	t := input.Trigger
	res := domain.ActionExecutionResult{
		ActionID:   t.ID,
		ActionType: t.Type,
		Status:     "executed",
	}

	*executed++
	if *executed > maxTotalBatchActions {
		return res, apperror.BadRequest("batch action budget exceeded")
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
		if p.Endpoint == "/api/v1/orders/express" && strings.EqualFold(strings.TrimSpace(p.Method), "POST") {
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
		if p.Endpoint == "/api/v1/pay/transfer" && strings.EqualFold(strings.TrimSpace(p.Method), "POST") {
			res.FollowUpActions = append(res.FollowUpActions,
				actions.NewTriggerFeedbackAction("pay_transfer_celebration", actions.TriggerFeedbackPayload{
					Effect:     actions.FeedbackCelebration,
					Title:      "Transfer Sent!",
					Subtitle:   "Instant settlement #PAY-3081 confirmed via Abeta Pay",
					DurationMs: 2400,
				}),
				actions.NewShowToastAction("pay_transfer_toast", actions.ShowToastPayload{
					Message:    "Transfer #PAY-3081 settled instantly!",
					DurationMs: 2500,
					Variant:    "success",
				}),
				actions.NewNavigateAction("pay_transfer_nav_receipt", actions.NavigatePayload{
					Route: "pay-receipt",
				}),
			)
		}
		if p.OnSuccess != nil {
			res.FollowUpActions = append(res.FollowUpActions, *p.OnSuccess)
		}
		if len(input.FormValues) > 0 {
			res.UpdatedState = map[string]any{"last_submitted_form": cloneStateValue(input.FormValues)}
		}

	case actions.ActionSaveState:
		p, err := actions.DecodePayload[actions.SaveStatePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Key == "" {
			return res, apperror.BadRequest("save_state.key must not be empty")
		}
		stored := cloneStateValue(p.Value)
		e.setStateBounded(p.Key, stored)
		res.UpdatedState = map[string]any{p.Key: cloneStateValue(stored)}
		res.Message = fmt.Sprintf("saved state key %s", p.Key)

	case actions.ActionDeleteState:
		p, err := actions.DecodePayload[actions.DeleteStatePayload](t)
		if err != nil {
			return res, apperror.BadRequest(err.Error())
		}
		if p.Key == "" {
			return res, apperror.BadRequest("delete_state.key must not be empty")
		}
		e.deleteState(p.Key)
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
			if strings.TrimSpace(child.ID) == "" || strings.TrimSpace(string(child.Type)) == "" {
				childErr := apperror.BadRequest("batch child requires non-empty id and type")
				if p.StopOnError {
					return res, childErr
				}
				continue
			}
			childInput := domain.ActionExecutionInput{
				Trigger:    child,
				FormValues: input.FormValues,
				ScreenID:   input.ScreenID,
				Metadata:   input.Metadata,
			}
			childRes, err := e.executeWithBudget(ctx, childInput, depth+1, executed)
			if err != nil {
				if *executed > maxTotalBatchActions || p.StopOnError {
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
		if err := validateOpenURL(p.URL); err != nil {
			return res, err
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

// GetState returns a cloned snapshot value from the executor's bounded in-memory state store.
func (e *DefaultActionExecutor) GetState(key string) (any, bool) {
	e.mu.RLock()
	defer e.mu.RUnlock()
	v, ok := e.state[key]
	if !ok {
		return nil, false
	}
	return cloneStateValue(v), true
}

func (e *DefaultActionExecutor) setStateBounded(key string, val any) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if _, exists := e.state[key]; exists {
		e.state[key] = val
		return
	}
	for len(e.state) >= maxExecutorStateKeys && len(e.stateOrder) > 0 {
		oldest := e.stateOrder[0]
		e.stateOrder = e.stateOrder[1:]
		delete(e.state, oldest)
	}
	e.state[key] = val
	e.stateOrder = append(e.stateOrder, key)
}

func (e *DefaultActionExecutor) deleteState(key string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if _, exists := e.state[key]; !exists {
		return
	}
	delete(e.state, key)
	for i, k := range e.stateOrder {
		if k == key {
			e.stateOrder = append(e.stateOrder[:i], e.stateOrder[i+1:]...)
			break
		}
	}
}

func validateOpenURL(raw string) error {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return apperror.BadRequest("open_url.url must not be empty")
	}
	parsed, err := url.Parse(trimmed)
	if err != nil || parsed.Scheme == "" {
		return apperror.BadRequest("open_url.url must be a valid absolute URL")
	}
	switch strings.ToLower(parsed.Scheme) {
	case "http", "https":
		if strings.TrimSpace(parsed.Host) == "" {
			return apperror.BadRequest("open_url.url must include a host")
		}
	case "mailto", "tel":
		if strings.TrimSpace(parsed.Opaque) == "" && strings.TrimSpace(parsed.Path) == "" {
			return apperror.BadRequest("open_url.url must include a target")
		}
	default:
		return apperror.BadRequest("open_url.url scheme is not allowed")
	}
	return nil
}

func cloneStateValue(v any) any {
	switch typed := v.(type) {
	case map[string]any:
		out := make(map[string]any, len(typed))
		for k, item := range typed {
			out[k] = cloneStateValue(item)
		}
		return out
	case map[string]string:
		out := make(map[string]string, len(typed))
		for k, item := range typed {
			out[k] = item
		}
		return out
	case []any:
		out := make([]any, len(typed))
		for i, item := range typed {
			out[i] = cloneStateValue(item)
		}
		return out
	case []string:
		out := make([]string, len(typed))
		copy(out, typed)
		return out
	default:
		return v
	}
}
