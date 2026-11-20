package theming_test

import (
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestColorTokens(t *testing.T) {
	colors := theming.AllColorTokens()
	if len(colors) == 0 {
		t.Fatal("expected non-empty color tokens")
	}

	for _, c := range colors {
		if !theming.IsValidColorToken(c.Token) {
			t.Errorf("expected token %q to be valid", c.Token)
		}
		spec, ok := theming.LookupColor(c.Token)
		if !ok {
			t.Errorf("expected LookupColor(%q) to succeed", c.Token)
		}
		if spec.LightHex == "" || spec.DarkHex == "" || spec.Role == "" {
			t.Errorf("incomplete color spec for %q: %+v", c.Token, spec)
		}
	}

	if theming.IsValidColorToken("nonexistent.token") {
		t.Error("expected nonexistent color token to be invalid")
	}
	if _, ok := theming.LookupColor("nonexistent.token"); ok {
		t.Error("expected LookupColor to fail for nonexistent token")
	}
}

func TestTypographyTokens(t *testing.T) {
	typos := theming.AllTypographyTokens()
	if len(typos) == 0 {
		t.Fatal("expected non-empty typography tokens")
	}

	for _, tp := range typos {
		if !theming.IsValidTypographyToken(tp.Token) {
			t.Errorf("expected token %q to be valid", tp.Token)
		}
		spec, ok := theming.LookupTypography(tp.Token)
		if !ok {
			t.Errorf("expected LookupTypography(%q) to succeed", tp.Token)
		}
		if spec.FontSizeSp <= 0 || spec.LineHeightSp <= 0 || spec.FontWeight == "" {
			t.Errorf("invalid typography spec for %q: %+v", tp.Token, spec)
		}
	}

	if theming.IsValidTypographyToken("unknown.typo") {
		t.Error("expected unknown typography token to be invalid")
	}
	if _, ok := theming.LookupTypography("unknown.typo"); ok {
		t.Error("expected LookupTypography to fail for unknown token")
	}
}

func TestSpacingAndCornerRadiusScales(t *testing.T) {
	spacings := theming.AllSpacingScales()
	if len(spacings) != 9 {
		t.Fatalf("expected 9 spacing scales, got %d", len(spacings))
	}
	if theming.SpacingLG.Dp() != 16 {
		t.Errorf("expected SpacingLG=16, got %d", theming.SpacingLG.Dp())
	}
	if theming.SpacingScale("unknown").Dp() != 0 {
		t.Error("expected unknown spacing scale Dp() to return 0")
	}

	radii := theming.AllCornerRadiusScales()
	if len(radii) != 7 {
		t.Fatalf("expected 7 corner radius scales, got %d", len(radii))
	}
	if theming.RadiusMD.Dp() != 12 {
		t.Errorf("expected RadiusMD=12, got %d", theming.RadiusMD.Dp())
	}
	if theming.CornerRadiusScale("unknown").Dp() != 0 {
		t.Error("expected unknown corner radius scale Dp() to return 0")
	}
}
