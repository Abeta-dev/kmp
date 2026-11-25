package snippets

import "github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"

// ── Carousel Builder ───────────────────────────────────────────────────────────

// CarouselSnippetBuilder fluently builds a carousel SnippetEnvelope.
type CarouselSnippetBuilder struct {
	meta envelopeMeta
	data CarouselSnippetData
}

// NewCarouselSnippet creates a CarouselSnippetBuilder.
func NewCarouselSnippet(id string) *CarouselSnippetBuilder {
	return &CarouselSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeCarousel},
		data: CarouselSnippetData{Items: []SnippetEnvelope{}},
	}
}

func (b *CarouselSnippetBuilder) WithParentID(parentID string) *CarouselSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *CarouselSnippetBuilder) WithTracking(eventName string, props map[string]string) *CarouselSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *CarouselSnippetBuilder) WithMotion(motion atoms.MotionSpec) *CarouselSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *CarouselSnippetBuilder) WithTitle(title atoms.TextAtom) *CarouselSnippetBuilder {
	b.data.Title = &title
	return b
}

func (b *CarouselSnippetBuilder) AddItem(item SnippetEnvelope) *CarouselSnippetBuilder {
	b.data.Items = append(b.data.Items, item)
	return b
}

func (b *CarouselSnippetBuilder) AddItems(items ...SnippetEnvelope) *CarouselSnippetBuilder {
	b.data.Items = append(b.data.Items, items...)
	return b
}

func (b *CarouselSnippetBuilder) WithItemWidthDp(dp int) *CarouselSnippetBuilder {
	b.data.ItemWidthDp = atoms.IntPtr(dp)
	return b
}

func (b *CarouselSnippetBuilder) WithItemWidthByWidth(spec ResponsiveIntSpec) *CarouselSnippetBuilder {
	b.data.ItemWidthByWidth = &spec
	return b
}

func (b *CarouselSnippetBuilder) WithSpacingDp(dp int) *CarouselSnippetBuilder {
	b.data.SpacingDp = atoms.IntPtr(dp)
	return b
}

func (b *CarouselSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *CarouselSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *CarouselSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Grid Builder ───────────────────────────────────────────────────────────────

// GridSnippetBuilder fluently builds a grid SnippetEnvelope.
type GridSnippetBuilder struct {
	meta envelopeMeta
	data GridSnippetData
}

// NewGridSnippet creates a GridSnippetBuilder with the requested column count.
func NewGridSnippet(id string, columns int) *GridSnippetBuilder {
	return &GridSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeGrid},
		data: GridSnippetData{
			Columns: columns,
			Items:   []SnippetEnvelope{},
		},
	}
}

func (b *GridSnippetBuilder) WithParentID(parentID string) *GridSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *GridSnippetBuilder) WithTracking(eventName string, props map[string]string) *GridSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *GridSnippetBuilder) WithMotion(motion atoms.MotionSpec) *GridSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *GridSnippetBuilder) WithTitle(title atoms.TextAtom) *GridSnippetBuilder {
	b.data.Title = &title
	return b
}

func (b *GridSnippetBuilder) WithSpacingDp(dp int) *GridSnippetBuilder {
	b.data.SpacingDp = atoms.IntPtr(dp)
	return b
}

func (b *GridSnippetBuilder) WithColumnsByWidth(spec ResponsiveColumnsSpec) *GridSnippetBuilder {
	b.data.ColumnsByWidth = &spec
	return b
}

func (b *GridSnippetBuilder) AddItem(item SnippetEnvelope) *GridSnippetBuilder {
	b.data.Items = append(b.data.Items, item)
	return b
}

func (b *GridSnippetBuilder) AddItems(items ...SnippetEnvelope) *GridSnippetBuilder {
	b.data.Items = append(b.data.Items, items...)
	return b
}

func (b *GridSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *GridSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *GridSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Tabs Builder ───────────────────────────────────────────────────────────────

// TabsSnippetBuilder fluently builds a tabs SnippetEnvelope.
type TabsSnippetBuilder struct {
	meta envelopeMeta
	data TabsSnippetData
}

// NewTabsSnippet creates a TabsSnippetBuilder.
func NewTabsSnippet(id string) *TabsSnippetBuilder {
	return &TabsSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeTabs},
		data: TabsSnippetData{Tabs: []TabItemSpec{}},
	}
}

func (b *TabsSnippetBuilder) WithParentID(parentID string) *TabsSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *TabsSnippetBuilder) WithTracking(eventName string, props map[string]string) *TabsSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *TabsSnippetBuilder) WithMotion(motion atoms.MotionSpec) *TabsSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *TabsSnippetBuilder) AddTab(tab TabItemSpec) *TabsSnippetBuilder {
	b.data.Tabs = append(b.data.Tabs, tab)
	return b
}

func (b *TabsSnippetBuilder) AddTabs(tabs ...TabItemSpec) *TabsSnippetBuilder {
	b.data.Tabs = append(b.data.Tabs, tabs...)
	return b
}

func (b *TabsSnippetBuilder) WithSelectedIndex(idx int) *TabsSnippetBuilder {
	b.data.SelectedIndex = idx
	return b
}

func (b *TabsSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *TabsSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *TabsSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Accordion Builder ──────────────────────────────────────────────────────────

// AccordionSnippetBuilder fluently builds an accordion SnippetEnvelope.
type AccordionSnippetBuilder struct {
	meta envelopeMeta
	data AccordionSnippetData
}

// NewAccordionSnippet creates an AccordionSnippetBuilder.
func NewAccordionSnippet(id string, title atoms.TextAtom) *AccordionSnippetBuilder {
	return &AccordionSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeAccordion},
		data: AccordionSnippetData{
			Title:    title,
			Snippets: []SnippetEnvelope{},
		},
	}
}

func (b *AccordionSnippetBuilder) WithParentID(parentID string) *AccordionSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *AccordionSnippetBuilder) WithTracking(eventName string, props map[string]string) *AccordionSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *AccordionSnippetBuilder) WithMotion(motion atoms.MotionSpec) *AccordionSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *AccordionSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *AccordionSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *AccordionSnippetBuilder) WithInitiallyExpanded(expanded bool) *AccordionSnippetBuilder {
	b.data.InitiallyExpanded = expanded
	return b
}

func (b *AccordionSnippetBuilder) AddSnippet(env SnippetEnvelope) *AccordionSnippetBuilder {
	b.data.Snippets = append(b.data.Snippets, env)
	return b
}

func (b *AccordionSnippetBuilder) AddSnippets(envs ...SnippetEnvelope) *AccordionSnippetBuilder {
	b.data.Snippets = append(b.data.Snippets, envs...)
	return b
}

func (b *AccordionSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *AccordionSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *AccordionSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}
