package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// ── MetricCard Builder ─────────────────────────────────────────────────────────

// MetricCardSnippetBuilder fluently builds a metric_card SnippetEnvelope.
type MetricCardSnippetBuilder struct {
	meta envelopeMeta
	data MetricCardSnippetData
}

// NewMetricCardSnippet creates a MetricCardSnippetBuilder.
func NewMetricCardSnippet(id string, label, value atoms.TextAtom) *MetricCardSnippetBuilder {
	return &MetricCardSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeMetricCard},
		data: MetricCardSnippetData{Label: label, Value: value},
	}
}

func (b *MetricCardSnippetBuilder) WithParentID(parentID string) *MetricCardSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *MetricCardSnippetBuilder) WithTracking(eventName string, props map[string]string) *MetricCardSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *MetricCardSnippetBuilder) WithMotion(motion atoms.MotionSpec) *MetricCardSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *MetricCardSnippetBuilder) WithDelta(tag atoms.TagAtom) *MetricCardSnippetBuilder {
	b.data.Delta = &tag
	return b
}

func (b *MetricCardSnippetBuilder) WithIcon(icon atoms.IconAtom) *MetricCardSnippetBuilder {
	b.data.Icon = &icon
	return b
}

func (b *MetricCardSnippetBuilder) WithProgress(progress atoms.ProgressAtom) *MetricCardSnippetBuilder {
	b.data.Progress = &progress
	return b
}

func (b *MetricCardSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *MetricCardSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *MetricCardSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *MetricCardSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *MetricCardSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── ProductCard Builder ────────────────────────────────────────────────────────

// ProductCardSnippetBuilder fluently builds a product_card SnippetEnvelope.
type ProductCardSnippetBuilder struct {
	meta envelopeMeta
	data ProductCardSnippetData
}

// NewProductCardSnippet creates a ProductCardSnippetBuilder with required product identity,
// title, price display, unit price in cents, and inline quantity stepper.
func NewProductCardSnippet(
	id string,
	productID string,
	title atoms.TextAtom,
	price atoms.TextAtom,
	unitPriceCents int,
	stepper atoms.StepperAtom,
) *ProductCardSnippetBuilder {
	return &ProductCardSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeProductCard},
		data: ProductCardSnippetData{
			ProductID:      productID,
			Title:          title,
			Price:          price,
			UnitPriceCents: unitPriceCents,
			Stepper:        stepper,
		},
	}
}

func (b *ProductCardSnippetBuilder) WithParentID(parentID string) *ProductCardSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *ProductCardSnippetBuilder) WithTracking(eventName string, props map[string]string) *ProductCardSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *ProductCardSnippetBuilder) WithMotion(motion atoms.MotionSpec) *ProductCardSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *ProductCardSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *ProductCardSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *ProductCardSnippetBuilder) WithOriginalPrice(originalPrice atoms.TextAtom) *ProductCardSnippetBuilder {
	b.data.OriginalPrice = &originalPrice
	return b
}

func (b *ProductCardSnippetBuilder) WithBadge(badge atoms.TagAtom) *ProductCardSnippetBuilder {
	b.data.Badge = &badge
	return b
}

func (b *ProductCardSnippetBuilder) WithImage(img atoms.ImageAtom) *ProductCardSnippetBuilder {
	b.data.Image = &img
	return b
}

func (b *ProductCardSnippetBuilder) WithRating(rating atoms.TextAtom) *ProductCardSnippetBuilder {
	b.data.Rating = &rating
	return b
}

func (b *ProductCardSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *ProductCardSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *ProductCardSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *ProductCardSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *ProductCardSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}
