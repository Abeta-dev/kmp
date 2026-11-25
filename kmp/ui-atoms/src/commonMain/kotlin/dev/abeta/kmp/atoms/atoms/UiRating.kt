package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.RatingAtom
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_STARS = 1
private const val MAX_STARS = 10
private const val MIN_STAR_SIZE_DP = 12
private const val MAX_STAR_SIZE_DP = 48
private const val FULL_STAR_THRESHOLD = 0.75f
private const val HALF_STAR_THRESHOLD = 0.25f
private const val HALF_STAR_FILL = 0.5f
private const val FULL_STAR_FILL = 1f
private const val EMPTY_STAR_FILL = 0f
private const val TENTHS_MULTIPLIER = 10
private const val STAR_POINTS = 5
private const val STAR_VERTICES = STAR_POINTS * 2
private const val STAR_INNER_RADIUS_RATIO = 0.42f
private const val START_ANGLE_RAD = -PI / 2.0
private const val HALF_DIVISOR = 2f
private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Pure helper mapping a continuous [rating] and 0-based [starIndex] to `0f`, `0.5f`, or `1f`.
 */
fun resolveStarFillLevel(
    rating: Float,
    starIndex: Int,
): Float {
    val delta = (rating - starIndex).coerceIn(EMPTY_STAR_FILL, FULL_STAR_FILL)
    return when {
        delta >= FULL_STAR_THRESHOLD -> FULL_STAR_FILL
        delta >= HALF_STAR_THRESHOLD -> HALF_STAR_FILL
        else -> EMPTY_STAR_FILL
    }
}

/**
 * Pure multiplatform helper formatting a rating summary such as `"4.8 (124)"` or `"4.5"`.
 */
fun formatRatingSummary(
    value: Float,
    reviewCount: Int?,
): String {
    val tenths = (value.coerceAtLeast(EMPTY_STAR_FILL) * TENTHS_MULTIPLIER).roundToInt()
    val whole = tenths / TENTHS_MULTIPLIER
    val fraction = tenths % TENTHS_MULTIPLIER
    val score = "$whole.$fraction"
    return if (reviewCount != null) "$score ($reviewCount)" else score
}

/**
 * Stateless display or interactive star rating UI atom rendered from a [RatingAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiRating(
    atom: RatingAtom,
    modifier: Modifier = Modifier,
    ratingOverride: Float? = null,
    onRate: (Float) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val haptic = LocalUiHapticFeedback.current

    val maxStars = atom.maxStars.coerceIn(MIN_STARS, MAX_STARS)
    val currentRating = (ratingOverride ?: atom.value).coerceIn(EMPTY_STAR_FILL, maxStars.toFloat())
    val starSize = atom.starSizeDp.coerceIn(MIN_STAR_SIZE_DP, MAX_STAR_SIZE_DP).dp
    val ratingKey = atom.id ?: atom.fieldId ?: "default"
    val showSummary = currentRating > EMPTY_STAR_FILL || atom.reviewCount != null

    Row(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .testTag("rating_$ratingKey"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        atom.label?.takeIf { it.isNotBlank() }?.let { labelText ->
            Text(
                text = labelText,
                style = typography.labelMedium,
                color = colors.text.secondary,
            )
        }
        UiStarsRow(
            ratingKey = ratingKey,
            currentRating = currentRating,
            maxStars = maxStars,
            starSize = starSize,
            interactive = atom.interactive,
            onStarTap = { starValue ->
                haptic(UiHapticKind.CLICK)
                onRate(starValue)
                atom.onRate?.let(onAction)
            },
        )
        if (showSummary) {
            Text(
                text = formatRatingSummary(currentRating, atom.reviewCount),
                style =
                    typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
                    ),
                color = colors.text.secondary,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiStarsRow(
    ratingKey: String,
    currentRating: Float,
    maxStars: Int,
    starSize: Dp,
    interactive: Boolean,
    onStarTap: (Float) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val sizing = ThemeTokens.sizing
    val filledColor = colors.status.warning
    val emptyColor = colors.border.default

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (interactive) spacing.space0 else spacing.space2),
    ) {
        repeat(maxStars) { index ->
            val fillLevel = resolveStarFillLevel(currentRating, index)
            val starNumber = index + 1
            val starModifier =
                if (interactive) {
                    Modifier
                        .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                        .minimumInteractiveComponentSize()
                        .testTag("rating_star_${ratingKey}_$starNumber")
                        .pressBounce()
                        .clickable(
                            role = Role.Button,
                            onClick = { onStarTap(starNumber.toFloat()) },
                        ).semantics {
                            contentDescription = "Rate $starNumber of $maxStars"
                        }.padding(spacing.space2)
                } else {
                    Modifier
                }
            Box(modifier = starModifier, contentAlignment = Alignment.Center) {
                StarVectorGlyph(
                    fillLevel = fillLevel,
                    filledColor = filledColor,
                    emptyColor = emptyColor,
                    size = starSize,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StarVectorGlyph(
    fillLevel: Float,
    filledColor: Color,
    emptyColor: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val starPath = buildStarPath(width = this.size.width, height = this.size.height)
        drawPath(path = starPath, color = emptyColor)
        when {
            fillLevel >= FULL_STAR_FILL -> {
                drawPath(path = starPath, color = filledColor)
            }

            fillLevel >= HALF_STAR_FILL -> {
                clipRect(right = this.size.width / HALF_DIVISOR) {
                    drawPath(path = starPath, color = filledColor)
                }
            }
        }
    }
}

private fun buildStarPath(
    width: Float,
    height: Float,
): Path {
    val centerX = width / HALF_DIVISOR
    val centerY = height / HALF_DIVISOR
    val outerRadius = minOf(width, height) / HALF_DIVISOR
    val innerRadius = outerRadius * STAR_INNER_RADIUS_RATIO
    val stepAngle = PI / STAR_POINTS

    return Path().apply {
        for (vertex in 0 until STAR_VERTICES) {
            val radius = if (vertex % 2 == 0) outerRadius else innerRadius
            val angle = START_ANGLE_RAD + vertex * stepAngle
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            if (vertex == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        close()
    }
}
