package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// ── NudgeBar Builder ───────────────────────────────────────────────────────────

// NudgeBarSnippetBuilder fluently builds a nudge_bar SnippetEnvelope.
type NudgeBarSnippetBuilder struct {
	meta envelopeMeta
	data NudgeBarSnippetData
}

// NewNudgeBarSnippet creates a NudgeBarSnippetBuilder with required snippet ID,
// nudge identifier, and title atom. Dismissible defaults to true to match KMP wire defaults.
func NewNudgeBarSnippet(id, nudgeID string, title atoms.TextAtom) *NudgeBarSnippetBuilder {
	return &NudgeBarSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeNudgeBar},
		data: NudgeBarSnippetData{
			NudgeID:     nudgeID,
			Title:       title,
			Dismissible: true,
		},
	}
}

func (b *NudgeBarSnippetBuilder) WithParentID(parentID string) *NudgeBarSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *NudgeBarSnippetBuilder) WithTracking(eventName string, props map[string]any) *NudgeBarSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *NudgeBarSnippetBuilder) WithMotion(motion atoms.MotionSpec) *NudgeBarSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *NudgeBarSnippetBuilder) WithSticky(sticky bool) *NudgeBarSnippetBuilder {
	b.meta.sticky = sticky
	return b
}

func (b *NudgeBarSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *NudgeBarSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *NudgeBarSnippetBuilder) WithIcon(icon atoms.IconAtom) *NudgeBarSnippetBuilder {
	b.data.Icon = &icon
	return b
}

func (b *NudgeBarSnippetBuilder) WithBadge(badge atoms.TagAtom) *NudgeBarSnippetBuilder {
	b.data.Badge = &badge
	return b
}

func (b *NudgeBarSnippetBuilder) WithCountdown(countdown atoms.CountdownAtom) *NudgeBarSnippetBuilder {
	b.data.Countdown = &countdown
	return b
}

func (b *NudgeBarSnippetBuilder) WithProgress(progress float64) *NudgeBarSnippetBuilder {
	b.data.Progress = atoms.Float64Ptr(progress)
	return b
}

func (b *NudgeBarSnippetBuilder) WithCTA(btn atoms.ButtonAtom) *NudgeBarSnippetBuilder {
	b.data.CTA = &btn
	return b
}

func (b *NudgeBarSnippetBuilder) WithDismissible(dismissible bool) *NudgeBarSnippetBuilder {
	b.data.Dismissible = dismissible
	return b
}

func (b *NudgeBarSnippetBuilder) WithPointerEdge(edge string) *NudgeBarSnippetBuilder {
	b.data.PointerEdge = edge
	return b
}

func (b *NudgeBarSnippetBuilder) WithOnDismiss(trigger actions.ActionTrigger) *NudgeBarSnippetBuilder {
	b.data.OnDismiss = &trigger
	return b
}

func (b *NudgeBarSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *NudgeBarSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *NudgeBarSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *NudgeBarSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *NudgeBarSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── MarqueeStrip Builder ───────────────────────────────────────────────────────

// MarqueeStripSnippetBuilder fluently builds a marquee_strip SnippetEnvelope.
type MarqueeStripSnippetBuilder struct {
	meta envelopeMeta
	data MarqueeStripSnippetData
}

// NewMarqueeStripSnippet creates a MarqueeStripSnippetBuilder with a defensive copy
// of initial items (never nil) and sensible velocity/separator defaults.
func NewMarqueeStripSnippet(id, stripID string, items ...MarqueeItem) *MarqueeStripSnippetBuilder {
	initial := make([]MarqueeItem, 0, len(items))
	initial = append(initial, items...)
	return &MarqueeStripSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeMarqueeStrip},
		data: MarqueeStripSnippetData{
			StripID:          stripID,
			Items:            initial,
			VelocityDpPerSec: 48,
			Separator:        "•",
		},
	}
}

