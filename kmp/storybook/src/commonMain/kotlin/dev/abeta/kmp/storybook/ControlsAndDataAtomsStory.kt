package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiAvatar
import dev.abeta.kmp.atoms.atoms.UiCheckbox
import dev.abeta.kmp.atoms.atoms.UiSkeleton
import dev.abeta.kmp.atoms.atoms.UiSlider
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiSwitch
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

private const val DEFAULT_SLIDER_VALUE = 40f
private const val SLIDER_MAX_VALUE = 100f
private const val SLIDER_STEPS = 4
private const val AVATAR_SM_DP = 32
private const val AVATAR_MD_DP = 44
private const val AVATAR_LG_DP = 56
private const val SKELETON_CIRCLE_DP = 44
private const val SKELETON_TEXT_WIDTH_DP = 220
private const val SKELETON_TEXT_HEIGHT_DP = 16
private const val SKELETON_SUBTITLE_WIDTH_DP = 150
private const val SKELETON_SUBTITLE_HEIGHT_DP = 12
private const val SKELETON_RECT_HEIGHT_DP = 88
private const val SHIMMER_CYCLE_MULTIPLIER = 2
private const val SPACER_SM_DP = 8
private const val SPACER_MD_DP = 12

/**
 * Interactive story showcasing [UiSwitch], [UiCheckbox], [UiSlider], [UiAvatar], and [UiSkeleton].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ControlsAndDataAtomsStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var switchChecked by remember { mutableStateOf(true) }
    var checkboxChecked by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(DEFAULT_SLIDER_VALUE) }
    var controlsDisabled by remember { mutableStateOf(false) }

    val switchAtom =
        remember(switchChecked, controlsDisabled) {
            SwitchAtom(
                fieldId = "story_auto_sync",
                label = "Auto-Sync SDUI Cache",
                subtitle = "Revalidate stale screens in the background",
                checked = switchChecked,
                enabled = !controlsDisabled,
                onChange =
                    ActionRef(
                        id = "story_switch_change",
                        type = ActionTypes.SAVE_STATE,
                        payload =
                            buildJsonObject {
                                put("key", "auto_sync")
                                put("value", !switchChecked)
                            },
                    ),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        ControlsShowcaseCard(
            switchAtom = switchAtom,
            switchChecked = switchChecked,
            checkboxChecked = checkboxChecked,
            sliderValue = sliderValue,
            controlsDisabled = controlsDisabled,
            onSwitchChange = { switchChecked = it },
            onCheckboxChange = { checkboxChecked = it },
            onSliderChange = { sliderValue = it },
            onDisabledChange = { controlsDisabled = it },
            onAction = onAction,
        )
        AvatarShowcaseCard(onAction = onAction)
        SkeletonShowcaseCard()
        JsonContractPreviewCard(
            title = "SwitchAtom Wire JSON",
            jsonText = SduiJson.encodeToString(SwitchAtom.serializer(), switchAtom),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun ControlsShowcaseCard(
    switchAtom: SwitchAtom,
    switchChecked: Boolean,
    checkboxChecked: Boolean,
    sliderValue: Float,
    controlsDisabled: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    onCheckboxChange: (Boolean) -> Unit,
    onSliderChange: (Float) -> Unit,
    onDisabledChange: (Boolean) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val (checkboxAtom, sliderAtom) = buildCheckAndSliderAtoms(checkboxChecked, sliderValue, controlsDisabled)

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Interactive Form Controls", typography = TypographyTokenRef("title.large")))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Text(
                    text = "Disable All",
                    style = ThemeTokens.typography.bodySmall,
                    color = ThemeTokens.colors.text.secondary,
                )
                Switch(checked = controlsDisabled, onCheckedChange = onDisabledChange)
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiSwitch(
            atom = switchAtom,
            checkedOverride = switchChecked,
            onCheckedChange = onSwitchChange,
            onAction = onAction,
        )
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        UiCheckbox(
            atom = checkboxAtom,
            checkedOverride = checkboxChecked,
            onCheckedChange = onCheckboxChange,
            onAction = onAction,
        )
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        UiSlider(
            atom = sliderAtom,
            valueOverride = sliderValue,
            onValueChange = onSliderChange,
            onAction = onAction,
        )
    }
}

private fun buildCheckAndSliderAtoms(
    checkboxChecked: Boolean,
    sliderValue: Float,
    controlsDisabled: Boolean,
): Pair<CheckboxAtom, SliderAtom> {
    val checkboxAtom =
        CheckboxAtom(
            fieldId = "story_strict_schema",
            label = "Strict Schema Validation",
            subtitle = "Reject unrecognized snippet envelopes",
            checked = checkboxChecked,
            enabled = !controlsDisabled,
            required = true,
            onChange = ActionRef(id = "story_checkbox_change", type = ActionTypes.SAVE_STATE),
        )
    val sliderAtom =
        SliderAtom(
            fieldId = "story_prefetch_radius",
            label = "Prefetch Radius",
            value = sliderValue,
            minValue = 0f,
            maxValue = SLIDER_MAX_VALUE,
            steps = SLIDER_STEPS,
            showValue = true,
            unitLabel = "items",
            enabled = !controlsDisabled,
            onChange =
                ActionRef(
                    id = "story_slider_change",
                    type = ActionTypes.SAVE_STATE,
                    payload = buildJsonObject { put("radius", sliderValue.roundToInt()) },
                ),
        )
    return checkboxAtom to sliderAtom
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AvatarShowcaseCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    val avatars = remember { buildShowcaseAvatars() }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Avatar Atoms (Initials & URL Slot)", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space24),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            avatars.forEach { (caption, atom) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                ) {
                    UiAvatar(atom = atom, onAction = onAction)
                    Text(
                        text = caption,
                        style = ThemeTokens.typography.bodySmall,
                        color = colors.text.secondary,
                    )
                }
            }
        }
    }
}

private fun buildShowcaseAvatars(): List<Pair<String, AvatarAtom>> =
    listOf(
        "Explicit Initials (AB)" to
            AvatarAtom(
                id = "avatar_ab",
                initials = "ab",
                sizeDp = AVATAR_LG_DP,
                contentDescription = "Abeta Builder",
                onClick = ActionRef(id = "avatar_ab_tap", type = ActionTypes.NAVIGATE),
            ),
        "Derived Initials (SR)" to
            AvatarAtom(
                id = "avatar_sr_derived",
                sizeDp = AVATAR_MD_DP,
                bgColor = ColorTokenRef("status.success_container"),
                textColor = ColorTokenRef("status.success"),
                contentDescription = "Sam Rivera",
                onClick = ActionRef(id = "avatar_sr_tap", type = ActionTypes.TRACK_EVENT),
            ),
        "Compact Initials (32dp)" to
            AvatarAtom(
                id = "avatar_compact",
                initials = "UX",
                sizeDp = AVATAR_SM_DP,
                bgColor = ColorTokenRef("status.info_container"),
                textColor = ColorTokenRef("status.info"),
                contentDescription = "UX Team",
            ),
        "URL Slot (KtorImageRenderer)" to
            AvatarAtom(
                id = "avatar_url_slot",
                url = "https://cdn.abeta.dev/avatars/sr.png",
                initials = "SR",
                sizeDp = AVATAR_LG_DP,
                bgColor = ColorTokenRef("brand.container"),
                textColor = ColorTokenRef("brand.on_container"),
                contentDescription = "Remote URL Avatar",
                onClick = ActionRef(id = "avatar_url_tap", type = ActionTypes.SHOW_TOAST),
            ),
    )

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SkeletonShowcaseCard() {
    val spacing = ThemeTokens.spacing
    val motion = ThemeTokens.motion
    val statusLabel =
        if (motion.reducedMotion) {
            "Paused · Reduced Motion Active"
        } else {
            "Shimmer Active (${motion.slowMs * SHIMMER_CYCLE_MULTIPLIER}ms)"
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Loading Skeletons", typography = TypographyTokenRef("title.large")))
            UiTag(
                TagAtom(
                    label = statusLabel,
                    variant = if (motion.reducedMotion) TagVariant.WARNING else TagVariant.SUCCESS,
                ),
            )
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            UiSkeleton(SkeletonAtom(kind = SkeletonKind.CIRCLE, widthDp = SKELETON_CIRCLE_DP))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                UiSkeleton(
                    SkeletonAtom(
                        kind = SkeletonKind.TEXT,
                        widthDp = SKELETON_TEXT_WIDTH_DP,
                        heightDp = SKELETON_TEXT_HEIGHT_DP,
                    ),
                )
                UiSkeleton(
                    SkeletonAtom(
                        kind = SkeletonKind.TEXT,
                        widthDp = SKELETON_SUBTITLE_WIDTH_DP,
                        heightDp = SKELETON_SUBTITLE_HEIGHT_DP,
                    ),
                )
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiSkeleton(SkeletonAtom(kind = SkeletonKind.RECTANGLE, heightDp = SKELETON_RECT_HEIGHT_DP))
    }
}
