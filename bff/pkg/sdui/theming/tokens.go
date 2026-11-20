// Package theming defines semantic design tokens for the SDUI wire protocol,
// aligning 1:1 with the KMP design-tokens registry.
package theming

// ColorToken identifies a semantic color token in the design system.
type ColorToken string

const (
	ColorBgPrimary        ColorToken = "bg.primary"
	ColorBgSecondary      ColorToken = "bg.secondary"
	ColorBgTertiary       ColorToken = "bg.tertiary"
	ColorSurfacePrimary   ColorToken = "surface.primary"
	ColorSurfaceSecondary ColorToken = "surface.secondary"
	ColorSurfaceElevated  ColorToken = "surface.elevated"
	ColorSurfaceInverse   ColorToken = "surface.inverse"

	ColorTextPrimary   ColorToken = "text.primary"
	ColorTextSecondary ColorToken = "text.secondary"
	ColorTextTertiary  ColorToken = "text.tertiary"
	ColorTextDisabled  ColorToken = "text.disabled"
	ColorTextInverse   ColorToken = "text.inverse"
	ColorTextBrand     ColorToken = "text.brand"

	ColorBrandPrimary   ColorToken = "brand.primary"
	ColorBrandSecondary ColorToken = "brand.secondary"
	ColorBrandContainer ColorToken = "brand.container"
	ColorAccentPrimary  ColorToken = "accent.primary"

	ColorSuccess          ColorToken = "status.success"
	ColorSuccessContainer ColorToken = "status.success_container"
	ColorWarning          ColorToken = "status.warning"
	ColorWarningContainer ColorToken = "status.warning_container"
	ColorError            ColorToken = "status.error"
	ColorErrorContainer   ColorToken = "status.error_container"
	ColorInfo             ColorToken = "status.info"
	ColorInfoContainer    ColorToken = "status.info_container"

	ColorBorderDefault ColorToken = "border.default"
	ColorBorderSubtle  ColorToken = "border.subtle"
	ColorBorderStrong  ColorToken = "border.strong"
	ColorBorderFocus   ColorToken = "border.focus"
	ColorDivider       ColorToken = "divider.default"
)

// ColorSpec describes light and dark hex defaults for a semantic ColorToken.
type ColorSpec struct {
	Token    ColorToken `json:"token"`
	LightHex string     `json:"light_hex"`
	DarkHex  string     `json:"dark_hex"`
	Role     string     `json:"role"`
}

