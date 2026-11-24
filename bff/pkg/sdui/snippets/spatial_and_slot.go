package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// Default viewport and zoom bounds for GraphCanvasSnippetData matching KMP defaults.
const (
	DefaultGraphHeightDp = 360
	DefaultGraphMinZoom  = 0.5
	DefaultGraphMaxZoom  = 2.5
)

// GraphEdgeStyle identifies the visual path style for an edge inside GraphCanvasSnippetData.
type GraphEdgeStyle string

// Wire identifiers for GraphEdgeSpec.Style matching KMP GraphEdgeStyle 1:1.
const (
	GraphEdgeBezier   GraphEdgeStyle = "bezier"
	GraphEdgeStraight GraphEdgeStyle = "straight"
	GraphEdgeDashed   GraphEdgeStyle = "dashed"
)

// CustomSlotSnippetData represents a host-provided custom view slot ("custom_slot").
type CustomSlotSnippetData struct {
	SlotID       string                           `json:"slot_id"`
	SlotType     string                           `json:"slot_type"`
	MinHeightDp  *int                             `json:"min_height_dp,omitempty"`
	AspectRatio  *float64                         `json:"aspect_ratio,omitempty"`
	InitialState map[string]any                   `json:"initial_state,omitempty"`
	OnEvent      map[string]actions.ActionTrigger `json:"on_event,omitempty"`
	Fallback     *SnippetEnvelope                 `json:"fallback,omitempty"`
	Layout       *atoms.LayoutConfig              `json:"layout,omitempty"`
}

// GraphNodeSpec represents a positioned interactive node on a GraphCanvasSnippetData surface.
type GraphNodeSpec struct {
	NodeID      string                 `json:"node_id"`
	Label       string                 `json:"label"`
	Subtitle    string                 `json:"subtitle,omitempty"`
	Badge       string                 `json:"badge,omitempty"`
	X           float64                `json:"x"`
	Y           float64                `json:"y"`
	BgColor     *atoms.ColorTokenRef   `json:"bg_color,omitempty"`
	BorderColor *atoms.ColorTokenRef   `json:"border_color,omitempty"`
	IconName    string                 `json:"icon_name,omitempty"`
	Draggable   *bool                  `json:"draggable,omitempty"`
	Pinned      bool                   `json:"pinned,omitempty"`
	OnClick     *actions.ActionTrigger `json:"on_click,omitempty"`
	OnDragEnd   *actions.ActionTrigger `json:"on_drag_end,omitempty"`
	Tracking    *atoms.TrackingMeta    `json:"tracking,omitempty"`
}

// GraphEdgeSpec represents a directed edge connecting FromNodeID to ToNodeID in a graph canvas.
type GraphEdgeSpec struct {
	EdgeID      string                 `json:"edge_id"`
	FromNodeID  string                 `json:"from_node_id"`
	ToNodeID    string                 `json:"to_node_id"`
	Label       string                 `json:"label,omitempty"`
	Style       GraphEdgeStyle         `json:"style,omitempty"`
	Color       *atoms.ColorTokenRef   `json:"color,omitempty"`
	Highlighted bool                   `json:"highlighted,omitempty"`
	OnClick     *actions.ActionTrigger `json:"on_click,omitempty"`
}

