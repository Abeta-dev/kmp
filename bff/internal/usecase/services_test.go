package usecase_test

import (
	"context"
	"errors"
	"testing"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

type stubScreenRepo struct {
	screen snippets.PageResponse
	ids    []string
	err    error
}

func (s *stubScreenRepo) GetScreen(_ context.Context, _ string) (snippets.PageResponse, error) {
	return s.screen, s.err
}

func (s *stubScreenRepo) ListScreenIDs(_ context.Context) ([]string, error) {
	return s.ids, s.err
}

type stubActionExecutor struct {
	res domain.ActionExecutionResult
	err error
}

func (s *stubActionExecutor) Execute(_ context.Context, _ domain.ActionExecutionInput) (domain.ActionExecutionResult, error) {
	return s.res, s.err
}

type stubCatalogProvider struct {
	cat domain.TokenCatalog
	err error
}

func (s *stubCatalogProvider) GetCatalog(_ context.Context) (domain.TokenCatalog, error) {
	return s.cat, s.err
}

func TestScreenService(t *testing.T) {
	ctx := context.Background()

	svcNil := usecase.NewScreenService(nil)
	if _, err := svcNil.GetScreen(ctx, "home"); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected internal error for nil repo, got %v", err)
	}
	if _, err := svcNil.ListScreens(ctx); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected internal error for nil repo list, got %v", err)
	}

	repo := &stubScreenRepo{
		screen: snippets.NewPage("home", "Home").Build(),
		ids:    []string{"home"},
	}
	svc := usecase.NewScreenService(repo)

	if _, err := svc.GetScreen(ctx, "   "); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected bad request for blank screenID, got %v", err)
	}

	page, err := svc.GetScreen(ctx, " home ")
	if err != nil || page.PageID != "home" {
		t.Fatalf("unexpected GetScreen result: %+v, err=%v", page, err)
	}

	ids, err := svc.ListScreens(ctx)
	if err != nil || len(ids) != 1 || ids[0] != "home" {
		t.Fatalf("unexpected ListScreens result: %v, err=%v", ids, err)
	}

	repo.err = errors.New("boom")
	if _, err := svc.GetScreen(ctx, "home"); err == nil {
		t.Fatal("expected propagated repo error")
	}
}

func TestActionService(t *testing.T) {
	ctx := context.Background()

	svcNil := usecase.NewActionService(nil)
	if _, err := svcNil.Execute(ctx, domain.ActionExecutionInput{
		Trigger: actions.NewBackPressAction("b1", actions.BackPressPayload{}),
	}); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected internal error for nil executor, got %v", err)
	}

	exec := &stubActionExecutor{
		res: domain.ActionExecutionResult{ActionID: "a1", Status: "executed"},
	}
	svc := usecase.NewActionService(exec)

	if _, err := svc.Execute(ctx, domain.ActionExecutionInput{}); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected bad request for empty trigger.id, got %v", err)
	}
	if _, err := svc.Execute(ctx, domain.ActionExecutionInput{
		Trigger: actions.ActionTrigger{ID: "a1"},
	}); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected bad request for empty trigger.type, got %v", err)
	}

	validatedTrigger := actions.NewCallAPIAction("a1", actions.CallAPIPayload{
		Endpoint: "/api/v1/actions/execute",
		Method:   "POST",
	}).WithValidations(
		actions.ValidationRule{FieldID: "name", Rule: "required", Message: "name required"},
		actions.ValidationRule{FieldID: "name", Rule: "min_length", Value: "3", Message: "too short"},
		actions.ValidationRule{FieldID: "code", Rule: "max_length", Value: "4", Message: "too long"},
		actions.ValidationRule{FieldID: "email", Rule: "regex", Value: `^.+@.+$`, Message: "invalid email"},
		actions.ValidationRule{FieldID: "bad_re", Rule: "regex", Value: `[invalid`, Message: "bad regex"},
	)

	res, err := svc.Execute(ctx, domain.ActionExecutionInput{
		Trigger: validatedTrigger,
		FormValues: map[string]string{
			"name":   "",
			"code":   "12345",
			"email":  "not-an-email",
			"bad_re": "x",
		},
	})
	if !apperror.Is(err, apperror.CodeUnprocessable) {
		t.Fatalf("expected unprocessable error on failed validations, got %v", err)
	}
	if res.Status != "validation_failed" || len(res.FieldErrors) != 4 {
		t.Fatalf("expected 4 field errors, got %+v", res)
	}
	if res.FieldErrors["name"] != "name required" {
		t.Errorf("expected first failing rule for name, got %q", res.FieldErrors["name"])
	}

	// Passing validations
	passingTrigger := actions.NewCallAPIAction("a1", actions.CallAPIPayload{
		Endpoint: "/api/v1/actions/execute",
		Method:   "POST",
	}).WithValidations(
		actions.ValidationRule{FieldID: "name", Rule: "required", Message: "name required"},
		actions.ValidationRule{FieldID: "name", Rule: "min_length", Value: "3", Message: "too short"},
		actions.ValidationRule{FieldID: "code", Rule: "max_length", Value: "4", Message: "too long"},
		actions.ValidationRule{FieldID: "email", Rule: "regex", Value: `^.+@.+$`, Message: "invalid email"},
	)
	res, err = svc.Execute(ctx, domain.ActionExecutionInput{
		Trigger: passingTrigger,
		FormValues: map[string]string{
			"name":  "Alex",
			"code":  "1234",
			"email": "alex@abeta.dev",
		},
	})
	if err != nil || res.Status != "executed" {
		t.Fatalf("expected executed result, got %+v, err=%v", res, err)
	}
}

func TestCatalogService(t *testing.T) {
	ctx := context.Background()

	svcNil := usecase.NewCatalogService(nil)
	if _, err := svcNil.GetCatalog(ctx); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected internal error for nil provider, got %v", err)
	}

	prov := &stubCatalogProvider{cat: domain.TokenCatalog{Version: "2.0.0"}}
	svc := usecase.NewCatalogService(prov)

	cat, err := svc.GetCatalog(ctx)
	if err != nil || cat.Version != "2.0.0" {
		t.Fatalf("unexpected catalog: %+v, err=%v", cat, err)
	}
}