var colorSpecs = []ColorSpec{
	{Token: ColorBgPrimary, LightHex: "#FFFFFF", DarkHex: "#0F1115", Role: "background"},
	{Token: ColorBgSecondary, LightHex: "#F6F7F9", DarkHex: "#161920", Role: "background"},
	{Token: ColorBgTertiary, LightHex: "#ECEEF2", DarkHex: "#1E222B", Role: "background"},
	{Token: ColorSurfacePrimary, LightHex: "#FFFFFF", DarkHex: "#161920", Role: "surface"},
	{Token: ColorSurfaceSecondary, LightHex: "#F4F6F8", DarkHex: "#1E222B", Role: "surface"},
	{Token: ColorSurfaceElevated, LightHex: "#FFFFFF", DarkHex: "#242936", Role: "surface"},
	{Token: ColorSurfaceInverse, LightHex: "#111318", DarkHex: "#F6F7F9", Role: "surface"},
	{Token: ColorTextPrimary, LightHex: "#111318", DarkHex: "#F4F6F8", Role: "text"},
	{Token: ColorTextSecondary, LightHex: "#4B5563", DarkHex: "#9CA3AF", Role: "text"},
	{Token: ColorTextTertiary, LightHex: "#6B7280", DarkHex: "#6B7280", Role: "text"},
	{Token: ColorTextDisabled, LightHex: "#9CA3AF", DarkHex: "#4B5563", Role: "text"},
	{Token: ColorTextInverse, LightHex: "#FFFFFF", DarkHex: "#111318", Role: "text"},
	{Token: ColorTextBrand, LightHex: "#2563EB", DarkHex: "#60A5FA", Role: "text"},
	{Token: ColorBrandPrimary, LightHex: "#2563EB", DarkHex: "#3B82F6", Role: "brand"},
	{Token: ColorBrandSecondary, LightHex: "#1D4ED8", DarkHex: "#60A5FA", Role: "brand"},
	{Token: ColorBrandContainer, LightHex: "#DBEAFE", DarkHex: "#1E3A8A", Role: "brand"},
	{Token: ColorAccentPrimary, LightHex: "#7C3AED", DarkHex: "#A78BFA", Role: "accent"},
	{Token: ColorSuccess, LightHex: "#16A34A", DarkHex: "#4ADE80", Role: "status"},
	{Token: ColorSuccessContainer, LightHex: "#DCFCE7", DarkHex: "#14532D", Role: "status"},
	{Token: ColorWarning, LightHex: "#D97706", DarkHex: "#FBBF24", Role: "status"},
	{Token: ColorWarningContainer, LightHex: "#FEF3C7", DarkHex: "#78350F", Role: "status"},
	{Token: ColorError, LightHex: "#DC2626", DarkHex: "#F87171", Role: "status"},
	{Token: ColorErrorContainer, LightHex: "#FEE2E2", DarkHex: "#7F1D1D", Role: "status"},
	{Token: ColorInfo, LightHex: "#0284C7", DarkHex: "#38BDF8", Role: "status"},
	{Token: ColorInfoContainer, LightHex: "#E0F2FE", DarkHex: "#0C4A6E", Role: "status"},
	{Token: ColorBorderDefault, LightHex: "#D1D5DB", DarkHex: "#374151", Role: "border"},
	{Token: ColorBorderSubtle, LightHex: "#E5E7EB", DarkHex: "#262B36", Role: "border"},
	{Token: ColorBorderStrong, LightHex: "#9CA3AF", DarkHex: "#4B5563", Role: "border"},
	{Token: ColorBorderFocus, LightHex: "#2563EB", DarkHex: "#60A5FA", Role: "border"},
	{Token: ColorDivider, LightHex: "#E5E7EB", DarkHex: "#262B36", Role: "divider"},
}

var colorLookup = func() map[ColorToken]ColorSpec {
	m := make(map[ColorToken]ColorSpec, len(colorSpecs))
	for _, spec := range colorSpecs {
		m[spec.Token] = spec
	}
	return m
}()

// AllColorTokens returns all registered ColorSpec definitions.
func AllColorTokens() []ColorSpec {
	out := make([]ColorSpec, len(colorSpecs))
	copy(out, colorSpecs)
	return out
}

// IsValidColorToken reports whether token is a known semantic ColorToken.
func IsValidColorToken(token ColorToken) bool {
	_, ok := colorLookup[token]
	return ok
}

// LookupColor returns the ColorSpec for token if registered.
func LookupColor(token ColorToken) (ColorSpec, bool) {
	spec, ok := colorLookup[token]
	return spec, ok
}

// TypographyToken identifies a semantic typography style in the design system.
type TypographyToken string

const (
	TypographyDisplayLarge   TypographyToken = "display.large"
	TypographyDisplayMedium  TypographyToken = "display.medium"
	TypographyDisplaySmall   TypographyToken = "display.small"
	TypographyHeadlineLarge  TypographyToken = "headline.large"
	TypographyHeadlineMedium TypographyToken = "headline.medium"
	TypographyHeadlineSmall  TypographyToken = "headline.small"
	TypographyTitleLarge     TypographyToken = "title.large"
	TypographyTitleMedium    TypographyToken = "title.medium"
	TypographyTitleSmall     TypographyToken = "title.small"
	TypographyBodyLarge      TypographyToken = "body.large"
	TypographyBodyMedium     TypographyToken = "body.medium"
	TypographyBodySmall      TypographyToken = "body.small"
	TypographyLabelLarge     TypographyToken = "label.large"
	TypographyLabelMedium    TypographyToken = "label.medium"
	TypographyLabelSmall     TypographyToken = "label.small"
	TypographyCaption        TypographyToken = "caption"
	TypographyOverline       TypographyToken = "overline"
)

