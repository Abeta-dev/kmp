// Package usecase implements application-layer SDUI services depending only on
// pure domain ports and SDUI wire contracts.
package usecase

import (
	"context"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

// DefaultAppVersion is the fallback app_version stamped on screens and manifests.
const DefaultAppVersion = "0.8.0"

// ScreenService orchestrates SDUI screen retrieval and manifest generation.
type ScreenService struct {
	repo       domain.ScreenRepository
	appVersion string
}

// NewScreenService constructs a ScreenService backed by repo with DefaultAppVersion.
func NewScreenService(repo domain.ScreenRepository) *ScreenService {
	return NewScreenServiceWithVersion(repo, DefaultAppVersion)
}

// NewScreenServiceWithVersion constructs a ScreenService backed by repo and appVersion.
func NewScreenServiceWithVersion(repo domain.ScreenRepository, appVersion string) *ScreenService {
	ver := strings.TrimSpace(appVersion)
	if ver == "" {
		ver = DefaultAppVersion
	}
	return &ScreenService{
		repo:       repo,
		appVersion: ver,
	}
}

// AppVersion returns the configured app_version for this service.
func (s *ScreenService) AppVersion() string {
	if s == nil || strings.TrimSpace(s.appVersion) == "" {
		return DefaultAppVersion
	}
	return s.appVersion
}

// GetScreen validates screenID, fetches the corresponding SDUI PageResponse,
// ensures deterministic component IDs, and stamps app_version, page_version, and etag if empty.
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
	if err := stampPageVersionMeta(&page, s.AppVersion()); err != nil {
		return snippets.PageResponse{}, apperror.Internal("failed to compute page version metadata")
	}
	return page, nil
}

// ListScreens returns all available screen identifiers.
func (s *ScreenService) ListScreens(ctx context.Context) ([]string, error) {
	if s.repo == nil {
		return nil, apperror.Internal("screen repository is not configured")
	}
	return s.repo.ListScreenIDs(ctx)
}

// BuildManifest computes deterministic version and quoted SHA-256 ETag metadata
// for every registered screen sorted by screen ID.
func (s *ScreenService) BuildManifest(ctx context.Context, appVersion string) (snippets.ScreenManifestResponse, error) {
	if s.repo == nil {
		return snippets.ScreenManifestResponse{}, apperror.Internal("screen repository is not configured")
	}
	ver := strings.TrimSpace(appVersion)
	if ver == "" {
		ver = s.AppVersion()
	}

	rawIDs, err := s.repo.ListScreenIDs(ctx)
	if err != nil {
		return snippets.ScreenManifestResponse{}, err
	}
	ids := append([]string(nil), rawIDs...)
	sort.Strings(ids)

	screens := make(map[string]snippets.ScreenVersionMeta, len(ids))
	for _, id := range ids {
		page, err := s.repo.GetScreen(ctx, id)
		if err != nil {
			return snippets.ScreenManifestResponse{}, err
		}
		snippets.EnsureComponentIDs(&page)
		if err := stampPageVersionMeta(&page, ver); err != nil {
			return snippets.ScreenManifestResponse{}, apperror.Internal("failed to compute screen manifest metadata")
		}
		screens[id] = snippets.ScreenVersionMeta{
			Version: page.PageVersion,
			ETag:    page.ETag,
		}
	}

	return snippets.ScreenManifestResponse{
		AppVersion: ver,
		Screens:    screens,
	}, nil
}

func stampPageVersionMeta(page *snippets.PageResponse, appVersion string) error {
	if page.ETag == "" {
		etag, err := snippets.ComputePageETag(*page)
		if err != nil {
			return err
		}
		page.ETag = etag
	}
	if page.PageVersion == "" {
		page.PageVersion = snippets.DerivePageVersion(page.ETag)
	}
	if page.AppVersion == "" {
		page.AppVersion = appVersion
	}
	return nil
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
