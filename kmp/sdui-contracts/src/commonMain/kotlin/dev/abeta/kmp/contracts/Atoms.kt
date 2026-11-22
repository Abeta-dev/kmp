package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_MAX_STEPPER_QUANTITY = 20
private const val DEFAULT_COUNTDOWN_URGENT_SECONDS = 60

/**
 * Pure wire contract for a text UI atom.
 */
@Serializable
data class TextAtom(
    val id: String? = null,
    val text: String,
    val color: ColorTokenRef? = null,
    val typography: TypographyTokenRef? = null,
    @SerialName("max_lines")
    val maxLines: Int? = null,
    val alignment: AlignmentToken? = null,
    val markdown: Boolean = false,
    @SerialName("strike_through")
    val strikeThrough: Boolean = false,
    val underline: Boolean = false,
    @SerialName("prefix_icon")
    val prefixIcon: IconAtom? = null,
    @SerialName("suffix_icon")
    val suffixIcon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for an icon UI atom.
 */
@Serializable
data class IconAtom(
    val id: String? = null,
    val name: String,
    @SerialName("size_dp")
    val sizeDp: Int? = null,
    val color: ColorTokenRef? = null,
    @SerialName("content_description")
    val contentDescription: String? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a pill/badge tag UI atom.
 */
@Serializable
data class TagAtom(
    val id: String? = null,
    val label: String,
    val variant: TagVariant? = null,
    @SerialName("bg_color")
    val bgColor: ColorTokenRef? = null,
    @SerialName("text_color")
    val textColor: ColorTokenRef? = null,
    val border: BorderSpec? = null,
    val icon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

/**
 * Pure wire contract for an interactive button UI atom.
 */
@Serializable
data class ButtonAtom(
    val id: String? = null,
    val label: String,
    @SerialName("sub_label")
    val subLabel: String? = null,
    val variant: ButtonVariant = ButtonVariant.PRIMARY,
    val size: ButtonSize = ButtonSize.MEDIUM,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    @SerialName("full_width")
    val fullWidth: Boolean = false,
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("trailing_icon")
    val trailingIcon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val tracking: TrackingContract? = null,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

/**
 * Pure wire contract for a remote or placeholder image UI atom.
 */
@Serializable
data class ImageAtom(
    val id: String? = null,
    val url: String,
    @SerialName("content_description")
    val contentDescription: String? = null,
    @SerialName("width_dp")
    val widthDp: Int? = null,
    @SerialName("height_dp")
    val heightDp: Int? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: Float? = null,
    @SerialName("scale_mode")
    val scaleMode: ImageScaleMode = ImageScaleMode.CROP,
    @SerialName("corner_radius")
    val cornerRadius: CornerRadiusSpec? = null,
    @SerialName("placeholder_color")
    val placeholderColor: ColorTokenRef? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a text input UI atom.
 */
@Serializable
data class TextFieldAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val placeholder: String? = null,
    @SerialName("initial_value")
    val initialValue: String = "",
    @SerialName("helper_text")
    val helperText: String? = null,
    @SerialName("error_text")
    val errorText: String? = null,
    val enabled: Boolean = true,
    @SerialName("read_only")
    val readOnly: Boolean = false,
    @SerialName("single_line")
    val singleLine: Boolean = true,
    @SerialName("max_lines")
    val maxLines: Int? = null,
    @SerialName("max_length")
    val maxLength: Int? = null,
    val required: Boolean = false,
    val regex: String? = null,
    @SerialName("keyboard_type")
    val keyboardType: KeyboardKind = KeyboardKind.TEXT,
    @SerialName("prefix_icon")
    val prefixIcon: IconAtom? = null,
    @SerialName("suffix_icon")
    val suffixIcon: IconAtom? = null,
    @SerialName("on_value_change")
    val onValueChange: ActionRef? = null,
    @SerialName("on_submit")
    val onSubmit: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a horizontal divider UI atom.
 */
@Serializable
data class DividerAtom(
    val id: String? = null,
    @SerialName("thickness_dp")
    val thicknessDp: Float? = null,
    val color: ColorTokenRef? = null,
    @SerialName("inset_start_dp")
    val insetStartDp: Int? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a layout spacer UI atom.
 */
@Serializable
data class SpacerAtom(
    val id: String? = null,
    @SerialName("width_dp")
    val widthDp: Int? = null,
    @SerialName("height_dp")
    val heightDp: Int? = null,
    val weight: Float? = null,
)

/**
 * Pure wire contract for a linear or circular progress indicator UI atom.
 */
@Serializable
data class ProgressAtom(
    val id: String? = null,
    val kind: ProgressKind = ProgressKind.LINEAR,
    val progress: Float? = null,
    val label: String? = null,
    val color: ColorTokenRef? = null,
    @SerialName("track_color")
    val trackColor: ColorTokenRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a toggle switch UI atom.
 */
@Serializable
data class SwitchAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String,
    val subtitle: String? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a checkbox selection UI atom.
 */
@Serializable
data class CheckboxAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String,
    val subtitle: String? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val required: Boolean = false,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a range slider UI atom.
 */
@Serializable
data class SliderAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val value: Float = 0f,
    @SerialName("min_value")
    val minValue: Float = 0f,
    @SerialName("max_value")
    val maxValue: Float = 100f,
    val steps: Int = 0,
    @SerialName("show_value")
    val showValue: Boolean = true,
    @SerialName("unit_label")
    val unitLabel: String? = null,
    val enabled: Boolean = true,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a circular user or entity avatar UI atom.
 */
@Serializable
data class AvatarAtom(
    val id: String? = null,
    val url: String? = null,
    val initials: String? = null,
    @SerialName("size_dp")
    val sizeDp: Int? = null,
    @SerialName("bg_color")
    val bgColor: ColorTokenRef? = null,
    @SerialName("text_color")
    val textColor: ColorTokenRef? = null,
    @SerialName("content_description")
    val contentDescription: String? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a loading skeleton placeholder UI atom.
 */
@Serializable
data class SkeletonAtom(
    val id: String? = null,
    val kind: SkeletonKind = SkeletonKind.RECTANGLE,
    @SerialName("width_dp")
    val widthDp: Int? = null,
    @SerialName("height_dp")
    val heightDp: Int? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for an interactive quantity stepper UI atom.
 */
@Serializable
data class StepperAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val quantity: Int = 0,
    @SerialName("min_quantity")
    val minQuantity: Int = 0,
    @SerialName("max_quantity")
    val maxQuantity: Int = DEFAULT_MAX_STEPPER_QUANTITY,
    val enabled: Boolean = true,
    @SerialName("on_increment")
    val onIncrement: ActionRef? = null,
    @SerialName("on_decrement")
    val onDecrement: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a live ticking countdown timer UI atom.
 */
@Serializable
data class CountdownAtom(
    @SerialName("countdown_id")
    val countdownId: String,
    @SerialName("remaining_seconds")
    val remainingSeconds: Int,
    val label: String? = null,
    @SerialName("urgent_threshold_seconds")
    val urgentThresholdSeconds: Int = DEFAULT_COUNTDOWN_URGENT_SECONDS,
    @SerialName("show_hours")
    val showHours: Boolean = false,
    @SerialName("expired_text")
    val expiredText: String = "00:00",
    @SerialName("on_expire")
    val onExpire: ActionRef? = null,
    val motion: MotionSpec? = null,
)