// TypographySpec describes font sizing and weight metrics for a TypographyToken.
type TypographySpec struct {
	Token        TypographyToken `json:"token"`
	FontSizeSp   int             `json:"font_size_sp"`
	LineHeightSp int             `json:"line_height_sp"`
	FontWeight   string          `json:"font_weight"`
}

var typographySpecs = []TypographySpec{
	{Token: TypographyDisplayLarge, FontSizeSp: 36, LineHeightSp: 44, FontWeight: "bold"},
	{Token: TypographyDisplayMedium, FontSizeSp: 32, LineHeightSp: 40, FontWeight: "bold"},
	{Token: TypographyDisplaySmall, FontSizeSp: 28, LineHeightSp: 36, FontWeight: "semi_bold"},
	{Token: TypographyHeadlineLarge, FontSizeSp: 24, LineHeightSp: 32, FontWeight: "bold"},
	{Token: TypographyHeadlineMedium, FontSizeSp: 22, LineHeightSp: 28, FontWeight: "semi_bold"},
	{Token: TypographyHeadlineSmall, FontSizeSp: 20, LineHeightSp: 26, FontWeight: "semi_bold"},
	{Token: TypographyTitleLarge, FontSizeSp: 18, LineHeightSp: 24, FontWeight: "semi_bold"},
	{Token: TypographyTitleMedium, FontSizeSp: 16, LineHeightSp: 22, FontWeight: "medium"},
	{Token: TypographyTitleSmall, FontSizeSp: 14, LineHeightSp: 20, FontWeight: "medium"},
	{Token: TypographyBodyLarge, FontSizeSp: 16, LineHeightSp: 24, FontWeight: "regular"},
	{Token: TypographyBodyMedium, FontSizeSp: 14, LineHeightSp: 20, FontWeight: "regular"},
	{Token: TypographyBodySmall, FontSizeSp: 12, LineHeightSp: 16, FontWeight: "regular"},
	{Token: TypographyLabelLarge, FontSizeSp: 14, LineHeightSp: 20, FontWeight: "semi_bold"},
	{Token: TypographyLabelMedium, FontSizeSp: 12, LineHeightSp: 16, FontWeight: "medium"},
	{Token: TypographyLabelSmall, FontSizeSp: 11, LineHeightSp: 14, FontWeight: "medium"},
	{Token: TypographyCaption, FontSizeSp: 12, LineHeightSp: 16, FontWeight: "regular"},
	{Token: TypographyOverline, FontSizeSp: 10, LineHeightSp: 14, FontWeight: "semi_bold"},
}

var typographyLookup = func() map[TypographyToken]TypographySpec {
	m := make(map[TypographyToken]TypographySpec, len(typographySpecs))
	for _, spec := range typographySpecs {
		m[spec.Token] = spec
	}
	return m
}()

// AllTypographyTokens returns all registered TypographySpec definitions.
func AllTypographyTokens() []TypographySpec {
	out := make([]TypographySpec, len(typographySpecs))
	copy(out, typographySpecs)
	return out
}

// IsValidTypographyToken reports whether token is a known semantic TypographyToken.
func IsValidTypographyToken(token TypographyToken) bool {
	_, ok := typographyLookup[token]
	return ok
}

// LookupTypography returns the TypographySpec for token if registered.
func LookupTypography(token TypographyToken) (TypographySpec, bool) {
	spec, ok := typographyLookup[token]
	return spec, ok
}

