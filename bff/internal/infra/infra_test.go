package infra_test

import (
	"context"
	"encoding/json"
	"testing"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
)

func TestInMemoryScreenRepository(t *testing.T) {
	ctx := context.Background()
	repo := infra.NewInMemoryScreenRepository()

	ids, err := repo.ListScreenIDs(ctx)
	if err != nil {
		t.Fatalf("ListScreenIDs error: %v", err)
	}
	wantIDs := []string{"catalog", "containers", "form-demo", "home", "profile", "store-home", "watch-status"}
	if len(ids) != len(wantIDs) {
		t.Fatalf("got %v, want %v", ids, wantIDs)
	}
	for i, id := range wantIDs {
		if ids[i] != id {
			t.Errorf("ids[%d] = %q, want %q", i, ids[i], id)
		}
		page, err := repo.GetScreen(ctx, id)
		if err != nil {
			t.Fatalf("GetScreen(%q) error: %v", id, err)
		}
		if page.PageID != id || len(page.Snippets) == 0 {
			t.Errorf("invalid screen %q: %+v", id, page)
		}
	}

	if _, err := repo.GetScreen(ctx, "does-not-exist"); !apperror.Is(err, apperror.CodeNotFound) {
		t.Fatalf("expected CodeNotFound, got %v", err)
	}
}

func TestStaticTokenCatalogProvider(t *testing.T) {
	ctx := context.Background()

	def := infra.NewStaticTokenCatalogProvider("")
	cat, err := def.GetCatalog(ctx)
	if err != nil {
		t.Fatalf("GetCatalog error: %v", err)
	}
	if cat.Version != "1.0.0" || len(cat.Colors) == 0 || len(cat.Typography) == 0 || len(cat.Spacing) == 0 || len(cat.CornerRadius) == 0 {
		t.Fatalf("unexpected default catalog: %+v", cat)
	}

	custom := infra.NewStaticTokenCatalogProvider("2.1.0")
	cat2, err := custom.GetCatalog(ctx)
	if err != nil || cat2.Version != "2.1.0" {
		t.Fatalf("unexpected custom catalog: %+v, err=%v", cat2, err)
	}
}

func TestDefaultActionExecutor_AllTypes(t *testing.T) {
	ctx := context.Background()
	exec := infra.NewDefaultActionExecutor()

	toast := actions.NewShowToastAction("toast_ok", actions.ShowToastPayload{Message: "Done", Variant: "success"})

	triggers := []actions.ActionTrigger{
		actions.NewNavigateAction("nav_1", actions.NavigatePayload{Route: "/catalog"}),
		actions.NewBackPressAction("back_1", actions.BackPressPayload{Steps: 1}),
		actions.NewOpenDialogAction("dlg_1", actions.OpenDialogPayload{DialogID: "d1", Title: "Confirm"}),
		actions.NewDismissOverlayAction("dis_1", actions.DismissOverlayPayload{OverlayID: "d1"}),
		toast,
		actions.NewCallAPIAction("api_1", actions.CallAPIPayload{
			Endpoint:  "/api/v1/actions/execute",
			Method:    "POST",
			OnSuccess: &toast,
		}),
		actions.NewSaveStateAction("save_1", actions.SaveStatePayload{Key: "theme", Value: "dark"}),
		actions.NewDeleteStateAction("del_1", actions.DeleteStatePayload{Key: "theme"}),
		actions.NewTrackEventAction("trk_1", actions.TrackEventPayload{EventName: "viewed"}),
		actions.NewRefreshPageAction("ref_1", actions.RefreshPagePayload{PageID: "home"}),
		actions.NewCopyToClipboardAction("cpy_1", actions.CopyToClipboardPayload{Text: "ws_abeta_01"}),
		actions.NewUpdateSnippetsAction("upd_1", actions.UpdateSnippetsPayload{Operation: "append"}),
		actions.NewBatchAction("bat_1", actions.BatchPayload{
			Actions: []actions.ActionTrigger{
				actions.NewSaveStateAction("save_2", actions.SaveStatePayload{Key: "k", Value: "v"}),
				actions.NewTrackEventAction("trk_2", actions.TrackEventPayload{EventName: "batch_item"}),
			},
			StopOnError: true,
		}).WithSecondaryActions(toast),
		actions.NewTriggerFeedbackAction("fb_1", actions.TriggerFeedbackPayload{
			Effect:     actions.FeedbackCelebration,
			Title:      "Order Confirmed!",
			Subtitle:   "Abeta Fresh rider arriving in 8 minutes",
			DurationMs: 2400,
		}),
		actions.NewOpenURLAction("url_1", actions.OpenURLPayload{
			URL: "https://abeta.dev/docs",
		}),
		actions.NewShareAction("shr_1", actions.SharePayload{
			Title: "Abeta Fresh",
			Text:  "Arriving in 8 mins",
			URL:   "https://app.abeta.dev/screens/store-home",
		}),
		actions.NewRequestPermissionAction("perm_1", actions.RequestPermissionPayload{
			Permission: actions.PermissionLocation,
			Rationale:  "Detect nearby express hub",
		}),
		actions.NewBiometricAuthAction("bio_1", actions.BiometricAuthPayload{
			Reason: "Authorize one-tap express payment",
		}),
	}

	for _, tr := range triggers {
		res, err := exec.Execute(ctx, domain.ActionExecutionInput{
			Trigger:    tr,
			FormValues: map[string]string{"full_name": "Alex"},
		})
		if err != nil {
			t.Fatalf("Execute(%s) failed: %v", tr.Type, err)
		}
		if res.Status != "executed" || res.ActionID != tr.ID {
			t.Errorf("unexpected result for %s: %+v", tr.Type, res)
		}
	}

	if v, ok := exec.GetState("k"); !ok || v != "v" {
		t.Errorf("expected state k=v, got %v (ok=%v)", v, ok)
	}
	if _, ok := exec.GetState("theme"); ok {
		t.Error("expected theme key to have been deleted")
	}
}