// GraphCanvasSnippetData represents an interactive pan/zoom spatial graph ("graph_canvas").
type GraphCanvasSnippetData struct {
	CanvasID      string                 `json:"canvas_id"`
	Title         *atoms.TextAtom        `json:"title,omitempty"`
	Subtitle      *atoms.TextAtom        `json:"subtitle,omitempty"`
	HeightDp      int                    `json:"height_dp,omitempty"`
	Nodes         []GraphNodeSpec        `json:"nodes"`
	Edges         []GraphEdgeSpec        `json:"edges"`
	AllowPan      *bool                  `json:"allow_pan,omitempty"`
	AllowZoom     *bool                  `json:"allow_zoom,omitempty"`
	AllowNodeDrag *bool                  `json:"allow_node_drag,omitempty"`
	MinZoom       float64                `json:"min_zoom,omitempty"`
	MaxZoom       float64                `json:"max_zoom,omitempty"`
	OnNodeMoved   *actions.ActionTrigger `json:"on_node_moved,omitempty"`
	OnCanvasTap   *actions.ActionTrigger `json:"on_canvas_tap,omitempty"`
	Layout        *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// NewCustomSlotSnippet constructs a custom_slot SnippetEnvelope with defensive copies of
// mutable maps and nested fallback envelopes.
func NewCustomSlotSnippet(snippetID string, data CustomSlotSnippetData) SnippetEnvelope {
	cp := data
	if data.MinHeightDp != nil {
		cp.MinHeightDp = atoms.IntPtr(*data.MinHeightDp)
	}
	if data.AspectRatio != nil {
		cp.AspectRatio = atoms.Float64Ptr(*data.AspectRatio)
	}
	if len(data.InitialState) > 0 {
		state := make(map[string]any, len(data.InitialState))
		for k, v := range data.InitialState {
			state[k] = v
		}
		cp.InitialState = state
	}
	if len(data.OnEvent) > 0 {
		events := make(map[string]actions.ActionTrigger, len(data.OnEvent))
		for k, act := range data.OnEvent {
			events[k] = act
		}
		cp.OnEvent = events
	}
	if data.Fallback != nil {
		fb := *data.Fallback
		cp.Fallback = &fb
	}
	return SnippetEnvelope{
		SnippetID:   snippetID,
		SnippetType: SnippetTypeCustomSlot,
		Data:        cp,
	}
}

// NewGraphCanvasSnippet constructs a graph_canvas SnippetEnvelope, applying canonical KMP wire
// defaults (height_dp=360, allow_pan/allow_zoom/allow_node_drag=true, min_zoom=0.5, max_zoom=2.5,
// node draggable=true, edge style="bezier") whenever omitted.
func NewGraphCanvasSnippet(snippetID string, data GraphCanvasSnippetData) SnippetEnvelope {
	cp := data
	if cp.HeightDp <= 0 {
		cp.HeightDp = DefaultGraphHeightDp
	}
	if cp.MinZoom <= 0 {
		cp.MinZoom = DefaultGraphMinZoom
	}
	if cp.MaxZoom <= 0 {
		cp.MaxZoom = DefaultGraphMaxZoom
	}
	if cp.AllowPan == nil {
		cp.AllowPan = atoms.BoolPtr(true)
	} else {
		cp.AllowPan = atoms.BoolPtr(*cp.AllowPan)
	}
	if cp.AllowZoom == nil {
		cp.AllowZoom = atoms.BoolPtr(true)
	} else {
		cp.AllowZoom = atoms.BoolPtr(*cp.AllowZoom)
	}
	if cp.AllowNodeDrag == nil {
		cp.AllowNodeDrag = atoms.BoolPtr(true)
	} else {
		cp.AllowNodeDrag = atoms.BoolPtr(*cp.AllowNodeDrag)
	}

	nodes := make([]GraphNodeSpec, len(data.Nodes))
	for i, n := range data.Nodes {
		if n.Draggable == nil {
			n.Draggable = atoms.BoolPtr(true)
		} else {
			n.Draggable = atoms.BoolPtr(*n.Draggable)
		}
		nodes[i] = n
	}
	cp.Nodes = nodes

	edges := make([]GraphEdgeSpec, len(data.Edges))
	for i, e := range data.Edges {
		if e.Style == "" {
			e.Style = GraphEdgeBezier
		}
		edges[i] = e
	}
	cp.Edges = edges

	return SnippetEnvelope{
		SnippetID:   snippetID,
		SnippetType: SnippetTypeGraphCanvas,
		Data:        cp,
	}
}
