// Package molecules provides server-side Abeta Fresh quick-commerce UI molecules
// molded purely from SDUI atoms and composed_block trees (zero custom client snippets).
package molecules

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// ProductTileSpec configures an express quick-commerce product card molded as a ComposedNode.
type ProductTileSpec struct {
	ProductID         string
	Title             string
	Subtitle          string
	ImageURL          string
	PriceText         string
	OriginalPriceText string
	DiscountRibbon    string
	TopEndBadge       string
	EtaLabel          string
	UnitSuffix        string
	Rating            float64
	ReviewCount       *int
	StepperFieldID    string
	Quantity          int
	MaxQuantity       int
	OnIncrement       *actions.ActionTrigger
	OnDecrement       *actions.ActionTrigger
	VariantLabel      string
	OnVariantClick    *actions.ActionTrigger
	OnFavorite        *actions.ActionTrigger
	OnClick           *actions.ActionTrigger
}

// CategoryTileSpec configures a single tile inside CategoryGridSection.
type CategoryTileSpec struct {
	CategoryID string
	Label      string
	Subtitle   string
	ImageURL   string
	BadgeLabel string
	OnClick    *actions.ActionTrigger
}

func tileCardLayout() atoms.LayoutConfig {
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

func sectionBlockLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

// ExpressProductTile molds an Abeta Fresh quick-commerce product tile purely
// from atoms and ComposedNode primitives (BoxNode image with corner-aligned
// discount ribbon, veg/favorite badge, 8 MINS ETA pill, title, pack size,
// optional variant picker chip, PriceNode, StepperNode, node_id, and tracking).
func ExpressProductTile(spec ProductTileSpec) snippets.ComposedNode {
	prefix := "tile_" + spec.ProductID
	fieldID := spec.StepperFieldID
	if fieldID == "" {
		fieldID = "cart_qty_" + spec.ProductID
	}
	maxQty := spec.MaxQuantity
	if maxQty <= 0 {
		maxQty = 10
	}

	mediaChildren := []snippets.ComposedNode{
		snippets.ImageNode(atoms.ImageAtom{
			ID:                 prefix + "_img",
			URL:                spec.ImageURL,
			ContentDescription: spec.Title,
			HeightDp:           atoms.IntPtr(112),
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusSM.Dp()),
		}).WithNodeID(prefix + "_img_node"),
	}
	if spec.DiscountRibbon != "" {
		mediaChildren = append(mediaChildren,
			snippets.TagNode(atoms.NewTag(spec.DiscountRibbon, theming.ColorErrorContainer)).
				WithNodeID(prefix+"_discount_ribbon").
				WithBoxAlignment(snippets.BoxAlignTopStart),
		)
	}
	if spec.OnFavorite != nil {
		favIcon := atoms.NewIcon("favorite_border", 18, theming.ColorError)
		favIcon.ContentDescription = "Toggle favorite " + spec.Title
		favIcon.OnClick = spec.OnFavorite
		mediaChildren = append(mediaChildren,
			snippets.IconNode(favIcon).
				WithNodeID(prefix+"_favorite_btn").
				WithBoxAlignment(snippets.BoxAlignTopEnd),
		)
	} else if spec.TopEndBadge != "" {
		mediaChildren = append(mediaChildren,
			snippets.TagNode(atoms.NewTag(spec.TopEndBadge, theming.ColorSuccessContainer)).
				WithNodeID(prefix+"_top_end_badge").
				WithBoxAlignment(snippets.BoxAlignTopEnd),
		)
	}
	if spec.EtaLabel != "" {
		mediaChildren = append(mediaChildren,
			snippets.TagNode(atoms.NewTag(spec.EtaLabel, theming.ColorSuccessContainer)).
				WithNodeID(prefix+"_eta_pill").
				WithBoxAlignment(snippets.BoxAlignBottomStart),
		)
	}

	colChildren := []snippets.ComposedNode{
		snippets.BoxNode(mediaChildren...).WithNodeID(prefix + "_media_box"),
		snippets.TextNode(atoms.NewText(spec.Title, theming.TypographyTitleSmall, theming.ColorTextPrimary)).
			WithNodeID(prefix + "_title"),
	}
	if spec.Subtitle != "" {
		colChildren = append(colChildren,
			snippets.TextNode(atoms.NewText(spec.Subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithNodeID(prefix+"_subtitle"),
		)
	}
	if spec.OnVariantClick != nil {
		variantLabel := spec.VariantLabel
		if variantLabel == "" {
			variantLabel = "Select pack"
		}
		colChildren = append(colChildren,
			snippets.ChipNode(atoms.NewChip(prefix+"_variant", variantLabel, false, spec.OnVariantClick)).
				WithNodeID(prefix+"_variant_chip"),
		)
	}
	if spec.Rating > 0 {
		rt := atoms.NewRating(prefix+"_rating_atom", spec.Rating, 5)
		rt.ReviewCount = spec.ReviewCount
		rt.StarSizeDp = 14
		colChildren = append(colChildren, snippets.RatingNode(rt).WithNodeID(prefix+"_rating"))
	}

	price := atoms.NewPrice(prefix+"_price_atom", spec.PriceText, spec.OriginalPriceText)
	price.DiscountLabel = spec.DiscountRibbon
	price.UnitSuffix = spec.UnitSuffix

	stepper := atoms.NewStepper(fieldID, "ADD", spec.Quantity, spec.OnIncrement, spec.OnDecrement)
	stepper.MinQuantity = 0
	stepper.MaxQuantity = maxQty

	colChildren = append(colChildren,
		snippets.HStack(
			theming.SpacingXS.Dp(),
			snippets.PriceNode(price).WithNodeID(prefix+"_price"),
			snippets.StepperNode(stepper).WithNodeID(prefix+"_stepper"),
		).
			WithNodeID(prefix+"_footer_row").
			WithArrangement(snippets.ComposedAlignSpaceBetween).
			WithAlignment("", snippets.ComposedAlignCenter).
			WithLayout(atoms.LayoutConfig{FillMaxWidth: atoms.BoolPtr(true)}),
	)

	card := snippets.CardNode(
		tileCardLayout(),
		snippets.VStack(theming.SpacingXS.Dp(), colChildren...).
			WithNodeID(prefix+"_col").
			WithLayout(atoms.LayoutConfig{FillMaxWidth: atoms.BoolPtr(true)}),
	).
		WithNodeID(prefix).
		WithTracking("node_impression", map[string]string{
			"component_id": prefix,
			"product_id":   spec.ProductID,
			"molecule":     "express_product_tile",
		}).
		WithClickTracking("interaction_click", map[string]string{
			"component_id": prefix,
			"product_id":   spec.ProductID,
			"molecule":     "express_product_tile",
		})
	if spec.OnClick != nil {
		card = card.WithOnClick(*spec.OnClick)
	}
	return card
}

// CategoryGridSection molds a responsive quick-commerce category grid
// (3 compact / 4 medium / 6 expanded) inside a composed_block using GridNode and BoxNode tiles.
func CategoryGridSection(snippetID, blockID, title string, items []CategoryTileSpec) snippets.SnippetEnvelope {
	tiles := make([]snippets.ComposedNode, 0, len(items))
	for _, it := range items {
		prefix := blockID + "_cat_" + it.CategoryID
		mediaChildren := []snippets.ComposedNode{
			snippets.ImageNode(atoms.ImageAtom{
				ID:                 prefix + "_img",
				URL:                it.ImageURL,
				ContentDescription: it.Label,
				HeightDp:           atoms.IntPtr(64),
				ScaleMode:          "crop",
				CornerRadius:       atoms.UniformRadius(theming.RadiusSM.Dp()),
			}).WithNodeID(prefix + "_img_node"),
		}
		if it.BadgeLabel != "" {
			mediaChildren = append(mediaChildren,
				snippets.TagNode(atoms.NewTag(it.BadgeLabel, theming.ColorBrandContainer)).
					WithNodeID(prefix+"_badge").
					WithBoxAlignment(snippets.BoxAlignTopEnd),
			)
		}

		tileChildren := []snippets.ComposedNode{
			snippets.BoxNode(mediaChildren...).WithNodeID(prefix + "_media_box"),
			snippets.TextNode(atoms.NewText(it.Label, theming.TypographyLabelMedium, theming.ColorTextPrimary)).
				WithNodeID(prefix + "_label"),
		}
		if it.Subtitle != "" {
			tileChildren = append(tileChildren,
				snippets.TextNode(atoms.NewText(it.Subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
					WithNodeID(prefix+"_subtitle"),
			)
		}

		tile := snippets.CardNode(
			tileCardLayout(),
			snippets.VStack(theming.SpacingXS.Dp(), tileChildren...).
				WithNodeID(prefix+"_col").
				WithAlignment(snippets.ComposedAlignCenter, ""),
		).
			WithNodeID(prefix).
			WithTracking("node_impression", map[string]string{
				"component_id": prefix,
				"category_id":  it.CategoryID,
				"molecule":     "category_grid_tile",
			}).
			WithClickTracking("interaction_click", map[string]string{
				"component_id": prefix,
				"category_id":  it.CategoryID,
				"molecule":     "category_grid_tile",
			})
		if it.OnClick != nil {
			tile = tile.WithOnClick(*it.OnClick)
		}
		tiles = append(tiles, tile)
	}

	grid := snippets.GridNode(3, theming.SpacingSM.Dp(), tiles...).
		WithNodeID(blockID + "_grid").
		WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
			Compact:  atoms.IntPtr(3),
			Medium:   atoms.IntPtr(4),
			Expanded: atoms.IntPtr(6),
		})

	rootChildren := make([]snippets.ComposedNode, 0, 2)
	if title != "" {
		rootChildren = append(rootChildren,
			snippets.TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithNodeID(blockID+"_title"),
		)
	}
	rootChildren = append(rootChildren, grid)

	root := snippets.VStack(theming.SpacingSM.Dp(), rootChildren...).
		WithNodeID(blockID+"_root").
		WithLayout(sectionBlockLayout()).
		WithTracking("node_impression", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "category_grid_section",
		})

	return snippets.NewComposedBlockSnippet(snippetID, blockID, root).
		WithRecipeName("express_category_grid").
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": snippetID,
			"molecule":   "category_grid_section",
		}).
		Build()
}