func TestDefaultActionExecutor_ErrorsAndBatchEdges(t *testing.T) {
	ctx := context.Background()
	exec := infra.NewDefaultActionExecutor()

	cancelledCtx, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := exec.Execute(cancelledCtx, domain.ActionExecutionInput{
		Trigger: actions.NewBackPressAction("b", actions.BackPressPayload{}),
	}); !apperror.Is(err, apperror.CodeUnavailable) {
		t.Fatalf("expected CodeUnavailable on cancelled ctx, got %v", err)
	}

	badTriggers := []actions.ActionTrigger{
		actions.NewNavigateAction("n", actions.NavigatePayload{}),
		{ID: "b_bad", Type: actions.ActionBackPress, Payload: json.RawMessage(`{bad`)},
		actions.NewOpenDialogAction("o", actions.OpenDialogPayload{}),
		{ID: "d_bad", Type: actions.ActionDismissOverlay, Payload: json.RawMessage(`{bad`)},
		actions.NewShowToastAction("t", actions.ShowToastPayload{}),
		actions.NewCallAPIAction("c", actions.CallAPIPayload{}),
		actions.NewSaveStateAction("s", actions.SaveStatePayload{}),
		actions.NewDeleteStateAction("del", actions.DeleteStatePayload{}),
		actions.NewTrackEventAction("te", actions.TrackEventPayload{}),
		{ID: "r_bad", Type: actions.ActionRefreshPage, Payload: json.RawMessage(`{bad`)},
		actions.NewCopyToClipboardAction("cp", actions.CopyToClipboardPayload{}),
		actions.NewUpdateSnippetsAction("up", actions.UpdateSnippetsPayload{}),
		actions.NewBatchAction("bat_empty", actions.BatchPayload{}),
		actions.NewTriggerFeedbackAction("fb_empty", actions.TriggerFeedbackPayload{}),
		actions.NewTriggerFeedbackAction("fb_bad_effect", actions.TriggerFeedbackPayload{Effect: "unknown"}),
		actions.NewTriggerFeedbackAction("fb_neg_dur", actions.TriggerFeedbackPayload{Effect: actions.FeedbackHapticTick, DurationMs: -1}),
		{ID: "fb_bad_json", Type: actions.ActionTriggerFeedback, Payload: json.RawMessage(`{bad`)},
		actions.NewOpenURLAction("url_empty", actions.OpenURLPayload{}),
		actions.NewShareAction("shr_empty", actions.SharePayload{}),
		actions.NewRequestPermissionAction("perm_bad", actions.RequestPermissionPayload{Permission: "root"}),
		actions.NewBiometricAuthAction("bio_empty", actions.BiometricAuthPayload{}),
		{ID: "unk", Type: actions.ActionType("unknown_action")},
	}
	for _, tr := range badTriggers {
		if _, err := exec.Execute(ctx, domain.ActionExecutionInput{Trigger: tr}); !apperror.Is(err, apperror.CodeBadRequest) {
			t.Errorf("expected CodeBadRequest for %s (%s), got %v", tr.ID, tr.Type, err)
		}
	}

	// Batch with StopOnError = false continues past invalid child
	res, err := exec.Execute(ctx, domain.ActionExecutionInput{
		Trigger: actions.NewBatchAction("bat_cont", actions.BatchPayload{
			Actions: []actions.ActionTrigger{
				actions.NewNavigateAction("bad_child", actions.NavigatePayload{}),
				actions.NewShowToastAction("good_child", actions.ShowToastPayload{Message: "ok"}),
			},
			StopOnError: false,
		}),
	})
	if err != nil || res.Status != "executed" {
		t.Fatalf("expected batch with StopOnError=false to succeed, got %+v, err=%v", res, err)
	}

	// Batch with StopOnError = true aborts on invalid child
	if _, err := exec.Execute(ctx, domain.ActionExecutionInput{
		Trigger: actions.NewBatchAction("bat_stop", actions.BatchPayload{
			Actions: []actions.ActionTrigger{
				actions.NewNavigateAction("bad_child", actions.NavigatePayload{}),
			},
			StopOnError: true,
		}),
	}); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected CodeBadRequest on StopOnError=true, got %v", err)
	}

	// Deeply nested batch exceeding max depth
	deep := actions.NewShowToastAction("leaf", actions.ShowToastPayload{Message: "leaf"})
	for range 6 {
		deep = actions.NewBatchAction("nest", actions.BatchPayload{
			Actions:     []actions.ActionTrigger{deep},
			StopOnError: true,
		})
	}
	if _, err := exec.Execute(ctx, domain.ActionExecutionInput{Trigger: deep}); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected CodeBadRequest when batch depth exceeded, got %v", err)
	}
}

