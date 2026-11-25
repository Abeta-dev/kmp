package snippets_test

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestCustomSlotSnippet_BuilderDefaultsDefensiveCopyAndRoundTrip(t *testing.T) {
	initialState := map[string]any{"zoom": 14, "order_id": "AF-4092"}
	onEvent := map[string]actions.ActionTrigger{
		"marker_tap": actions.NewShowToastAction("evt_marker", actions.ShowToastPayload{Message: "Rider nearby"}),
	}
	fallback := snippets.NewCardSnippet(
		"map_fallback",
		atoms.NewText("Map Fallback", theming.TypographyTitleSmall, theming.ColorTextPrimary),
	).Build()

	env := snippets.NewCustomSlotSnippet("slot_live_map", snippets.CustomSlotSnippetData{
		SlotID:       "delivery_live_map",
		SlotType:     "native_map_view",
		MinHeightDp:  atoms.IntPtr(240),
		AspectRatio:  atoms.Float64Ptr(1.75),
		InitialState: initialState,
		OnEvent:      onEvent,
		Fallback:     &fallback,
	})

	// Mutate caller maps after construction to verify defensive copies.
	initialState["zoom"] = 99
	onEvent["extra"] = actions.NewBackPressAction("extra", actions.BackPressPayload{})

	if env.SnippetID != "slot_live_map" || env.SnippetType != snippets.SnippetTypeCustomSlot {
		t.Fatalf("unexpected custom_slot envelope header: %+v", env)
	}

	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal custom_slot: %v", err)
	}
	wire := string(raw)
	for _, key := range []string{
		`"slot_id":"delivery_live_map"`,
		`"slot_type":"native_map_view"`,
		`"min_height_dp":240`,
		`"aspect_ratio":1.75`,
		`"initial_state"`,
		`"on_event"`,
		`"fallback"`,
	} {
		if !strings.Contains(wire, key) {
			t.Errorf("expected custom_slot wire JSON to contain %s, got %s", key, wire)
		}
	}

	var decodedEnv snippets.SnippetEnvelope
	if err := json.Unmarshal(raw, &decodedEnv); err != nil {
		t.Fatalf("unmarshal custom_slot envelope: %v", err)
	}
	data, err := snippets.DecodeSnippetData[snippets.CustomSlotSnippetData](decodedEnv)
	if err != nil {
		t.Fatalf("DecodeSnippetData[CustomSlotSnippetData]: %v", err)
	}
	if data.SlotID != "delivery_live_map" || data.SlotType != "native_map_view" {
		t.Fatalf("unexpected custom_slot identity: %+v", data)
	}
	if data.MinHeightDp == nil || *data.MinHeightDp != 240 || data.AspectRatio == nil || *data.AspectRatio != 1.75 {
		t.Fatalf("unexpected custom_slot sizing: %+v", data)
	}
	if data.InitialState["zoom"] != float64(14) || len(data.OnEvent) != 1 {
		t.Fatalf("expected defensive copy of maps, got state=%+v events=%+v", data.InitialState, data.OnEvent)
	}
	if data.Fallback == nil || data.Fallback.SnippetID != "map_fallback" || data.Fallback.SnippetType != snippets.SnippetTypeCard {
		t.Fatalf("unexpected fallback envelope: %+v", data.Fallback)
	}
}