// ImpulseUpsellRail molds a horizontal "Before You Checkout" ScrollRowNode upsell rail
// composed of ExpressProductTile cards inside a composed_block.
func ImpulseUpsellRail(snippetID, blockID, title string, items []ProductTileSpec) snippets.SnippetEnvelope {
	heading := title
	if heading == "" {
		heading = "Before You Checkout"
	}

	cards := make([]snippets.ComposedNode, 0, len(items))
	for _, it := range items {
		cards = append(cards, ExpressProductTile(it))
	}

	header := snippets.HStack(
		theming.SpacingSM.Dp(),
		snippets.TextNode(atoms.NewText(heading, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
			WithNodeID(blockID+"_title"),
		snippets.TagNode(atoms.NewTag("QUICK ADD", theming.ColorWarningContainer)).
			WithNodeID(blockID+"_badge"),
	).
		WithNodeID(blockID+"_header").
		WithArrangement(snippets.ComposedAlignSpaceBetween).
		WithAlignment("", snippets.ComposedAlignCenter).
		WithLayout(atoms.LayoutConfig{FillMaxWidth: atoms.BoolPtr(true)})

	rail := snippets.ScrollRowNode(theming.SpacingMD.Dp(), cards...).
		WithNodeID(blockID + "_scroll_row")

	root := snippets.VStack(theming.SpacingSM.Dp(), header, rail).
		WithNodeID(blockID+"_root").
		WithLayout(sectionBlockLayout()).
		WithTracking("node_impression", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "impulse_upsell_rail",
		})

	return snippets.NewComposedBlockSnippet(snippetID, blockID, root).
		WithRecipeName("express_impulse_upsell_rail").
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": snippetID,
			"molecule":   "impulse_upsell_rail",
		}).
		Build()
}
