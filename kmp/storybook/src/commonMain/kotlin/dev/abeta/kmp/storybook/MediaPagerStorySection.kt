package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiMediaPager
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.MediaPagerAtom
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MEDIA_PAGER_HEIGHT_DP = 168
private const val MEDIA_PAGER_AUTO_ADVANCE_MS = 4500
private const val SPACER_SM_DP = 8
private const val SPACER_MD_DP = 12

/**
 * Interactive [UiMediaPager] (31st stateless UI atom) specimen card and live `MediaPagerAtom`
 * JSON preview embedded in [MediaFeedbackStory].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun MediaPagerSpecimenSection(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var showIndicators by remember { mutableStateOf(true) }
    var autoAdvance by remember { mutableStateOf(false) }
    val pagerAtom =
        remember(showIndicators, autoAdvance) {
            buildStoryMediaPagerAtom(showIndicators = showIndicators, autoAdvance = autoAdvance)
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "UiMediaPager · Hero & Product Gallery",
                    typography = TypographyTokenRef("title.large"),
                ),
            )
            UiTag(TagAtom(label = "31st Atom · 3 Slides", variant = TagVariant.BRAND))
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        UiText(
            TextAtom(
                text = "Swipe horizontally across slides or tap any banner to emit its ActionRef.",
                typography = TypographyTokenRef("body.small"),
                color = ColorTokenRef("text.secondary"),
            ),
        )
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiMediaPager(atom = pagerAtom, onAction = onAction)
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaPagerToggle("Pill Indicators", showIndicators) { showIndicators = it }
            MediaPagerToggle("Auto-Advance (4.5s)", autoAdvance) { autoAdvance = it }
        }
    }
    JsonContractPreviewCard(
        title = "MediaPagerAtom Wire JSON",
        jsonText = SduiJson.encodeToString(MediaPagerAtom.serializer(), pagerAtom),
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MediaPagerToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space8),
    ) {
        Text(text = label, style = ThemeTokens.typography.bodySmall, color = ThemeTokens.colors.text.primary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun buildStoryMediaPagerAtom(
    showIndicators: Boolean,
    autoAdvance: Boolean,
): MediaPagerAtom =
    MediaPagerAtom(
        id = "story_media_pager",
        heightDp = MEDIA_PAGER_HEIGHT_DP,
        autoAdvanceMs = if (autoAdvance) MEDIA_PAGER_AUTO_ADVANCE_MS else null,
        showIndicators = showIndicators,
        cornerRadius = CornerRadiusSpec(token = "radius.md"),
        onPageClick =
            ActionRef(
                id = "story_media_pager_fallback_tap",
                type = ActionTypes.SHOW_TOAST,
                payload = buildJsonObject { put("message", "Opened Abeta Fresh campaign slide") },
            ),
        items =
            listOf(
                pagerSlide(
                    id = "story_slide_harvest",
                    url = "https://cdn.abeta.dev/fresh/hero-harvest.png",
                    description = "Morning Harvest · 25% OFF Produce",
                    actionId = "story_media_slide_1",
                    toast = "Slide 1: Morning Harvest 25% OFF",
                ),
                pagerSlide(
                    id = "story_slide_dairy",
                    url = "https://cdn.abeta.dev/fresh/hero-dairy.png",
                    description = "Chilled A2 Dairy · 8-Min Delivery",
                    actionId = "story_media_slide_2",
                    toast = "Slide 2: Chilled A2 Dairy in 8 mins",
                ),
                pagerSlide(
                    id = "story_slide_bakery",
                    url = "https://cdn.abeta.dev/fresh/hero-bakery.png",
                    description = "Warm Artisan Sourdough · Stone-Baked",
                    actionId = "story_media_slide_3",
                    toast = "Slide 3: Warm Artisan Sourdough",
                ),
            ),
    )

private fun pagerSlide(
    id: String,
    url: String,
    description: String,
    actionId: String,
    toast: String,
): ImageAtom =
    ImageAtom(
        id = id,
        url = url,
        contentDescription = description,
        scaleMode = ImageScaleMode.CROP,
        cornerRadius = CornerRadiusSpec(token = "radius.md"),
        onClick =
            ActionRef(
                id = actionId,
                type = ActionTypes.SHOW_TOAST,
                payload = buildJsonObject { put("message", toast) },
            ),
    )