func TestGraphCanvasSnippet_DefaultsExplicitFalseAndRoundTrip(t *testing.T) {
	// 1. Defaults applied when omitted
	defaultEnv := snippets.NewGraphCanvasSnippet("graph_default", snippets.GraphCanvasSnippetData{
		CanvasID: "arch_graph",
		Nodes: []snippets.GraphNodeSpec{
			{NodeID: "n1", Label: "Core", X: 20, Y: 40},
		},
		Edges: []snippets.GraphEdgeSpec{
			{EdgeID: "e1", FromNodeID: "n1", ToNodeID: "n2"},
		},
	})
	if defaultEnv.SnippetType != snippets.SnippetTypeGraphCanvas {
		t.Fatalf("expected snippet_type=%q, got %q", snippets.SnippetTypeGraphCanvas, defaultEnv.SnippetType)
	}
	defaultData, err := snippets.DecodeSnippetData[snippets.GraphCanvasSnippetData](defaultEnv)
	if err != nil {
		t.Fatalf("decode default graph: %v", err)
	}
	if defaultData.HeightDp != snippets.DefaultGraphHeightDp ||
		defaultData.MinZoom != snippets.DefaultGraphMinZoom ||
		defaultData.MaxZoom != snippets.DefaultGraphMaxZoom ||
		defaultData.AllowPan == nil || !*defaultData.AllowPan ||
		defaultData.AllowZoom == nil || !*defaultData.AllowZoom ||
		defaultData.AllowNodeDrag == nil || !*defaultData.AllowNodeDrag {
		t.Fatalf("unexpected graph defaults: %+v", defaultData)
	}
	if len(defaultData.Nodes) != 1 || defaultData.Nodes[0].Draggable == nil || !*defaultData.Nodes[0].Draggable {
		t.Fatalf("expected node Draggable default true, got %+v", defaultData.Nodes)
	}
	if len(defaultData.Edges) != 1 || defaultData.Edges[0].Style != snippets.GraphEdgeBezier {
		t.Fatalf("expected edge Style default bezier, got %+v", defaultData.Edges)
	}

	// 2. Explicit false overrides and all 3 GraphEdgeStyle values round-trip cleanly
	clickAct := actions.NewShowToastAction("tap_node", actions.ShowToastPayload{Message: "Node tapped"})
	dragAct := actions.NewSaveStateAction("drag_node", actions.SaveStatePayload{Key: "node_pos", Value: "100,200"})
	movedAct := actions.NewSaveStateAction("moved", actions.SaveStatePayload{Key: "last_node", Value: "n1"})
	tapAct := actions.NewTrackEventAction("tap_canvas", actions.TrackEventPayload{EventName: "canvas_tap"})
	title := atoms.NewText("Topology", theming.TypographyTitleMedium, theming.ColorTextPrimary)
	sub := atoms.NewText("Read-only preview", theming.TypographyBodySmall, theming.ColorTextSecondary)

	customEnv := snippets.NewGraphCanvasSnippet("graph_custom", snippets.GraphCanvasSnippetData{
		CanvasID:      "locked_topology",
		Title:         &title,
		Subtitle:      &sub,
		HeightDp:      440,
		AllowPan:      atoms.BoolPtr(false),
		AllowZoom:     atoms.BoolPtr(false),
		AllowNodeDrag: atoms.BoolPtr(false),
		MinZoom:       0.25,
		MaxZoom:       3.0,
		OnNodeMoved:   &movedAct,
		OnCanvasTap:   &tapAct,
		Nodes: []snippets.GraphNodeSpec{
			{
				NodeID:      "node_a",
				Label:       "BFF",
				Subtitle:    "Go 1.26",
				Badge:       "EDGE",
				X:           60,
				Y:           80,
				BgColor:     atoms.ColorRef(theming.ColorBrandContainer),
				BorderColor: atoms.ColorRef(theming.ColorBrandPrimary),
				IconName:    "cloud",
				Draggable:   atoms.BoolPtr(false),
				Pinned:      true,
				OnClick:     &clickAct,
				OnDragEnd:   &dragAct,
				Tracking:    &atoms.TrackingMeta{EventName: "node_seen", Properties: map[string]string{"node_id": "node_a"}},
			},
		},
		Edges: []snippets.GraphEdgeSpec{
			{EdgeID: "e_bez", FromNodeID: "node_a", ToNodeID: "node_b", Label: "A", Style: snippets.GraphEdgeBezier, Highlighted: true, Color: atoms.ColorRef(theming.ColorBrandPrimary), OnClick: &clickAct},
			{EdgeID: "e_str", FromNodeID: "node_a", ToNodeID: "node_c", Label: "B", Style: snippets.GraphEdgeStraight},
			{EdgeID: "e_dsh", FromNodeID: "node_b", ToNodeID: "node_c", Label: "C", Style: snippets.GraphEdgeDashed},
		},
	})

	raw, err := json.Marshal(customEnv)
	if err != nil {
		t.Fatalf("marshal custom graph: %v", err)
	}
	wire := string(raw)
	for _, want := range []string{
		`"allow_pan":false`,
		`"allow_zoom":false`,
		`"allow_node_drag":false`,
		`"draggable":false`,
		`"pinned":true`,
		`"style":"bezier"`,
		`"style":"straight"`,
		`"style":"dashed"`,
		`"on_node_moved"`,
		`"on_canvas_tap"`,
		`"on_drag_end"`,
	} {
		if !strings.Contains(wire, want) {
			t.Errorf("expected graph wire JSON to contain %s, got %s", want, wire)
		}
	}

	var decodedEnv snippets.SnippetEnvelope
	if err := json.Unmarshal(raw, &decodedEnv); err != nil {
		t.Fatalf("unmarshal graph envelope: %v", err)
	}
	decoded, err := snippets.DecodeSnippetData[snippets.GraphCanvasSnippetData](decodedEnv)
	if err != nil {
		t.Fatalf("DecodeSnippetData[GraphCanvasSnippetData]: %v", err)
	}
	if decoded.HeightDp != 440 || *decoded.AllowPan || *decoded.AllowZoom || *decoded.AllowNodeDrag {
		t.Fatalf("expected explicit overrides preserved, got %+v", decoded)
	}
	if *decoded.Nodes[0].Draggable || !decoded.Nodes[0].Pinned || decoded.Edges[2].Style != snippets.GraphEdgeDashed {
		t.Fatalf("unexpected decoded node/edge fields: nodes=%+v edges=%+v", decoded.Nodes, decoded.Edges)
	}
}
