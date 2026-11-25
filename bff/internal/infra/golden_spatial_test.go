package infra_test

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func canonicalSpatialCustomAndGraphScreen(t *testing.T) snippets.PageResponse {
	t.Helper()
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }

	cardLayout := atoms.LayoutConfig{
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

	toolbarTitle := atoms.NewText("Abeta Spatial Graph & Custom Slots", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("Interactive relationship map & host-bridged custom slot", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_spatial_demo", actions.BackPressPayload{Steps: 1}))

	header := snippets.NewHeaderSnippet(
		"spatial_demo_header",
		atoms.NewText("Service Topology & Live Map", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Spatial pan/zoom graph canvas paired with a native host slot", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithLeadingIcon(atoms.NewIcon("sparkles", 20, theming.ColorBrandPrimary)).
		WithTrailingTag(atoms.NewTag("SPATIAL", theming.ColorInfoContainer)).
		WithLayout(cardLayout).
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": "spatial_demo_header",
			"surface":    "spatial-custom-and-graph-demo",
		}).
		Build()

	graphTitle := atoms.NewText("KMP Clean Architecture Topology", theming.TypographyTitleMedium, theming.ColorTextPrimary)
	graphSubtitle := atoms.NewText("Drag nodes, pan, or zoom to inspect module dependencies", theming.TypographyBodySmall, theming.ColorTextSecondary)

	graphEnv := snippets.NewGraphCanvasSnippet("spatial_topology_graph", snippets.GraphCanvasSnippetData{
		CanvasID:      "service_topology_canvas",
		Title:         &graphTitle,
		Subtitle:      &graphSubtitle,
		HeightDp:      380,
		AllowPan:      atoms.BoolPtr(true),
		AllowZoom:     atoms.BoolPtr(true),
		AllowNodeDrag: atoms.BoolPtr(true),
		MinZoom:       0.5,
		MaxZoom:       2.5,
		Nodes: []snippets.GraphNodeSpec{
			{
				NodeID:      "node_bff",
				Label:       "Go SDUI BFF",
				Subtitle:    "bff@v0.8.0",
				Badge:       "EDGE",
				X:           60,
				Y:           40,
				BgColor:     atoms.ColorRef(theming.ColorBrandContainer),
				BorderColor: atoms.ColorRef(theming.ColorBrandPrimary),
				IconName:    "cloud",
				Draggable:   atoms.BoolPtr(false),
				Pinned:      true,
				OnClick: ptrAction(actions.NewShowToastAction("click_node_bff", actions.ShowToastPayload{
					Message:    "Go SDUI BFF serves 23 snippet types",
					DurationMs: 2000,
					Variant:    "info",
				})),
				Tracking: &atoms.TrackingMeta{
					EventName: "graph_node_clicked",
					Properties: map[string]string{
						"node_id": "node_bff",
						"layer":   "bff",
					},
				},
			},
			{
				NodeID:      "node_contracts",
				Label:       "sdui-contracts",
				Subtitle:    "Layer 1 Wire DTOs",
				Badge:       "23 TYPES",
				X:           240,
				Y:           150,
				BgColor:     atoms.ColorRef(theming.ColorSurfacePrimary),
				BorderColor: atoms.ColorRef(theming.ColorBorderSubtle),
				IconName:    "code",
				Draggable:   atoms.BoolPtr(true),
				Pinned:      false,
				OnClick: ptrAction(actions.NewNavigateAction("click_node_contracts", actions.NavigatePayload{
					Route: "/screens/catalog",
				})),
				OnDragEnd: ptrAction(actions.NewSaveStateAction("drag_end_node_contracts", actions.SaveStatePayload{
					Key:   "node_contracts_pos",
					Value: "240,150",
					Scope: "session",
				})),
				Tracking: &atoms.TrackingMeta{
					EventName: "graph_node_moved",
					Properties: map[string]string{
						"node_id": "node_contracts",
						"layer":   "layer_1",
					},
				},
			},
			{
				NodeID:      "node_layout",
				Label:       "sdui-layout",
				Subtitle:    "Layer 3 Orchestrator",
				Badge:       "COMPOSE",
				X:           420,
				Y:           260,
				BgColor:     atoms.ColorRef(theming.ColorSuccessContainer),
				BorderColor: atoms.ColorRef(theming.ColorSuccess),
				IconName:    "layers",
				Draggable:   atoms.BoolPtr(true),
				Pinned:      false,
				OnClick: ptrAction(actions.NewTriggerFeedbackAction("click_node_layout", actions.TriggerFeedbackPayload{
					Effect: actions.FeedbackHapticTick,
				})),
				OnDragEnd: ptrAction(actions.NewCallAPIAction("drag_end_node_layout", actions.CallAPIPayload{
					Endpoint: "/api/v1/actions/execute",
					Method:   "POST",
					Body:     map[string]any{"node_id": "node_layout"},
				})),
			},
		},
		Edges: []snippets.GraphEdgeSpec{
			{
				EdgeID:      "edge_bff_contracts",
				FromNodeID:  "node_bff",
				ToNodeID:    "node_contracts",
				Label:       "JSON v1",
				Style:       snippets.GraphEdgeBezier,
				Color:       atoms.ColorRef(theming.ColorBrandPrimary),
				Highlighted: true,
				OnClick: ptrAction(actions.NewShowToastAction("click_edge_bff_contracts", actions.ShowToastPayload{
					Message:    "1:1 KMP <-> Go wire parity",
					DurationMs: 1800,
					Variant:    "success",
				})),
			},
			{
				EdgeID:      "edge_contracts_layout",
				FromNodeID:  "node_contracts",
				ToNodeID:    "node_layout",
				Label:       "Wire DTOs",
				Style:       snippets.GraphEdgeStraight,
				Color:       atoms.ColorRef(theming.ColorTextSecondary),
				Highlighted: false,
			},
			{
				EdgeID:      "edge_bff_layout",
				FromNodeID:  "node_bff",
				ToNodeID:    "node_layout",
				Label:       "SSE Delta",
				Style:       snippets.GraphEdgeDashed,
				Color:       atoms.ColorRef(theming.ColorInfo),
				Highlighted: true,
				OnClick: ptrAction(actions.NewTrackEventAction("click_edge_bff_layout", actions.TrackEventPayload{
					EventName:  "graph_edge_clicked",
					Properties: map[string]any{"edge_id": "edge_bff_layout"},
				})),
			},
		},
		OnNodeMoved: ptrAction(actions.NewSaveStateAction("spatial_graph_node_moved", actions.SaveStatePayload{
			Key:   "graph_last_moved_node",
			Value: "node_contracts",
			Scope: "session",
		})),
		OnCanvasTap: ptrAction(actions.NewTrackEventAction("spatial_graph_canvas_tap", actions.TrackEventPayload{
			EventName:  "graph_canvas_tapped",
			Properties: map[string]any{"canvas_id": "service_topology_canvas"},
		})),
		Layout: &cardLayout,
	})
	graphEnv.Tracking = &atoms.TrackingMeta{
		EventName: "snippet_impression",
		Properties: map[string]string{
			"snippet_id": "spatial_topology_graph",
			"surface":    "spatial-custom-and-graph-demo",
		},
	}

	fallbackCard := snippets.NewCardSnippet(
		"spatial_live_map_fallback",
		atoms.NewText("Interactive Map Unavailable", theming.TypographyTitleMedium, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Host did not register slot_type=native_map_view", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithDescription(atoms.NewText("Using graceful SDUI fallback card for delivery #AF-4092.", theming.TypographyBodyMedium, theming.ColorTextPrimary)).
		WithBadge(atoms.NewTag("FALLBACK", theming.ColorWarningContainer)).
		WithPrimaryButton(atoms.NewButton(
			"btn_open_map_directions",
			"Open Directions",
			"primary",
			ptrAction(actions.NewOpenURLAction("open_map_directions", actions.OpenURLPayload{
				URL:             "https://maps.abeta.dev/orders/AF-4092",
				ExternalBrowser: atoms.BoolPtr(true),
			})),
		)).
		WithLayout(cardLayout).
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": "spatial_live_map_fallback",
			"surface":    "spatial-custom-and-graph-demo",
		}).
		Build()

	slotEnv := snippets.NewCustomSlotSnippet("spatial_live_map_slot", snippets.CustomSlotSnippetData{
		SlotID:      "delivery_live_map",
		SlotType:    "native_map_view",
		MinHeightDp: atoms.IntPtr(240),
		AspectRatio: atoms.Float64Ptr(1.75),
		InitialState: map[string]any{
			"center_lat": 37.7749,
			"center_lng": -122.4194,
			"zoom":       14,
			"show_rider": true,
			"order_id":   "AF-4092",
		},
		OnEvent: map[string]actions.ActionTrigger{
			"marker_tap": actions.NewShowToastAction("slot_map_marker_tap", actions.ShowToastPayload{
				Message:    "Rider is 4 mins away",
				DurationMs: 2000,
				Variant:    "info",
			}),
			"region_change": actions.NewTrackEventAction("slot_map_region_change", actions.TrackEventPayload{
				EventName:  "map_region_changed",
				Properties: map[string]any{"slot_id": "delivery_live_map"},
			}),
		},
		Fallback: &fallbackCard,
		Layout:   &cardLayout,
	})
	slotEnv.Tracking = &atoms.TrackingMeta{
		EventName: "snippet_impression",
		Properties: map[string]string{
			"snippet_id": "spatial_live_map_slot",
			"surface":    "spatial-custom-and-graph-demo",
		},
	}

	page := snippets.NewPage("spatial-custom-and-graph-demo", "Abeta Spatial Graph & Custom Slots").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			Subtitle:       &toolbarSub,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode: snippets.LayoutModeCentered,
			MaxWidthDp: atoms.IntPtr(1120),
		}).
		WithAppVersion("0.8.0").
		AddSnippets(header, graphEnv, slotEnv).
		WithTracking("page_loaded", map[string]string{
			"page_id": "spatial-custom-and-graph-demo",
			"version": "v1",
		}).
		Build()

	snippets.EnsureComponentIDs(&page)
	etag, err := snippets.ComputePageETag(page)
	if err != nil {
		t.Fatalf("ComputePageETag failed: %v", err)
	}
	page.ETag = etag
	page.PageVersion = snippets.DerivePageVersion(etag)
	return page
}

