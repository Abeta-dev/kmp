// Package usecase implements application-layer SDUI services depending only on
// pure domain ports and SDUI wire contracts.
package usecase

import (
	"context"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

// ScreenService orchestrates SDUI screen retrieval.
type ScreenService struct {
	repo domain.ScreenRepository
}

// NewScreenService constructs a ScreenService backed by repo.
func NewScreenService(repo domain.ScreenRepository) *ScreenService {
	return &ScreenService{repo: repo}
}

// GetScreen validates screenID, fetches the corresponding SDUI PageResponse,
// and ensures deterministic component IDs across all snippets and composed nodes.
func (s *ScreenService) GetScreen(ctx context.Context, screenID string) (snippets.PageResponse, error) {
	id := strings.TrimSpace(screenID)
	if id == "" {
		return snippets.PageResponse{}, apperror.BadRequest("screen_id must not be empty")
	}
	if s.repo == nil {
		return snippets.PageResponse{}, apperror.Internal("screen repository is not configured")
	}
	page, err := s.repo.GetScreen(ctx, id)
	if err != nil {
		return snippets.PageResponse{}, err
	}
	snippets.EnsureComponentIDs(&page)
	return page, nil
}

// ListScreens returns all available screen identifiers.
func (s *ScreenService) ListScreens(ctx context.Context) ([]string, error) {
	if s.repo == nil {
		return nil, apperror.Internal("screen repository is not configured")
	}
	return s.repo.ListScreenIDs(ctx)
}

// ActionService validates and delegates SDUI action execution.
type ActionService struct {
	executor domain.ActionExecutor
}

// NewActionService constructs an ActionService backed by executor.
func NewActionService(executor domain.ActionExecutor) *ActionService {
	return &ActionService{executor: executor}
}

// Execute validates the ActionTrigger, enforces declarative validation rules against
// FormValues, and dispatches execution to the domain ActionExecutor.
func (s *ActionService) Execute(ctx context.Context, input domain.ActionExecutionInput) (domain.ActionExecutionResult, error) {
	if strings.TrimSpace(input.Trigger.ID) == "" {
		return domain.ActionExecutionResult{}, apperror.BadRequest("trigger.id must not be empty")
	}
	if strings.TrimSpace(string(input.Trigger.Type)) == "" {
		return domain.ActionExecutionResult{}, apperror.BadRequest("trigger.type must not be empty")
	}
	if s.executor == nil {
		return domain.ActionExecutionResult{}, apperror.Internal("action executor is not configured")
	}

	if fieldErrors := validateRules(input.Trigger.Validations, input.FormValues); len(fieldErrors) > 0 {
		res := domain.ActionExecutionResult{
			ActionID:    input.Trigger.ID,
			ActionType:  input.Trigger.Type,
			Status:      "validation_failed",
			Message:     "one or more fields failed validation",
			FieldErrors: fieldErrors,
		}
		return res, apperror.NewWithDetails(apperror.CodeUnprocessable, "action validation failed", fieldErrors)
	}

	return s.executor.Execute(ctx, input)
}

func validateRules(rules []actions.ValidationRule, values map[string]string) map[string]string {
	if len(rules) == 0 {
		return nil
	}
	errs := make(map[string]string)
	for _, rule := range rules {
		if _, alreadyFailed := errs[rule.FieldID]; alreadyFailed {
			continue
		}
		val := strings.TrimSpace(values[rule.FieldID])
		switch rule.Rule {
		case "required":
			if val == "" {
				errs[rule.FieldID] = rule.Message
			}
		case "min_length":
			minLen, err := strconv.Atoi(rule.Value)
			if err == nil && utf8.RuneCountInString(val) < minLen {
				errs[rule.FieldID] = rule.Message
			}
		case "max_length":
			maxLen, err := strconv.Atoi(rule.Value)
			if err == nil && utf8.RuneCountInString(val) > maxLen {
				errs[rule.FieldID] = rule.Message
			}
		case "regex":
			if rule.Value != "" {
				re, err := regexp.Compile(rule.Value)
				if err != nil || !re.MatchString(val) {
					errs[rule.FieldID] = rule.Message
				}
			}
		}
	}
	return errs
}

// CatalogService provides read access to the active SDUI design token catalog.
type CatalogService struct {
	provider domain.TokenCatalogProvider
}

// NewCatalogService constructs a CatalogService backed by provider.
func NewCatalogService(provider domain.TokenCatalogProvider) *CatalogService {
	return &CatalogService{provider: provider}
}

// GetCatalog returns the active design token catalog.
func (s *CatalogService) GetCatalog(ctx context.Context) (domain.TokenCatalog, error) {
	if s.provider == nil {
		return domain.TokenCatalog{}, apperror.Internal("token catalog provider is not configured")
	}
	return s.provider.GetCatalog(ctx)
}
