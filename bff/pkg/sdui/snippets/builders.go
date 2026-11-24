package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// PageBuilder fluently constructs a PageResponse.
type PageBuilder struct {
	page PageResponse
}

// NewPage initializes a PageBuilder for pageID and title.
func NewPage(pageID, title string) *PageBuilder {
	return &PageBuilder{
		page: PageResponse{
			PageID:   pageID,
			Title:    title,
			Snippets: []SnippetEnvelope{},
		},
	}
}

// WithToolbar attaches a ToolbarSpec to the page.
func (b *PageBuilder) WithToolbar(toolbar ToolbarSpec) *PageBuilder {
	b.page.Toolbar = &toolbar
	return b
}

// WithBottomBar attaches a BottomBarSpec to the page.
func (b *PageBuilder) WithBottomBar(bottomBar BottomBarSpec) *PageBuilder {
	b.page.BottomBar = &bottomBar
	return b
}

// WithFloatingCart attaches a FloatingCartSpec to the page.
func (b *PageBuilder) WithFloatingCart(cart FloatingCartSpec) *PageBuilder {
	b.page.FloatingCart = &cart
	return b
}

// AddSnippet appends a single SnippetEnvelope.
func (b *PageBuilder) AddSnippet(env SnippetEnvelope) *PageBuilder {
	b.page.Snippets = append(b.page.Snippets, env)
	return b
}

// AddSnippets appends multiple SnippetEnvelopes.
func (b *PageBuilder) AddSnippets(envs ...SnippetEnvelope) *PageBuilder {
	b.page.Snippets = append(b.page.Snippets, envs...)
	return b
}

// AddPageAction appends a page-level ActionTrigger (e.g., on-load analytics).
func (b *PageBuilder) AddPageAction(action actions.ActionTrigger) *PageBuilder {
	b.page.PageActions = append(b.page.PageActions, action)
	return b
}

// WithPagination attaches cursor-based PaginationSpec metadata to the page.
func (b *PageBuilder) WithPagination(pagination PaginationSpec) *PageBuilder {
	b.page.Pagination = &pagination
	return b
}

// WithPullToRefresh attaches a pull-to-refresh ActionTrigger to the page.
func (b *PageBuilder) WithPullToRefresh(trigger actions.ActionTrigger) *PageBuilder {
	b.page.PullToRefresh = &trigger
	return b
}

// WithAdaptiveLayout attaches responsive breakpoint & split-pane hints to the page.
func (b *PageBuilder) WithAdaptiveLayout(spec AdaptivePageSpec) *PageBuilder {
	b.page.AdaptiveLayout = &spec
	return b
}

// WithTracking attaches page-level TrackingMeta.
func (b *PageBuilder) WithTracking(eventName string, properties map[string]any) *PageBuilder {
	b.page.Tracking = &atoms.TrackingMeta{
		EventName:  eventName,
		Properties: properties,
	}
	return b
}

// Build returns the constructed PageResponse.
func (b *PageBuilder) Build() PageResponse {
	return b.page
}

// envelopeMeta holds shared envelope fields embedded by snippet builders.
type envelopeMeta struct {
	id       string
	parentID string
	kind     string
	tracking *atoms.TrackingMeta
	motion   *atoms.MotionSpec
	sticky   bool
}

func (m envelopeMeta) wrap(data any) SnippetEnvelope {
	return SnippetEnvelope{
		SnippetID:       m.id,
		SnippetParentID: m.parentID,
		SnippetType:     m.kind,
		Data:            data,
		Tracking:        m.tracking,
		Motion:          m.motion,
		Sticky:          m.sticky,
	}
}

// ── Header Builder ─────────────────────────────────────────────────────────────

// HeaderSnippetBuilder fluently builds a header_v1 SnippetEnvelope.
type HeaderSnippetBuilder struct {
	meta envelopeMeta
	data HeaderSnippetData
}

// NewHeaderSnippet creates a HeaderSnippetBuilder.
func NewHeaderSnippet(id string, title atoms.TextAtom) *HeaderSnippetBuilder {
	return &HeaderSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeHeader},
		data: HeaderSnippetData{Title: title},
	}
}

func (b *HeaderSnippetBuilder) WithParentID(parentID string) *HeaderSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *HeaderSnippetBuilder) WithTracking(eventName string, props map[string]any) *HeaderSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *HeaderSnippetBuilder) WithMotion(motion atoms.MotionSpec) *HeaderSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *HeaderSnippetBuilder) WithSticky(sticky bool) *HeaderSnippetBuilder {
	b.meta.sticky = sticky
	return b
}