func TestDefaultActionExecutor_ExpressOrderFollowUps(t *testing.T) {
	ctx := context.Background()
	exec := infra.NewDefaultActionExecutor()

	res, err := exec.Execute(ctx, domain.ActionExecutionInput{
		Trigger: actions.NewCallAPIAction("place_express_order", actions.CallAPIPayload{
			Endpoint: "/api/v1/orders/express",
			Method:   "POST",
			Body:     map[string]any{"store_brand": "Abeta Fresh"},
		}),
		ScreenID: "store-home",
	})
	if err != nil {
		t.Fatalf("Execute express order failed: %v", err)
	}
	if len(res.FollowUpActions) != 2 {
		t.Fatalf("expected 2 follow-up actions, got %d (%+v)", len(res.FollowUpActions), res.FollowUpActions)
	}
	if res.FollowUpActions[0].Type != actions.ActionTriggerFeedback {
		t.Fatalf("expected first follow-up trigger_feedback, got %s", res.FollowUpActions[0].Type)
	}
	fb, err := actions.DecodePayload[actions.TriggerFeedbackPayload](res.FollowUpActions[0])
	if err != nil || fb.Effect != actions.FeedbackCelebration || fb.Title != "Order Confirmed!" || fb.Subtitle != "Abeta Fresh rider arriving in 8 minutes" || fb.DurationMs != 2400 {
		t.Fatalf("unexpected celebration payload: %+v, err=%v", fb, err)
	}
	if res.FollowUpActions[1].Type != actions.ActionShowToast {
		t.Fatalf("expected second follow-up show_toast, got %s", res.FollowUpActions[1].Type)
	}
	toast, err := actions.DecodePayload[actions.ShowToastPayload](res.FollowUpActions[1])
	if err != nil || toast.Message != "Express order confirmed!" || toast.Variant != "success" {
		t.Fatalf("unexpected toast payload: %+v, err=%v", toast, err)
	}
}
