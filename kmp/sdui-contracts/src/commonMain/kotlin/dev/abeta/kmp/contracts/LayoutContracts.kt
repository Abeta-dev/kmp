package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directional spacing configuration in density-independent pixels (dp).
 */
@Serializable
data class SpacingValues(
    val top: Int? = null,
    val bottom: Int? = null,
    val start: Int? = null,
    val end: Int? = null,
    val all: Int? = null,
    val horizontal: Int? = null,
    val vertical: Int? = null,
)

/**
 * Corner rounding specification via semantic token name or explicit dp values.
 */
@Serializable
data class CornerRadiusSpec(
    val token: String? = null,
    val all: Int? = null,
    @SerialName("top_start")
    val topStart: Int? = null,
    @SerialName("top_end")
    val topEnd: Int? = null,
    @SerialName("bottom_start")
    val bottomStart: Int? = null,
    @SerialName("bottom_end")
    val bottomEnd: Int? = null,
)

/**
 * Border stroke specification via explicit dp width or semantic stroke token.
 */
@Serializable
data class BorderSpec(
    @SerialName("width_dp")
    val widthDp: Float? = null,
    @SerialName("width_token")
    val widthToken: String? = null,
    val color: ColorTokenRef? = null,
)

/**
 * Common box layout styling attributes shared across atoms and snippets.
 */
@Serializable
data class LayoutConfig(
    val padding: SpacingValues? = null,
    val margin: SpacingValues? = null,
    @SerialName("bg_color")
    val bgColor: ColorTokenRef? = null,
    @SerialName("corner_radius")
    val cornerRadius: CornerRadiusSpec? = null,
    val border: BorderSpec? = null,
    @SerialName("fill_max_width")
    val fillMaxWidth: Boolean = false,
)
