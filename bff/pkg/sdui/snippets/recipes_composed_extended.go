package snippets

import "github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"

// ScrollRowNode creates a horizontally scrollable container ComposedNode with optional spacing in dp.
func ScrollRowNode(spacingDp int, children ...ComposedNode) ComposedNode {
	n := ComposedNode{
		Kind:     ComposedNodeScrollRow,
		Children: cloneNodeSlice(children),
	}
	if spacingDp > 0 {
		n.SpacingDp = atoms.IntPtr(spacingDp)
	}
	return n
}

// GridNode creates a multi-column grid container ComposedNode with optional column count and spacing in dp.
func GridNode(columns int, spacingDp int, children ...ComposedNode) ComposedNode {
	n := ComposedNode{
		Kind:     ComposedNodeGrid,
		Children: cloneNodeSlice(children),
	}
	if columns > 0 {
		n.Columns = atoms.IntPtr(columns)
	}
	if spacingDp > 0 {
		n.SpacingDp = atoms.IntPtr(spacingDp)
	}
	return n
}

// TextFieldNode wraps a TextFieldAtom as a composed leaf node.
func TextFieldNode(field atoms.TextFieldAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeTextField, TextField: &field}
}

// SwitchNode wraps a SwitchAtom as a composed leaf node.
func SwitchNode(sw atoms.SwitchAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeSwitch, Switch: &sw}
}

// CheckboxNode wraps a CheckboxAtom as a composed leaf node.
func CheckboxNode(cb atoms.CheckboxAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeCheckbox, Checkbox: &cb}
}

// SliderNode wraps a SliderAtom as a composed leaf node.
func SliderNode(sl atoms.SliderAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeSlider, Slider: &sl}
}

// MediaPagerNode wraps a MediaPagerAtom as a composed leaf node with defensive copy of items.
func MediaPagerNode(pager atoms.MediaPagerAtom) ComposedNode {
	cp := pager
	if len(pager.Items) > 0 {
		items := make([]atoms.ImageAtom, len(pager.Items))
		copy(items, pager.Items)
		cp.Items = items
	}
	return ComposedNode{Kind: ComposedNodeMediaPager, MediaPager: &cp}
}

// WithBoxAlignment sets the child alignment inside a parent BoxNode (e.g. BoxAlignTopStart).
func (n ComposedNode) WithBoxAlignment(alignment string) ComposedNode {
	n.BoxAlignment = alignment
	return n
}

// WithColumns sets the column count on a GridNode.
func (n ComposedNode) WithColumns(columns int) ComposedNode {
	n.Columns = atoms.IntPtr(columns)
	return n
}

// WithColumnsByWidth sets responsive breakpoint column counts on a GridNode.
func (n ComposedNode) WithColumnsByWidth(spec ResponsiveColumnsSpec) ComposedNode {
	cp := spec
	n.ColumnsByWidth = &cp
	return n
}

// WithTracking attaches impression/exposure tracking metadata to a ComposedNode.
func (n ComposedNode) WithTracking(eventName string, props map[string]string) ComposedNode {
	n.Tracking = cloneTrackingMeta(&atoms.TrackingMeta{
		EventName:  eventName,
		Properties: props,
	})
	return n
}

// WithClickTracking attaches click/interaction tracking metadata to a ComposedNode.
func (n ComposedNode) WithClickTracking(eventName string, props map[string]string) ComposedNode {
	n.ClickTracking = cloneTrackingMeta(&atoms.TrackingMeta{
		EventName:  eventName,
		Properties: props,
	})
	return n
}