func (b *MarqueeStripSnippetBuilder) WithParentID(parentID string) *MarqueeStripSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *MarqueeStripSnippetBuilder) WithTracking(eventName string, props map[string]any) *MarqueeStripSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *MarqueeStripSnippetBuilder) WithMotion(motion atoms.MotionSpec) *MarqueeStripSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *MarqueeStripSnippetBuilder) AddItem(item MarqueeItem) *MarqueeStripSnippetBuilder {
	b.data.Items = append(b.data.Items, item)
	return b
}

func (b *MarqueeStripSnippetBuilder) AddItems(items ...MarqueeItem) *MarqueeStripSnippetBuilder {
	b.data.Items = append(b.data.Items, items...)
	return b
}

func (b *MarqueeStripSnippetBuilder) WithVelocityDpPerSec(dpPerSec int) *MarqueeStripSnippetBuilder {
	b.data.VelocityDpPerSec = dpPerSec
	return b
}

func (b *MarqueeStripSnippetBuilder) WithSeparator(separator string) *MarqueeStripSnippetBuilder {
	b.data.Separator = separator
	return b
}

func (b *MarqueeStripSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *MarqueeStripSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *MarqueeStripSnippetBuilder) Build() SnippetEnvelope {
	out := b.data
	out.Items = make([]MarqueeItem, len(b.data.Items))
	copy(out.Items, b.data.Items)
	return b.meta.wrap(out)
}

// ── OrderTracker Builder ───────────────────────────────────────────────────────

// OrderTrackerSnippetBuilder fluently builds an order_tracker SnippetEnvelope.
type OrderTrackerSnippetBuilder struct {
	meta envelopeMeta
	data OrderTrackerSnippetData
}

// NewOrderTrackerSnippet creates an OrderTrackerSnippetBuilder with a defensive copy
// of initial order steps (never nil).
func NewOrderTrackerSnippet(
	id string,
	trackerID string,
	orderCode string,
	headline atoms.TextAtom,
	steps ...OrderTrackerStep,
) *OrderTrackerSnippetBuilder {
	initial := make([]OrderTrackerStep, 0, len(steps))
	initial = append(initial, steps...)
	return &OrderTrackerSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeOrderTracker},
		data: OrderTrackerSnippetData{
			TrackerID: trackerID,
			OrderCode: orderCode,
			Headline:  headline,
			Steps:     initial,
		},
	}
}

func (b *OrderTrackerSnippetBuilder) WithParentID(parentID string) *OrderTrackerSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *OrderTrackerSnippetBuilder) WithTracking(eventName string, props map[string]any) *OrderTrackerSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *OrderTrackerSnippetBuilder) WithMotion(motion atoms.MotionSpec) *OrderTrackerSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *OrderTrackerSnippetBuilder) WithEtaCountdown(countdown atoms.CountdownAtom) *OrderTrackerSnippetBuilder {
	b.data.EtaCountdown = &countdown
	return b
}

func (b *OrderTrackerSnippetBuilder) WithEtaBadge(badge atoms.TagAtom) *OrderTrackerSnippetBuilder {
	b.data.EtaBadge = &badge
	return b
}

func (b *OrderTrackerSnippetBuilder) WithRider(name, caption string) *OrderTrackerSnippetBuilder {
	b.data.RiderName = name
	b.data.RiderCaption = caption
	return b
}

func (b *OrderTrackerSnippetBuilder) AddStep(step OrderTrackerStep) *OrderTrackerSnippetBuilder {
	b.data.Steps = append(b.data.Steps, step)
	return b
}

func (b *OrderTrackerSnippetBuilder) AddSteps(steps ...OrderTrackerStep) *OrderTrackerSnippetBuilder {
	b.data.Steps = append(b.data.Steps, steps...)
	return b
}

func (b *OrderTrackerSnippetBuilder) WithPrimaryAction(btn atoms.ButtonAtom) *OrderTrackerSnippetBuilder {
	b.data.PrimaryAction = &btn
	return b
}

