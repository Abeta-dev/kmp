@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_OTP_LENGTH = 6
private const val DEFAULT_MAX_STARS = 5
private const val DEFAULT_STAR_SIZE_DP = 20
private const val DEFAULT_BADGE_MAX_COUNT = 99
private const val DEFAULT_QR_CODE_SIZE_DP = 160
private const val DEFAULT_MEDIA_PAGER_HEIGHT_DP = 200

/**
 * Reusable selectable option item shared by [RadioGroupAtom], [SelectAtom], and [SegmentedControlAtom].
 */
@Serializable
data class OptionItem(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val badge: String? = null,
)

/**
 * Pure wire contract for a single-choice radio group UI atom.
 */
@Serializable
data class RadioGroupAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val options: List<OptionItem> = emptyList(),
    @SerialName("selected_id")
    val selectedId: String? = null,
    val enabled: Boolean = true,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a dropdown select menu UI atom.
 */
@Serializable
data class SelectAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val placeholder: String = "Select an option",
    val options: List<OptionItem> = emptyList(),
    @SerialName("selected_id")
    val selectedId: String? = null,
    @SerialName("helper_text")
    val helperText: String? = null,
    @SerialName("error_text")
    val errorText: String? = null,
    val enabled: Boolean = true,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a fixed-length OTP or PIN verification code input UI atom.
 */
@Serializable
data class OtpInputAtom(
    @SerialName("field_id")
    val fieldId: String,
    val label: String? = null,
    val length: Int = DEFAULT_OTP_LENGTH,
    @SerialName("initial_value")
    val initialValue: String = "",
    val masked: Boolean = false,
    @SerialName("helper_text")
    val helperText: String? = null,
    @SerialName("error_text")
    val errorText: String? = null,
    val enabled: Boolean = true,
    @SerialName("on_complete")
    val onComplete: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for an interactive standalone filter or removable chip UI atom.
 */
@Serializable
data class ChipAtom(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val removable: Boolean = false,
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    @SerialName("on_remove")
    val onRemove: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a multi-option segmented control UI atom.
 */
@Serializable
data class SegmentedControlAtom(
    @SerialName("field_id")
    val fieldId: String,
    val options: List<OptionItem> = emptyList(),
    @SerialName("selected_id")
    val selectedId: String? = null,
    val enabled: Boolean = true,
    @SerialName("on_change")
    val onChange: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a display or interactive star rating UI atom.
 */
@Serializable
data class RatingAtom(
    val id: String? = null,
    @SerialName("field_id")
    val fieldId: String? = null,
    val value: Float = 0f,
    @SerialName("max_stars")
    val maxStars: Int = DEFAULT_MAX_STARS,
    val interactive: Boolean = false,
    @SerialName("review_count")
    val reviewCount: Int? = null,
    val label: String? = null,
    @SerialName("star_size_dp")
    val starSizeDp: Int = DEFAULT_STAR_SIZE_DP,
    @SerialName("on_rate")
    val onRate: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a commerce price display UI atom with optional MRP and discount pill.
 */
@Serializable
data class PriceAtom(
    val id: String? = null,
    @SerialName("amount_text")
    val amountText: String,
    @SerialName("original_amount_text")
    val originalAmountText: String? = null,
    @SerialName("discount_label")
    val discountLabel: String? = null,
    @SerialName("unit_suffix")
    val unitSuffix: String? = null,
    val emphasized: Boolean = true,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a compact notification badge or live dot indicator UI atom.
 */
@Serializable
data class BadgeAtom(
    val id: String? = null,
    val count: Int? = null,
    @SerialName("max_count")
    val maxCount: Int = DEFAULT_BADGE_MAX_COUNT,
    val label: String? = null,
    @SerialName("dot_only")
    val dotOnly: Boolean = false,
    val variant: TagVariant = TagVariant.ERROR,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

/**
 * Pure wire contract for an inline semantic alert banner UI atom.
 */
@Serializable
data class AlertBannerAtom(
    val id: String? = null,
    val title: String,
    val message: String? = null,
    val variant: TagVariant = TagVariant.INFO,
    val icon: IconAtom? = null,
    @SerialName("action_label")
    val actionLabel: String? = null,
    @SerialName("on_action")
    val onAction: ActionRef? = null,
    val dismissible: Boolean = false,
    @SerialName("on_dismiss")
    val onDismiss: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a KPI / metric summary tile UI atom.
 */
@Serializable
data class StatTileAtom(
    val id: String? = null,
    val label: String,
    val value: String,
    @SerialName("delta_label")
    val deltaLabel: String? = null,
    @SerialName("delta_positive")
    val deltaPositive: Boolean? = null,
    val caption: String? = null,
    val icon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a structured receipt, bill, or specification key-value row UI atom.
 */
@Serializable
data class KeyValueRowAtom(
    val id: String? = null,
    val label: String,
    val value: String,
    val sublabel: String? = null,
    @SerialName("value_color")
    val valueColor: ColorTokenRef? = null,
    val emphasized: Boolean = false,
    @SerialName("show_divider")
    val showDivider: Boolean = false,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a deterministic matrix QR code display UI atom.
 */
@Serializable
data class QrCodeAtom(
    val id: String? = null,
    val payload: String,
    val label: String? = null,
    val caption: String? = null,
    @SerialName("size_dp")
    val sizeDp: Int = DEFAULT_QR_CODE_SIZE_DP,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Pure wire contract for a swipeable horizontal media pager UI atom.
 */
@Serializable
data class MediaPagerAtom(
    val id: String? = null,
    val items: List<ImageAtom> = emptyList(),
    @SerialName("height_dp")
    val heightDp: Int = DEFAULT_MEDIA_PAGER_HEIGHT_DP,
    @SerialName("auto_advance_ms")
    val autoAdvanceMs: Int? = null,
    @SerialName("show_indicators")
    val showIndicators: Boolean = true,
    @SerialName("corner_radius")
    val cornerRadius: CornerRadiusSpec? = null,
    @SerialName("on_page_click")
    val onPageClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)
