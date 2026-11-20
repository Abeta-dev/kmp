package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire reference to a semantic color token (e.g. `"text.primary"`) with optional alpha override,
 * fallback hex literal, or direct hex color literal (e.g. `"#4F46E5"`).
 */
@Serializable
data class ColorTokenRef(
    val token: String? = null,
    val alpha: Float? = null,
    @SerialName("fallback_hex")
    val fallbackHex: String? = null,
    val hex: String? = null,
)

/**
 * Wire reference to a semantic typography scale token (e.g. `"title.large"`, `"body.medium"`)
 * with optional font weight and letter spacing overrides.
 */
@Serializable
data class TypographyTokenRef(
    val token: String,
    @SerialName("font_weight")
    val fontWeight: String? = null,
    @SerialName("letter_spacing")
    val letterSpacing: Double? = null,
)
