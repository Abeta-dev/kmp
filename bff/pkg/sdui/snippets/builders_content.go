package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// ── Banner Builder ─────────────────────────────────────────────────────────────

// BannerSnippetBuilder fluently builds a banner_v1 SnippetEnvelope.
type BannerSnippetBuilder struct {
	meta envelopeMeta
	data BannerSnippetData
}

// NewBannerSnippet creates a BannerSnippetBuilder.
func NewBannerSnippet(id string, title atoms.TextAtom) *BannerSnippetBuilder {
	return &BannerSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeBanner},
		data: BannerSnippetData{Title: title},
	}
}

func (b *BannerSnippetBuilder) WithParentID(parentID string) *BannerSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *BannerSnippetBuilder) WithTracking(eventName string, props map[string]string) *BannerSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *BannerSnippetBuilder) WithMotion(motion atoms.MotionSpec) *BannerSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *BannerSnippetBuilder) WithBody(body atoms.TextAtom) *BannerSnippetBuilder {
	b.data.Body = &body
	return b
}

func (b *BannerSnippetBuilder) WithIcon(icon atoms.IconAtom) *BannerSnippetBuilder {
	b.data.Icon = &icon
	return b
}

func (b *BannerSnippetBuilder) WithImage(img atoms.ImageAtom) *BannerSnippetBuilder {
	b.data.Image = &img
	return b
}

func (b *BannerSnippetBuilder) WithTag(tag atoms.TagAtom) *BannerSnippetBuilder {
	b.data.Tag = &tag
	return b
}

func (b *BannerSnippetBuilder) WithCTA(btn atoms.ButtonAtom) *BannerSnippetBuilder {
	b.data.CTA = &btn
	return b
}

func (b *BannerSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *BannerSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *BannerSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *BannerSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *BannerSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Card Builder ───────────────────────────────────────────────────────────────

// CardSnippetBuilder fluently builds a card_v1 SnippetEnvelope.
type CardSnippetBuilder struct {
	meta envelopeMeta
	data CardSnippetData
}

// NewCardSnippet creates a CardSnippetBuilder.
func NewCardSnippet(id string, title atoms.TextAtom) *CardSnippetBuilder {
	return &CardSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeCard},
		data: CardSnippetData{Title: title},
	}
}

func (b *CardSnippetBuilder) WithParentID(parentID string) *CardSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *CardSnippetBuilder) WithTracking(eventName string, props map[string]string) *CardSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *CardSnippetBuilder) WithMotion(motion atoms.MotionSpec) *CardSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *CardSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *CardSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *CardSnippetBuilder) WithDescription(desc atoms.TextAtom) *CardSnippetBuilder {
	b.data.Description = &desc
	return b
}

func (b *CardSnippetBuilder) WithMedia(img atoms.ImageAtom) *CardSnippetBuilder {
	b.data.Media = &img
	return b
}

func (b *CardSnippetBuilder) WithAvatar(avatar atoms.AvatarAtom) *CardSnippetBuilder {
	b.data.Avatar = &avatar
	return b
}

func (b *CardSnippetBuilder) WithSkeletons(skeletons ...atoms.SkeletonAtom) *CardSnippetBuilder {
	b.data.Skeletons = append(b.data.Skeletons, skeletons...)
	return b
}

func (b *CardSnippetBuilder) WithBadge(tag atoms.TagAtom) *CardSnippetBuilder {
	b.data.Badge = &tag
	return b
}

func (b *CardSnippetBuilder) WithPrimaryButton(btn atoms.ButtonAtom) *CardSnippetBuilder {
	b.data.PrimaryButton = &btn
	return b
}

func (b *CardSnippetBuilder) WithSecondaryButton(btn atoms.ButtonAtom) *CardSnippetBuilder {
	b.data.SecondaryButton = &btn
	return b
}

func (b *CardSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *CardSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *CardSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *CardSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *CardSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── ActionRow Builder ──────────────────────────────────────────────────────────

// ActionRowSnippetBuilder fluently builds an action_row SnippetEnvelope.
type ActionRowSnippetBuilder struct {
	meta envelopeMeta
	data ActionRowSnippetData
}

// NewActionRowSnippet creates an ActionRowSnippetBuilder.
func NewActionRowSnippet(id string, title atoms.TextAtom) *ActionRowSnippetBuilder {
	return &ActionRowSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeActionRow},
		data: ActionRowSnippetData{Title: title},
	}
}

