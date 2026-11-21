package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.DividerSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.SpacerSnippetData
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

private const val CARD_PADDING_DP = 16
private const val CARD_RADIUS_DP = 12
private const val ICON_SM_DP = 20
private const val ICON_MD_DP = 24
private const val ICON_LG_DP = 32
private const val AVATAR_SIZE_DP = 40
private const val SPACER_HEIGHT_DP = 24
private const val SLO_PROGRESS = 0.996f
private const val SLIDER_MAX = 4f
private const val SLIDER_STEPS = 3
private const val SKELETON_LINE_W = 180
private const val SKELETON_LINE_H = 14

/**
 * Descriptor pairing a display label with its canonical SDUI [SnippetEnvelope].
 */
internal data class SnippetSampleItem(
    val label: String,
    val envelope: SnippetEnvelope,
)

/**
 * Provides sample [SnippetEnvelope] fixtures covering all 17 canonical [SnippetTypes].
 */
internal object SnippetsSamples {
    val cardLayout =
        LayoutConfig(
            padding = SpacingValues(all = CARD_PADDING_DP),
            bgColor = ColorTokenRef(token = "surface.primary"),
            cornerRadius = CornerRadiusSpec(all = CARD_RADIUS_DP),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef(token = "border.subtle")),
            fillMaxWidth = true,
        )

    inline fun <reified T> encode(value: T): JsonObject {
        val element = SduiJson.encodeToJsonElement(serializer<T>(), value)
        return element as JsonObject
    }

    fun txt(
        text: String,
        role: String = "body.medium",
        color: String = "text.primary",
    ): TextAtom =
        TextAtom(
            text = text,
            typography = TypographyTokenRef(role),
            color = ColorTokenRef(color),
        )

    fun allItems(): List<SnippetSampleItem> =
        buildHeaderAndBanner() +
            buildMetricAndCard() +
            buildRowAndEmpty() +
            listOf(SnippetSampleItem("Form", buildFormEnvelope())) +
            buildDividerAndSpacer() +
            SnippetsContainerSamples.allItems()

    private fun buildHeaderAndBanner(): List<SnippetSampleItem> {
        val header =
            HeaderSnippetData(
                title = txt("Release Readiness", "headline.small"),
                subtitle = txt("All 17 snippet renderers active", "body.small", "text.secondary"),
                avatar = AvatarAtom(initials = "AB", sizeDp = AVATAR_SIZE_DP),
                trailingTag = TagAtom(label = "v1", variant = TagVariant.BRAND),
                trailingAction =
                    ButtonAtom(
                        label = "Inspect",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "header_inspect", type = ActionTypes.TRACK_EVENT),
                    ),
                layout = cardLayout,
            )
        val banner =
            BannerSnippetData(
                title = txt("Zero-Release Streaming", "title.medium"),
                body = txt("Update screen hierarchy from the Go BFF without shipping a binary."),
                icon = IconAtom(name = "notifications", sizeDp = ICON_MD_DP),
                tag = TagAtom(label = "NEW", variant = TagVariant.INFO),
                cta =
                    ButtonAtom(
                        label = "Acknowledge",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "banner_ack", type = ActionTypes.SHOW_TOAST),
                    ),
                layout = cardLayout.copy(bgColor = ColorTokenRef("brand.container")),
            )
        return listOf(
            SnippetSampleItem(
                "Header",
                SnippetEnvelope("story_header", snippetType = SnippetTypes.HEADER, data = encode(header)),
            ),
            SnippetSampleItem(
                "Banner",
                SnippetEnvelope("story_banner", snippetType = SnippetTypes.BANNER, data = encode(banner)),
            ),
        )
    }

    private fun buildMetricAndCard(): List<SnippetSampleItem> {
        val metric =
            MetricCardSnippetData(
                label = txt("Rendering SLA", "label.medium", "text.secondary"),
                value = txt("99.6%", "headline.medium"),
                delta = TagAtom(label = "+0.3%", variant = TagVariant.SUCCESS),
                icon = IconAtom(name = "check", sizeDp = ICON_SM_DP),
                progress = ProgressAtom(kind = ProgressKind.LINEAR, progress = SLO_PROGRESS),
                onClick = ActionRef(id = "metric_tap", type = ActionTypes.SHOW_TOAST),
                layout = cardLayout,
            )
        val card =
            CardSnippetData(
                title = txt("Atomic Card Snippet", "title.medium"),
                subtitle = txt("Composes Avatar, Text, Tag & Buttons", "label.medium", "text.brand"),
                description = txt("Cards adapt automatically to light, dark, and preset tokens."),
                avatar = AvatarAtom(initials = "SR", sizeDp = AVATAR_SIZE_DP),
                badge = TagAtom(label = "STABLE", variant = TagVariant.SUCCESS),
                skeletons =
                    listOf(
                        SkeletonAtom(
                            kind = SkeletonKind.TEXT,
                            widthDp = SKELETON_LINE_W,
                            heightDp = SKELETON_LINE_H,
                        ),
                    ),
                primaryButton =
                    ButtonAtom(
                        label = "Primary",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "card_primary", type = ActionTypes.NAVIGATE),
                    ),
                secondaryButton =
                    ButtonAtom(
                        label = "Secondary",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "card_secondary", type = ActionTypes.TRACK_EVENT),
                    ),
                layout = cardLayout,
            )
        return listOf(
            SnippetSampleItem(
                "Metric Card",
                SnippetEnvelope("story_metric", snippetType = SnippetTypes.METRIC_CARD, data = encode(metric)),
            ),
            SnippetSampleItem(
                "Card",
                SnippetEnvelope("story_card", snippetType = SnippetTypes.CARD, data = encode(card)),
            ),
        )
    }

    fun buildRowAndEmpty(): List<SnippetSampleItem> {
        val row =
            ActionRowSnippetData(
                title = txt("Workspace Preferences", "title.small"),
                subtitle = txt("Tap row to emit ActionRef", "body.small", "text.secondary"),
                leadingIcon = IconAtom(name = "settings", sizeDp = ICON_SM_DP),
                trailingTag = TagAtom(label = "SYNCED", variant = TagVariant.NEUTRAL),
                trailingIcon = IconAtom(name = "chevron_right", sizeDp = ICON_SM_DP),
                onClick = ActionRef(id = "row_tap", type = ActionTypes.NAVIGATE),
                layout = cardLayout,
            )
        val empty =
            EmptyStateSnippetData(
                title = txt("No Pending Alerts", "title.medium"),
                message = txt("Everything in your workspace looks healthy.", "body.small", "text.secondary"),
                icon = IconAtom(name = "info", sizeDp = ICON_LG_DP),
                actionButton =
                    ButtonAtom(
                        label = "Refresh",
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "empty_refresh", type = ActionTypes.REFRESH_PAGE),
                    ),
                layout = cardLayout,
            )
        return listOf(
            SnippetSampleItem(
                "Action Row",
                SnippetEnvelope("story_action_row", snippetType = SnippetTypes.ACTION_ROW, data = encode(row)),
            ),
            SnippetSampleItem(
                "Empty State",
                SnippetEnvelope("story_empty", snippetType = SnippetTypes.EMPTY_STATE, data = encode(empty)),
            ),
        )
    }

    private fun buildFormEnvelope(): SnippetEnvelope {
        val form =
            FormSnippetData(
                formId = "snippet_gallery_form",
                title = txt("Inline Form Snippet", "title.medium"),
                fields = listOf(TextFieldAtom(fieldId = "slug", label = "Service Slug", placeholder = "core")),
                switches = listOf(SwitchAtom(fieldId = "canary", label = "Canary Rollout", checked = true)),
                checkboxes =
                    listOf(
                        CheckboxAtom(fieldId = "verify", label = "Verify Checksum", required = true),
                    ),
                sliders =
                    listOf(
                        SliderAtom(
                            fieldId = "replicas",
                            label = "Replicas",
                            value = 2f,
                            minValue = 1f,
                            maxValue = SLIDER_MAX,
                            steps = SLIDER_STEPS,
                            unitLabel = "pods",
                        ),
                    ),
                submitButton =
                    ButtonAtom(
                        label = "Submit Form",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "form_submit", type = ActionTypes.CALL_API),
                    ),
                layout = cardLayout,
            )
        return SnippetEnvelope("story_form", snippetType = SnippetTypes.FORM, data = encode(form))
    }

    private fun buildDividerAndSpacer(): List<SnippetSampleItem> {
        val divider =
            DividerSnippetData(
                divider = DividerAtom(thicknessDp = 1f, color = ColorTokenRef("divider.default")),
            )
        val spacer = SpacerSnippetData(spacer = SpacerAtom(heightDp = SPACER_HEIGHT_DP))
        return listOf(
            SnippetSampleItem(
                "Divider",
                SnippetEnvelope("story_divider", snippetType = SnippetTypes.DIVIDER, data = encode(divider)),
            ),
            SnippetSampleItem(
                "Spacer",
                SnippetEnvelope("story_spacer", snippetType = SnippetTypes.SPACER, data = encode(spacer)),
            ),
        )
    }
}
