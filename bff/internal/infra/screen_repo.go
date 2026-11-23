// Package infra provides concrete repository, action executor, and token catalog
// implementations fulfilling the internal/domain interfaces.
package infra

import (
	"context"
	"sort"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// InMemoryScreenRepository serves built-in rich SDUI sample screens.
type InMemoryScreenRepository struct {
	screens map[string]snippets.PageResponse
}

// NewInMemoryScreenRepository initializes the repository with built-in sample screens
// ("home", "catalog", "containers", "profile", "form-demo", "store-home", "watch-status")
// and Abeta Pay fintech vertical screens ("pay-home", "pay-transfer", "pay-cards", "pay-receipt").
func NewInMemoryScreenRepository() *InMemoryScreenRepository {
	return &InMemoryScreenRepository{
		screens: map[string]snippets.PageResponse{
			"home":         buildHomeScreen(),
			"catalog":      buildCatalogScreen(),
			"containers":   buildContainersScreen(),
			"profile":      buildProfileScreen(),
			"form-demo":    buildFormDemoScreen(),
			"store-home":   buildStoreHomeScreen(),
			"watch-status": buildWatchStatusScreen(),
			"pay-home":     buildPayHomeScreen(),
			"pay-transfer": buildPayTransferScreen(),
			"pay-cards":    buildPayCardsScreen(),
			"pay-receipt":  buildPayReceiptScreen(),
		},
	}
}

// GetScreen returns the PageResponse for screenID or apperror.NotFound if unknown.
func (r *InMemoryScreenRepository) GetScreen(_ context.Context, screenID string) (snippets.PageResponse, error) {
	page, ok := r.screens[screenID]
	if !ok {
		return snippets.PageResponse{}, apperror.NotFound("screen not found: " + screenID)
	}
	return page, nil
}

// ListScreenIDs returns a sorted slice of available screen identifiers.
func (r *InMemoryScreenRepository) ListScreenIDs(_ context.Context) ([]string, error) {
	ids := make([]string, 0, len(r.screens))
	for id := range r.screens {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids, nil
}

func cardLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingLG.Dp()),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderSubtle),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

func compactCardLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingMD.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderSubtle),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

func backNavigationIcon(actionID string) atoms.IconAtom {
	backAct := actions.NewBackPressAction(actionID, actions.BackPressPayload{Steps: 1})
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = &backAct
	return backIcon
}

func ptrAction(a actions.ActionTrigger) *actions.ActionTrigger {
	return &a
}

func ptrTag(t atoms.TagAtom) *atoms.TagAtom {
	return &t
}