func (b *OrderTrackerSnippetBuilder) WithSecondaryAction(btn atoms.ButtonAtom) *OrderTrackerSnippetBuilder {
	b.data.SecondaryAction = &btn
	return b
}

func (b *OrderTrackerSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *OrderTrackerSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *OrderTrackerSnippetBuilder) Build() SnippetEnvelope {
	out := b.data
	out.Steps = make([]OrderTrackerStep, len(b.data.Steps))
	copy(out.Steps, b.data.Steps)
	return b.meta.wrap(out)
}

// ── ComposedBlock Builder ──────────────────────────────────────────────────────

// ComposedBlockSnippetBuilder fluently builds a composed_block SnippetEnvelope.
type ComposedBlockSnippetBuilder struct {
	meta envelopeMeta
	data ComposedBlockSnippetData
}

// NewComposedBlockSnippet creates a ComposedBlockSnippetBuilder wrapping a deep-copied
// ComposedNode root so caller slice mutations cannot leak into built envelopes.
func NewComposedBlockSnippet(id, blockID string, root ComposedNode) *ComposedBlockSnippetBuilder {
	return &ComposedBlockSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeComposedBlock},
		data: ComposedBlockSnippetData{
			BlockID: blockID,
			Root:    cloneComposedNode(root),
		},
	}
}

func (b *ComposedBlockSnippetBuilder) WithParentID(parentID string) *ComposedBlockSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *ComposedBlockSnippetBuilder) WithTracking(eventName string, props map[string]any) *ComposedBlockSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *ComposedBlockSnippetBuilder) WithMotion(motion atoms.MotionSpec) *ComposedBlockSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *ComposedBlockSnippetBuilder) WithSticky(sticky bool) *ComposedBlockSnippetBuilder {
	b.meta.sticky = sticky
	return b
}

func (b *ComposedBlockSnippetBuilder) WithRecipeName(name string) *ComposedBlockSnippetBuilder {
	b.data.RecipeName = name
	return b
}

func (b *ComposedBlockSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *ComposedBlockSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *ComposedBlockSnippetBuilder) Build() SnippetEnvelope {
	out := b.data
	out.Root = cloneComposedNode(b.data.Root)
	return b.meta.wrap(out)
}

func isComposedContainerKind(kind string) bool {
	switch kind {
	case ComposedNodeColumn, ComposedNodeRow, ComposedNodeBox, ComposedNodeCard, ComposedNodeFlowRow,
		ComposedNodeScrollRow, ComposedNodeGrid:
		return true
	default:
		return false
	}
}

func cloneTrackingMeta(meta *atoms.TrackingMeta) *atoms.TrackingMeta {
	if meta == nil {
		return nil
	}
	cp := *meta
	if len(meta.Properties) > 0 {
		props := make(map[string]any, len(meta.Properties))
		for k, v := range meta.Properties {
			props[k] = v
		}
		cp.Properties = props
	}
	return &cp
}

func cloneComposedNode(node ComposedNode) ComposedNode {
	cloned := node
	if len(node.Children) > 0 {
		children := make([]ComposedNode, len(node.Children))
		for i, ch := range node.Children {
			children[i] = cloneComposedNode(ch)
		}
		cloned.Children = children
	} else if isComposedContainerKind(node.Kind) {
		cloned.Children = []ComposedNode{}
	}
	if node.ColumnsByWidth != nil {
		cp := *node.ColumnsByWidth
		cloned.ColumnsByWidth = &cp
	}
	if node.MediaPager != nil {
		cp := *node.MediaPager
		if len(node.MediaPager.Items) > 0 {
			items := make([]atoms.ImageAtom, len(node.MediaPager.Items))
			copy(items, node.MediaPager.Items)
			cp.Items = items
		}
		cloned.MediaPager = &cp
	}
	cloned.Tracking = cloneTrackingMeta(node.Tracking)
	cloned.ClickTracking = cloneTrackingMeta(node.ClickTracking)
	return cloned
}