// SpacingScale represents named spacing steps in density-independent pixels (dp).
type SpacingScale string

const (
	SpacingNone SpacingScale = "spacing.none"
	SpacingXXS  SpacingScale = "spacing.xxs"
	SpacingXS   SpacingScale = "spacing.xs"
	SpacingSM   SpacingScale = "spacing.sm"
	SpacingMD   SpacingScale = "spacing.md"
	SpacingLG   SpacingScale = "spacing.lg"
	SpacingXL   SpacingScale = "spacing.xl"
	SpacingXXL  SpacingScale = "spacing.xxl"
	SpacingXXXL SpacingScale = "spacing.xxxl"
)

// SpacingSpec maps a SpacingScale token to its dp value.
type SpacingSpec struct {
	Token SpacingScale `json:"token"`
	Dp    int          `json:"dp"`
}

var spacingSpecs = []SpacingSpec{
	{Token: SpacingNone, Dp: 0},
	{Token: SpacingXXS, Dp: 2},
	{Token: SpacingXS, Dp: 4},
	{Token: SpacingSM, Dp: 8},
	{Token: SpacingMD, Dp: 12},
	{Token: SpacingLG, Dp: 16},
	{Token: SpacingXL, Dp: 24},
	{Token: SpacingXXL, Dp: 32},
	{Token: SpacingXXXL, Dp: 48},
}

var spacingLookup = func() map[SpacingScale]int {
	m := make(map[SpacingScale]int, len(spacingSpecs))
	for _, spec := range spacingSpecs {
		m[spec.Token] = spec.Dp
	}
	return m
}()

// AllSpacingScales returns all registered SpacingSpec definitions.
func AllSpacingScales() []SpacingSpec {
	out := make([]SpacingSpec, len(spacingSpecs))
	copy(out, spacingSpecs)
	return out
}

// Dp returns the integer dp value for a SpacingScale token, defaulting to 0.
func (s SpacingScale) Dp() int {
	return spacingLookup[s]
}

// CornerRadiusScale represents named corner radius steps in dp.
type CornerRadiusScale string

const (
	RadiusNone CornerRadiusScale = "radius.none"
	RadiusXS   CornerRadiusScale = "radius.xs"
	RadiusSM   CornerRadiusScale = "radius.sm"
	RadiusMD   CornerRadiusScale = "radius.md"
	RadiusLG   CornerRadiusScale = "radius.lg"
	RadiusXL   CornerRadiusScale = "radius.xl"
	RadiusFull CornerRadiusScale = "radius.full"
)

// CornerRadiusSpec maps a CornerRadiusScale token to its dp value.
type CornerRadiusSpec struct {
	Token CornerRadiusScale `json:"token"`
	Dp    int               `json:"dp"`
}

var cornerRadiusSpecs = []CornerRadiusSpec{
	{Token: RadiusNone, Dp: 0},
	{Token: RadiusXS, Dp: 4},
	{Token: RadiusSM, Dp: 8},
	{Token: RadiusMD, Dp: 12},
	{Token: RadiusLG, Dp: 16},
	{Token: RadiusXL, Dp: 24},
	{Token: RadiusFull, Dp: 9999},
}

var cornerRadiusLookup = func() map[CornerRadiusScale]int {
	m := make(map[CornerRadiusScale]int, len(cornerRadiusSpecs))
	for _, spec := range cornerRadiusSpecs {
		m[spec.Token] = spec.Dp
	}
	return m
}()

// AllCornerRadiusScales returns all registered CornerRadiusSpec definitions.
func AllCornerRadiusScales() []CornerRadiusSpec {
	out := make([]CornerRadiusSpec, len(cornerRadiusSpecs))
	copy(out, cornerRadiusSpecs)
	return out
}

// Dp returns the integer dp value for a CornerRadiusScale token, defaulting to 0.
func (r CornerRadiusScale) Dp() int {
	return cornerRadiusLookup[r]
}