func (b *HeaderSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *HeaderSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *HeaderSnippetBuilder) WithLeadingIcon(icon atoms.IconAtom) *HeaderSnippetBuilder {
	b.data.LeadingIcon = &icon
	return b
}

func (b *HeaderSnippetBuilder) WithAvatar(avatar atoms.AvatarAtom) *HeaderSnippetBuilder {
	b.data.Avatar = &avatar
	return b
}

func (b *HeaderSnippetBuilder) WithTrailingTag(tag atoms.TagAtom) *HeaderSnippetBuilder {
	b.data.TrailingTag = &tag
	return b
}

func (b *HeaderSnippetBuilder) WithTrailingAction(btn atoms.ButtonAtom) *HeaderSnippetBuilder {
	b.data.TrailingAction = &btn
	return b
}

func (b *HeaderSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *HeaderSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *HeaderSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── EmptyState Builder ─────────────────────────────────────────────────────────

// EmptyStateSnippetBuilder fluently builds an empty_state SnippetEnvelope.
type EmptyStateSnippetBuilder struct {
	meta envelopeMeta
	data EmptyStateSnippetData
}

// NewEmptyStateSnippet creates an EmptyStateSnippetBuilder.
func NewEmptyStateSnippet(id string, title atoms.TextAtom) *EmptyStateSnippetBuilder {
	return &EmptyStateSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeEmptyState},
		data: EmptyStateSnippetData{Title: title},
	}
}

func (b *EmptyStateSnippetBuilder) WithParentID(parentID string) *EmptyStateSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *EmptyStateSnippetBuilder) WithTracking(eventName string, props map[string]any) *EmptyStateSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *EmptyStateSnippetBuilder) WithMotion(motion atoms.MotionSpec) *EmptyStateSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *EmptyStateSnippetBuilder) WithMessage(msg atoms.TextAtom) *EmptyStateSnippetBuilder {
	b.data.Message = &msg
	return b
}

func (b *EmptyStateSnippetBuilder) WithIcon(icon atoms.IconAtom) *EmptyStateSnippetBuilder {
	b.data.Icon = &icon
	return b
}

func (b *EmptyStateSnippetBuilder) WithImage(img atoms.ImageAtom) *EmptyStateSnippetBuilder {
	b.data.Image = &img
	return b
}

func (b *EmptyStateSnippetBuilder) WithActionButton(btn atoms.ButtonAtom) *EmptyStateSnippetBuilder {
	b.data.ActionButton = &btn
	return b
}

func (b *EmptyStateSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *EmptyStateSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *EmptyStateSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Divider & Spacer Builders ──────────────────────────────────────────────────

// DividerSnippetBuilder fluently builds a divider SnippetEnvelope.
type DividerSnippetBuilder struct {
	meta envelopeMeta
	data DividerSnippetData
}

// NewDividerSnippet creates a DividerSnippetBuilder.
func NewDividerSnippet(id string, divider atoms.DividerAtom) *DividerSnippetBuilder {
	return &DividerSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeDivider},
		data: DividerSnippetData{Divider: divider},
	}
}

func (b *DividerSnippetBuilder) WithParentID(parentID string) *DividerSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *DividerSnippetBuilder) WithTracking(eventName string, props map[string]any) *DividerSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *DividerSnippetBuilder) WithMotion(motion atoms.MotionSpec) *DividerSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *DividerSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *DividerSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *DividerSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// SpacerSnippetBuilder fluently builds a spacer SnippetEnvelope.
type SpacerSnippetBuilder struct {
	meta envelopeMeta
	data SpacerSnippetData
}

// NewSpacerSnippet creates a SpacerSnippetBuilder.
func NewSpacerSnippet(id string, spacer atoms.SpacerAtom) *SpacerSnippetBuilder {
	return &SpacerSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeSpacer},
		data: SpacerSnippetData{Spacer: spacer},
	}
}

func (b *SpacerSnippetBuilder) WithParentID(parentID string) *SpacerSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *SpacerSnippetBuilder) WithTracking(eventName string, props map[string]any) *SpacerSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *SpacerSnippetBuilder) WithMotion(motion atoms.MotionSpec) *SpacerSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *SpacerSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}