func TestGoldenSpatialCustomAndGraphScreen_RoundTrip(t *testing.T) {
	page := canonicalSpatialCustomAndGraphScreen(t)
	rawBytes := writeOrCompareGolden(t, "spatial_custom_and_graph_screen.json", page)
	assertStringOnlyTrackingProperties(t, rawBytes)

	var decoded snippets.PageResponse
	if err := json.Unmarshal(rawBytes, &decoded); err != nil {
		t.Fatalf("round-trip unmarshal spatial_custom_and_graph_screen.json: %v", err)
	}
	if decoded.PageID != "spatial-custom-and-graph-demo" || decoded.AppVersion != "0.8.0" {
		t.Fatalf("unexpected decoded spatial page identity: %+v", decoded)
	}
	if !strings.HasPrefix(decoded.PageVersion, "v1-") || !strings.HasPrefix(decoded.ETag, `"`) || !strings.HasSuffix(decoded.ETag, `"`) {
		t.Fatalf("expected deterministic page_version and quoted etag, got version=%q etag=%q", decoded.PageVersion, decoded.ETag)
	}
	if len(decoded.Snippets) != 3 {
		t.Fatalf("expected 3 snippets, got %d", len(decoded.Snippets))
	}
	if decoded.Snippets[0].SnippetType != snippets.SnippetTypeHeader ||
		decoded.Snippets[1].SnippetType != snippets.SnippetTypeGraphCanvas ||
		decoded.Snippets[2].SnippetType != snippets.SnippetTypeCustomSlot {
		t.Fatalf("unexpected snippet types: %+v", decoded.Snippets)
	}

	graphData, err := snippets.DecodeSnippetData[snippets.GraphCanvasSnippetData](decoded.Snippets[1])
	if err != nil {
		t.Fatalf("decode graph_canvas golden data: %v", err)
	}
	if graphData.CanvasID != "service_topology_canvas" || graphData.HeightDp != 380 || len(graphData.Nodes) != 3 || len(graphData.Edges) != 3 {
		t.Fatalf("unexpected graph_canvas golden data: %+v", graphData)
	}
	if graphData.Nodes[0].Draggable == nil || *graphData.Nodes[0].Draggable || !graphData.Nodes[0].Pinned {
		t.Fatalf("expected node_bff pinned and draggable=false, got %+v", graphData.Nodes[0])
	}
	if graphData.Edges[0].Style != snippets.GraphEdgeBezier || graphData.Edges[1].Style != snippets.GraphEdgeStraight || graphData.Edges[2].Style != snippets.GraphEdgeDashed {
		t.Fatalf("expected all 3 GraphEdgeStyle variants in golden fixture, got %+v", graphData.Edges)
	}

	slotData, err := snippets.DecodeSnippetData[snippets.CustomSlotSnippetData](decoded.Snippets[2])
	if err != nil {
		t.Fatalf("decode custom_slot golden data: %v", err)
	}
	if slotData.SlotID != "delivery_live_map" || slotData.SlotType != "native_map_view" || slotData.MinHeightDp == nil || *slotData.MinHeightDp != 240 || slotData.AspectRatio == nil || *slotData.AspectRatio != 1.75 {
		t.Fatalf("unexpected custom_slot golden data: %+v", slotData)
	}
	if len(slotData.OnEvent) != 2 || slotData.Fallback == nil || slotData.Fallback.SnippetParentID != "spatial_live_map_slot" {
		t.Fatalf("unexpected custom_slot events/fallback: %+v", slotData)
	}

	recomputedETag, err := snippets.ComputePageETag(decoded)
	if err != nil || recomputedETag != decoded.ETag {
		t.Fatalf("expected idempotent ComputePageETag=%q, got %q (err=%v)", decoded.ETag, recomputedETag, err)
	}
}
