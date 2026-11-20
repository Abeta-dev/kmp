package dev.abeta.kmp.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale tokens on a 4dp baseline grid with BFF-aligned semantic aliases.
 */
@Immutable
data class SpacingTokens(
    val space0: Dp,
    val space2: Dp,
    val space4: Dp,
    val space8: Dp,
    val space12: Dp,
    val space16: Dp,
    val space20: Dp,
    val space24: Dp,
    val space32: Dp,
    val space40: Dp,
    val space48: Dp,
    val space56: Dp,
    val space64: Dp,
) {
    val none: Dp get() = space0
    val xxs: Dp get() = space2
    val xs: Dp get() = space4
    val sm: Dp get() = space8
    val md: Dp get() = space12
    val lg: Dp get() = space16
    val xl: Dp get() = space24
    val xxl: Dp get() = space32
    val xxxl: Dp get() = space48
}

/**
 * Default spacing scale tokens.
 */
@Suppress("MagicNumber")
val DefaultSpacingTokens =
    SpacingTokens(
        space0 = 0.dp,
        space2 = 2.dp,
        space4 = 4.dp,
        space8 = 8.dp,
        space12 = 12.dp,
        space16 = 16.dp,
        space20 = 20.dp,
        space24 = 24.dp,
        space32 = 32.dp,
        space40 = 40.dp,
        space48 = 48.dp,
        space56 = 56.dp,
        space64 = 64.dp,
    )
