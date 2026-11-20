package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
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
import dev.abeta.kmp.atoms.atoms.UiBuiltinIcons
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiImage
import dev.abeta.kmp.atoms.atoms.UiProgress
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.atoms.UiTextField
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_PROGRESS = 0.65f
private const val SAMPLE_IMAGE_WIDTH_DP = 240
private const val SAMPLE_IMAGE_HEIGHT_DP = 110
private const val TEXT_FIELD_MAX_LENGTH = 36

/**
 * Interactive [UiButton] story with variant, size, enabled, loading, and full-width controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ButtonStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var variant by remember { mutableStateOf(ButtonVariant.PRIMARY) }
    var size by remember { mutableStateOf(ButtonSize.MEDIUM) }
    var enabled by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var fullWidth by remember { mutableStateOf(false) }
    val activeAtom =
        remember(variant, size, enabled, loading, fullWidth) {
            ButtonAtom(
                id = "story_button",
                label = "Deploy Release",
                subLabel = "v0.1.0 BOM",
                variant = variant,
                size = size,
                enabled = enabled,
                loading = loading,
                fullWidth = fullWidth,
                leadingIcon = IconAtom(name = "star"),
                onClick = ActionRef(id = "story_button_click", type = ActionTypes.SHOW_TOAST),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiText(TextAtom(text = "Interactive Preview", typography = TypographyTokenRef("title.large")))
            UiSpacer(SpacerAtom(heightDp = 12))
            UiButton(atom = activeAtom, onAction = onAction)
        }
        ButtonControlsCard(
            variant = variant,
            size = size,
            enabled = enabled,
            loading = loading,
            fullWidth = fullWidth,
            onVariantChange = { variant = it },
            onSizeChange = { size = it },
            onEnabledChange = { enabled = it },
            onLoadingChange = { loading = it },
            onFullWidthChange = { fullWidth = it },
        )
        ButtonVariantMatrixCard(size = size, onAction = onAction)
        JsonContractPreviewCard(
            title = "ButtonAtom Wire JSON",
            jsonText = SduiJson.encodeToString(ButtonAtom.serializer(), activeAtom),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ButtonVariantMatrixCard(
    size: ButtonSize,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "All Button Variants Matrix", typography = TypographyTokenRef("title.medium")))
        UiSpacer(SpacerAtom(heightDp = 8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            ButtonVariant.entries.forEach { v ->
                UiButton(
                    atom =
                        ButtonAtom(
                            label = v.name,
                            variant = v,
                            size = size,
                            onClick = ActionRef(id = "matrix_${v.name}", type = ActionTypes.TRACK_EVENT),
                        ),
                    onAction = onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun ButtonControlsCard(
    variant: ButtonVariant,
    size: ButtonSize,
    enabled: Boolean,
    loading: Boolean,
    fullWidth: Boolean,
    onVariantChange: (ButtonVariant) -> Unit,
    onSizeChange: (ButtonSize) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onFullWidthChange: (Boolean) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Controls", typography = TypographyTokenRef("title.medium")))
        UiSpacer(SpacerAtom(heightDp = 8))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
            ButtonVariant.entries.forEach { v ->
                FilterChip(selected = variant == v, onClick = { onVariantChange(v) }, label = { Text(v.name) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
            ButtonSize.entries.forEach { s ->
                FilterChip(selected = size == s, onClick = { onSizeChange(s) }, label = { Text(s.name) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledSwitch(label = "Enabled", checked = enabled, onCheckedChange = onEnabledChange)
            LabeledSwitch(label = "Loading", checked = loading, onCheckedChange = onLoadingChange)
            LabeledSwitch(label = "Full Width", checked = fullWidth, onCheckedChange = onFullWidthChange)
        }
    }
}

/**
 * Interactive [UiText] and [UiTag] showcase across variants and text styles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun TypographyAndTagStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var underline by remember { mutableStateOf(false) }
    var strikeThrough by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiText(TextAtom(text = "Text Atom & Decorations", typography = TypographyTokenRef("title.large")))
            UiSpacer(SpacerAtom(heightDp = 8))
            UiText(
                atom =
                    TextAtom(
                        text = "Interactive text atom with prefix check icon",
                        typography = TypographyTokenRef("title.medium"),
                        color = ColorTokenRef("text.primary"),
                        prefixIcon = IconAtom(name = "check"),
                        underline = underline,
                        strikeThrough = strikeThrough,
                        onClick = ActionRef(id = "text_tap", type = ActionTypes.TRACK_EVENT),
                    ),
                onAction = onAction,
            )
            UiSpacer(SpacerAtom(heightDp = 8))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space16)) {
                LabeledSwitch("Underline", underline) { underline = it }
                LabeledSwitch("Strike Through", strikeThrough) { strikeThrough = it }
            }
        }

        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiText(TextAtom(text = "Semantic Tag Variants", typography = TypographyTokenRef("title.large")))
            UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                TagVariant.entries.forEach { tone ->
                    UiTag(
                        atom =
                            TagAtom(
                                label = tone.name,
                                variant = tone,
                                icon = IconAtom(name = "info"),
                                onClick = ActionRef(id = "tag_${tone.name}", type = ActionTypes.SHOW_TOAST),
                            ),
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

/**
 * Interactive [UiTextField] showcase with error toggle, keyboard kind selector, and character limits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun TextFieldStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var textValue by remember { mutableStateOf("builder@abeta.dev") }
    var showError by remember { mutableStateOf(false) }
    var keyboardKind by remember { mutableStateOf(KeyboardKind.EMAIL) }

    val atom =
        TextFieldAtom(
            fieldId = "storybook_field",
            label = "Workspace Contact",
            placeholder = "Enter email, phone, or secret...",
            helperText = "Max $TEXT_FIELD_MAX_LENGTH characters · Mode: ${keyboardKind.name}",
            errorText = if (showError) "Validation failed: invalid format for ${keyboardKind.name}" else null,
            keyboardType = keyboardKind,
            maxLength = TEXT_FIELD_MAX_LENGTH,
            prefixIcon = IconAtom(name = "search"),
            onSubmit = ActionRef(id = "field_submit", type = ActionTypes.CALL_API),
        )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiTextField(
                atom = atom,
                valueOverride = textValue,
                onValueChange = { textValue = it },
                onAction = onAction,
            )
            UiSpacer(SpacerAtom(heightDp = 8))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledSwitch("Simulate Error", showError) { showError = it }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                val modes = listOf(KeyboardKind.TEXT, KeyboardKind.EMAIL, KeyboardKind.PHONE, KeyboardKind.PASSWORD)
                modes.forEach { kind ->
                    FilterChip(
                        selected = keyboardKind == kind,
                        onClick = { keyboardKind = kind },
                        label = { Text(kind.name) },
                    )
                }
            }
        }
    }
}

/**
 * Showcase for [UiProgress], [UiIcon], [UiImage], [UiDivider], [UiSpacer], and [UiSurfaceCard].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MediaFeedbackStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var progress by remember { mutableFloatStateOf(DEFAULT_PROGRESS) }
    var indeterminate by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            UiText(TextAtom(text = "Progress Indicators", typography = TypographyTokenRef("title.large")))
            UiSpacer(SpacerAtom(heightDp = 8))
            UiProgress(
                ProgressAtom(
                    kind = ProgressKind.LINEAR,
                    progress = if (indeterminate) null else progress,
                    label = if (indeterminate) "Syncing..." else "${(progress * 100).toInt()}% Complete",
                ),
            )
            UiSpacer(SpacerAtom(heightDp = 12))
            UiProgress(
                ProgressAtom(
                    kind = ProgressKind.CIRCULAR,
                    progress = if (indeterminate) null else progress,
                    label = "Circular SLO Gauge",
                ),
            )
            Slider(value = progress, onValueChange = { progress = it }, enabled = !indeterminate)
            LabeledSwitch("Indeterminate", indeterminate) { indeterminate = it }
        }
        IconGalleryCard(onAction = onAction)
        SampleImageCard(onAction = onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun IconGalleryCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Built-In Vector Icons Gallery", typography = TypographyTokenRef("title.medium")))
        UiSpacer(SpacerAtom(heightDp = 8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            for (iconName in UiBuiltinIcons.names()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    UiIcon(
                        atom = IconAtom(name = iconName, color = ColorTokenRef("icon.brand")),
                        onAction = onAction,
                    )
                    Text(
                        text = iconName,
                        style = ThemeTokens.typography.caption,
                        color = ThemeTokens.colors.text.secondary,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SampleImageCard(onAction: (ActionRef) -> Unit) {
    val previewDesc = "Host ImageRenderer slot ($SAMPLE_IMAGE_WIDTH_DP x $SAMPLE_IMAGE_HEIGHT_DP dp)"
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Placeholder Image Atom", typography = TypographyTokenRef("title.medium")))
        UiSpacer(SpacerAtom(heightDp = 8))
        UiImage(
            atom =
                ImageAtom(
                    url = "https://abeta.dev/assets/architecture.png",
                    contentDescription = previewDesc,
                    widthDp = SAMPLE_IMAGE_WIDTH_DP,
                    heightDp = SAMPLE_IMAGE_HEIGHT_DP,
                    scaleMode = ImageScaleMode.CROP,
                    cornerRadius = CornerRadiusSpec(token = "radius.md"),
                    onClick = ActionRef(id = "image_tap", type = ActionTypes.NAVIGATE),
                ),
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun LabeledSwitch(
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

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun JsonContractPreviewCard(
    title: String,
    jsonText: String,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = title, typography = TypographyTokenRef("label.large")))
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        Text(
            text = jsonText,
            style = ThemeTokens.typography.caption.copy(fontFamily = ThemeTokens.typography.fonts.mono),
            color = ThemeTokens.colors.text.secondary,
        )
    }
}