func (b *ActionRowSnippetBuilder) WithParentID(parentID string) *ActionRowSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *ActionRowSnippetBuilder) WithTracking(eventName string, props map[string]string) *ActionRowSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *ActionRowSnippetBuilder) WithMotion(motion atoms.MotionSpec) *ActionRowSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *ActionRowSnippetBuilder) WithSubtitle(subtitle atoms.TextAtom) *ActionRowSnippetBuilder {
	b.data.Subtitle = &subtitle
	return b
}

func (b *ActionRowSnippetBuilder) WithLeadingIcon(icon atoms.IconAtom) *ActionRowSnippetBuilder {
	b.data.LeadingIcon = &icon
	return b
}

func (b *ActionRowSnippetBuilder) WithTrailingTag(tag atoms.TagAtom) *ActionRowSnippetBuilder {
	b.data.TrailingTag = &tag
	return b
}

func (b *ActionRowSnippetBuilder) WithTrailingIcon(icon atoms.IconAtom) *ActionRowSnippetBuilder {
	b.data.TrailingIcon = &icon
	return b
}

func (b *ActionRowSnippetBuilder) WithOnClick(trigger actions.ActionTrigger) *ActionRowSnippetBuilder {
	b.data.OnClick = &trigger
	return b
}

func (b *ActionRowSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *ActionRowSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *ActionRowSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── Form Builder ───────────────────────────────────────────────────────────────

// FormSnippetBuilder fluently builds a form_v1 SnippetEnvelope.
type FormSnippetBuilder struct {
	meta envelopeMeta
	data FormSnippetData
}

// NewFormSnippet creates a FormSnippetBuilder.
func NewFormSnippet(id, formID string, submit atoms.ButtonAtom) *FormSnippetBuilder {
	return &FormSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeForm},
		data: FormSnippetData{
			FormID:       formID,
			Fields:       []atoms.TextFieldAtom{},
			SubmitButton: submit,
		},
	}
}

func (b *FormSnippetBuilder) WithParentID(parentID string) *FormSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *FormSnippetBuilder) WithTracking(eventName string, props map[string]string) *FormSnippetBuilder {
	b.meta.tracking = cloneTrackingMeta(&atoms.TrackingMeta{EventName: eventName, Properties: props})
	return b
}

func (b *FormSnippetBuilder) WithMotion(motion atoms.MotionSpec) *FormSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *FormSnippetBuilder) WithTitle(title atoms.TextAtom) *FormSnippetBuilder {
	b.data.Title = &title
	return b
}

func (b *FormSnippetBuilder) WithDescription(desc atoms.TextAtom) *FormSnippetBuilder {
	b.data.Description = &desc
	return b
}

func (b *FormSnippetBuilder) AddField(field atoms.TextFieldAtom) *FormSnippetBuilder {
	b.data.Fields = append(b.data.Fields, field)
	return b
}

func (b *FormSnippetBuilder) AddFields(fields ...atoms.TextFieldAtom) *FormSnippetBuilder {
	b.data.Fields = append(b.data.Fields, fields...)
	return b
}

func (b *FormSnippetBuilder) AddSwitch(sw atoms.SwitchAtom) *FormSnippetBuilder {
	b.data.Switches = append(b.data.Switches, sw)
	return b
}

func (b *FormSnippetBuilder) AddSwitches(switches ...atoms.SwitchAtom) *FormSnippetBuilder {
	b.data.Switches = append(b.data.Switches, switches...)
	return b
}

func (b *FormSnippetBuilder) AddCheckbox(cb atoms.CheckboxAtom) *FormSnippetBuilder {
	b.data.Checkboxes = append(b.data.Checkboxes, cb)
	return b
}

func (b *FormSnippetBuilder) AddCheckboxes(checkboxes ...atoms.CheckboxAtom) *FormSnippetBuilder {
	b.data.Checkboxes = append(b.data.Checkboxes, checkboxes...)
	return b
}

func (b *FormSnippetBuilder) AddSlider(sl atoms.SliderAtom) *FormSnippetBuilder {
	b.data.Sliders = append(b.data.Sliders, sl)
	return b
}

func (b *FormSnippetBuilder) AddSliders(sliders ...atoms.SliderAtom) *FormSnippetBuilder {
	b.data.Sliders = append(b.data.Sliders, sliders...)
	return b
}

func (b *FormSnippetBuilder) WithCancelButton(btn atoms.ButtonAtom) *FormSnippetBuilder {
	b.data.CancelButton = &btn
	return b
}

func (b *FormSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *FormSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *FormSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}
