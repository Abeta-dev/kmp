package infra_test

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"os"
	"path/filepath"
	"testing"

	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

var updateGolden = flag.Bool("update", false, "update golden JSON fixtures")

func goldenDir(t *testing.T) string {
	t.Helper()
	return filepath.Join("..", "..", "testdata", "golden")
}

func canonicalActionBatch() actions.ActionTrigger {
	return actions.NewBatchAction("batch_onboarding_submit", actions.BatchPayload{
		Actions: []actions.ActionTrigger{
			actions.NewSaveStateAction("save_onboarding_draft", actions.SaveStatePayload{
				Key:   "onboarding_completed",
				Value: true,
				Scope: "disk",
			}),
			actions.NewCallAPIAction("submit_onboarding", actions.CallAPIPayload{
				Endpoint: "/api/v1/actions/execute",
				Method:   "POST",
				Body:     map[string]any{"form_id": "workspace_onboarding"},
				OnSuccess: func() *actions.ActionTrigger {
					t := actions.NewNavigateAction("nav_home_after_onboarding", actions.NavigatePayload{
						Route:          "/screens/home",
						ClearBackStack: true,
					})
					return &t
				}(),
			}).WithValidations(
				actions.ValidationRule{
					FieldID: "full_name",
					Rule:    "required",
					Message: "Full name is required",
				},
				actions.ValidationRule{
					FieldID: "work_email",
					Rule:    "regex",
					Value:   `^[^@\s]+@[^@\s]+\.[^@\s]+$`,
					Message: "Enter a valid email address",
				},
			),
		},
		StopOnError: true,
	}).WithSecondaryActions(
		actions.NewTrackEventAction("track_onboarding_batch", actions.TrackEventPayload{
			EventName:  "onboarding_batch_dispatched",
			Properties: map[string]any{"screen": "form-demo"},
		}),
	)
}

func writeOrCompareGolden(t *testing.T, filename string, value any) []byte {
	t.Helper()

	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	if err := enc.Encode(value); err != nil {
		t.Fatalf("encode %s: %v", filename, err)
	}
	got := buf.Bytes()

	path := filepath.Join(goldenDir(t), filename)
	if *updateGolden {
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatalf("mkdir golden dir: %v", err)
		}
		if err := os.WriteFile(path, got, 0o644); err != nil {
			t.Fatalf("write golden %s: %v", path, err)
		}
		return got
	}

	want, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read golden %s: %v", path, err)
	}
	if !bytes.Equal(bytes.TrimSpace(got), bytes.TrimSpace(want)) {
		t.Fatalf("golden mismatch for %s:\n--- got ---\n%s\n--- want ---\n%s", filename, string(got), string(want))
	}
	return want
}

