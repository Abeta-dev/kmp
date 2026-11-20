package infra

import (
	"context"

	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// StaticTokenCatalogProvider serves the canonical SDUI design token catalog
// derived from pkg/sdui/theming.
type StaticTokenCatalogProvider struct {
	version string
}

// NewStaticTokenCatalogProvider constructs a StaticTokenCatalogProvider with the given catalog version.
func NewStaticTokenCatalogProvider(version string) *StaticTokenCatalogProvider {
	if version == "" {
		version = "1.0.0"
	}
	return &StaticTokenCatalogProvider{version: version}
}

// GetCatalog returns all registered color, typography, spacing, and corner-radius tokens.
func (p *StaticTokenCatalogProvider) GetCatalog(_ context.Context) (domain.TokenCatalog, error) {
	return domain.TokenCatalog{
		Version:      p.version,
		Colors:       theming.AllColorTokens(),
		Typography:   theming.AllTypographyTokens(),
		Spacing:      theming.AllSpacingScales(),
		CornerRadius: theming.AllCornerRadiusScales(),
	}, nil
}