func TestGoldenFixtures_RoundTrip(t *testing.T) {
	ctx := context.Background()
	repo := infra.NewInMemoryScreenRepository()

	homePage, err := repo.GetScreen(ctx, "home")
	if err != nil {
		t.Fatalf("GetScreen(home): %v", err)
	}
	homeBytes := writeOrCompareGolden(t, "home_screen.json", homePage)

	var decodedHome snippets.PageResponse
	if err := json.Unmarshal(homeBytes, &decodedHome); err != nil {
		t.Fatalf("round-trip unmarshal home_screen.json: %v", err)
	}
	if decodedHome.PageID != "home" || len(decodedHome.Snippets) == 0 {
		t.Fatalf("unexpected decoded home screen: %+v", decodedHome)
	}

	formPage, err := repo.GetScreen(ctx, "form-demo")
	if err != nil {
		t.Fatalf("GetScreen(form-demo): %v", err)
	}
	formBytes := writeOrCompareGolden(t, "form_screen.json", formPage)

	var decodedForm snippets.PageResponse
	if err := json.Unmarshal(formBytes, &decodedForm); err != nil {
		t.Fatalf("round-trip unmarshal form_screen.json: %v", err)
	}
	if decodedForm.PageID != "form-demo" || len(decodedForm.Snippets) != 2 {
		t.Fatalf("unexpected decoded form screen: %+v", decodedForm)
	}
	formData, err := snippets.DecodeSnippetData[snippets.FormSnippetData](decodedForm.Snippets[1])
	if err != nil || formData.FormID != "workspace_onboarding" || len(formData.Fields) != 3 {
		t.Fatalf("unexpected decoded form snippet: %+v, err=%v", formData, err)
	}

	batchAction := canonicalActionBatch()
	batchBytes := writeOrCompareGolden(t, "action_batch.json", batchAction)

	var decodedBatch actions.ActionTrigger
	if err := json.Unmarshal(batchBytes, &decodedBatch); err != nil {
		t.Fatalf("round-trip unmarshal action_batch.json: %v", err)
	}
	batchPayload, err := actions.DecodePayload[actions.BatchPayload](decodedBatch)
	if err != nil || len(batchPayload.Actions) != 2 || !batchPayload.StopOnError {
		t.Fatalf("unexpected decoded batch payload: %+v, err=%v", batchPayload, err)
	}

	containersPage, err := repo.GetScreen(ctx, "containers")
	if err != nil {
		t.Fatalf("GetScreen(containers): %v", err)
	}
	containersBytes := writeOrCompareGolden(t, "containers_screen.json", containersPage)

	var decodedContainers snippets.PageResponse
	if err := json.Unmarshal(containersBytes, &decodedContainers); err != nil {
		t.Fatalf("round-trip unmarshal containers_screen.json: %v", err)
	}
	if decodedContainers.PageID != "containers" || len(decodedContainers.Snippets) != 7 {
		t.Fatalf("unexpected decoded containers screen: %+v", decodedContainers)
	}
	if decodedContainers.Pagination == nil || !decodedContainers.Pagination.HasMore || decodedContainers.PullToRefresh == nil {
		t.Fatalf("expected pagination and pull_to_refresh on containers screen: %+v", decodedContainers)
	}

	hdrData, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](decodedContainers.Snippets[0])
	if err != nil || hdrData.Avatar == nil || hdrData.TrailingAction == nil || hdrData.TrailingAction.OnClick == nil {
		t.Fatalf("unexpected containers header: %+v, err=%v", hdrData, err)
	}
	dlgPayload, err := actions.DecodePayload[actions.OpenDialogPayload](*hdrData.TrailingAction.OnClick)
	if err != nil || dlgPayload.Presentation != actions.PresentationBottomSheet {
		t.Fatalf("expected bottom_sheet presentation on header action, got %+v, err=%v", dlgPayload, err)
	}

	carData, err := snippets.DecodeSnippetData[snippets.CarouselSnippetData](decodedContainers.Snippets[1])
	if err != nil || len(carData.Items) != 2 {
		t.Fatalf("unexpected carousel data: %+v, err=%v", carData, err)
	}
	gridData, err := snippets.DecodeSnippetData[snippets.GridSnippetData](decodedContainers.Snippets[2])
	if err != nil || gridData.Columns != 2 || len(gridData.Items) != 2 {
		t.Fatalf("unexpected grid data: %+v, err=%v", gridData, err)
	}
	tabsData, err := snippets.DecodeSnippetData[snippets.TabsSnippetData](decodedContainers.Snippets[3])
	if err != nil || len(tabsData.Tabs) != 2 {
		t.Fatalf("unexpected tabs data: %+v, err=%v", tabsData, err)
	}
	accData, err := snippets.DecodeSnippetData[snippets.AccordionSnippetData](decodedContainers.Snippets[4])
	if err != nil || !accData.InitiallyExpanded || len(accData.Snippets) != 1 {
		t.Fatalf("unexpected accordion data: %+v, err=%v", accData, err)
	}
	skelCard, err := snippets.DecodeSnippetData[snippets.CardSnippetData](decodedContainers.Snippets[5])
	if err != nil || skelCard.Avatar == nil || len(skelCard.Skeletons) != 3 {
		t.Fatalf("unexpected skeleton card data: %+v, err=%v", skelCard, err)
	}
	ctrlForm, err := snippets.DecodeSnippetData[snippets.FormSnippetData](decodedContainers.Snippets[6])
	if err != nil || len(ctrlForm.Switches) != 1 || len(ctrlForm.Checkboxes) != 1 || len(ctrlForm.Sliders) != 1 {
		t.Fatalf("unexpected controls form data: %+v, err=%v", ctrlForm, err)
	}

	storeHomePage, err := repo.GetScreen(ctx, "store-home")
	if err != nil {
		t.Fatalf("GetScreen(store-home): %v", err)
	}
	storeHomeBytes := writeOrCompareGolden(t, "store_home.json", storeHomePage)

	var decodedStoreHome snippets.PageResponse
	if err := json.Unmarshal(storeHomeBytes, &decodedStoreHome); err != nil {
		t.Fatalf("round-trip unmarshal store_home.json: %v", err)
	}
	if decodedStoreHome.PageID != "store-home" || len(decodedStoreHome.Snippets) != 4 || decodedStoreHome.FloatingCart == nil {
		t.Fatalf("unexpected decoded store-home screen: %+v", decodedStoreHome)
	}
	if decodedStoreHome.Toolbar == nil || decodedStoreHome.Toolbar.Title == nil || decodedStoreHome.Toolbar.Title.Text != "Abeta Fresh" {
		t.Fatalf("unexpected store-home toolbar: %+v", decodedStoreHome.Toolbar)
	}
	etaBanner, err := snippets.DecodeSnippetData[snippets.BannerSnippetData](decodedStoreHome.Snippets[0])
	if err != nil || decodedStoreHome.Snippets[0].Motion == nil || decodedStoreHome.Snippets[0].Motion.LoopEffect != atoms.MotionLoopPulse {
		t.Fatalf("unexpected store-home ETA banner: %+v, motion=%+v, err=%v", etaBanner, decodedStoreHome.Snippets[0].Motion, err)
	}
	promoCar, err := snippets.DecodeSnippetData[snippets.CarouselSnippetData](decodedStoreHome.Snippets[1])
	if err != nil || len(promoCar.Items) != 3 {
		t.Fatalf("unexpected store-home promo carousel: %+v, err=%v", promoCar, err)
	}
	catTabs, err := snippets.DecodeSnippetData[snippets.TabsSnippetData](decodedStoreHome.Snippets[2])
	if err != nil || len(catTabs.Tabs) != 3 {
		t.Fatalf("unexpected store-home category tabs: %+v, err=%v", catTabs, err)
	}
	firstGrid, err := snippets.DecodeSnippetData[snippets.GridSnippetData](catTabs.Tabs[0].Snippets[0])
	if err != nil || firstGrid.Columns != 2 || len(firstGrid.Items) != 4 {
		t.Fatalf("unexpected fruits grid: %+v, err=%v", firstGrid, err)
	}
	firstProd, err := snippets.DecodeSnippetData[snippets.ProductCardSnippetData](firstGrid.Items[0])
	if err != nil || firstProd.Stepper.Quantity != 1 || firstProd.UnitPriceCents <= 0 {
		t.Fatalf("unexpected first product card: %+v, err=%v", firstProd, err)
	}
	secondProd, err := snippets.DecodeSnippetData[snippets.ProductCardSnippetData](firstGrid.Items[1])
	if err != nil || secondProd.Stepper.Quantity != 0 || secondProd.Stepper.Label != "ADD" {
		t.Fatalf("unexpected second product card: %+v, err=%v", secondProd, err)
	}
	offersAcc, err := snippets.DecodeSnippetData[snippets.AccordionSnippetData](decodedStoreHome.Snippets[3])
	if err != nil || len(offersAcc.Snippets) != 2 {
		t.Fatalf("unexpected store-home offers accordion: %+v, err=%v", offersAcc, err)
	}
	if decodedStoreHome.FloatingCart.CTA.OnClick == nil {
		t.Fatal("expected FloatingCart CTA OnClick trigger")
	}
	checkoutDlg, err := actions.DecodePayload[actions.OpenDialogPayload](*decodedStoreHome.FloatingCart.CTA.OnClick)
	if err != nil || checkoutDlg.Presentation != actions.PresentationBottomSheet || checkoutDlg.PositiveAction == nil {
		t.Fatalf("unexpected FloatingCart checkout dialog: %+v, err=%v", checkoutDlg, err)
	}
	orderAPI, err := actions.DecodePayload[actions.CallAPIPayload](*checkoutDlg.PositiveAction)
	if err != nil || orderAPI.Endpoint != "/api/v1/orders/express" {
		t.Fatalf("unexpected positive action API payload: %+v, err=%v", orderAPI, err)
	}
}
