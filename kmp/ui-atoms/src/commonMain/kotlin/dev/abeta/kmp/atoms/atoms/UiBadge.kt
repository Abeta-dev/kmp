package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.BadgeAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_BADGE_MAX_COUNT = 99
private const val BADGE_DOT_SIZE_DP = 8
private const val BADGE_MIN_PILL_HEIGHT_DP = 20
private const val BADGE_BORDER_ALPHA = 0.28f
private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Pure helper formatting a badge counter, capping values above [maxCount] as `"$maxCount+"`.
 */
fun formatBadgeCount(
    count: Int,
    maxCount: Int = DEFAULT_BADGE_MAX_COUNT,
): String {
    val safeCount = count.coerceAtLeast(0)
    val safeMax = maxCount.coerceAtLeast(1)
    return if (safeCount > safeMax) "$safeMax+" else safeCount.toString()
}

/**
 * Stateless compact badge / live dot indicator UI atom rendered from a [BadgeAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiBadge(
    atom: BadgeAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    val resolvedColors =
        resolveTagColors(
            variant = atom.variant,
            bgColor = null,
            textColor = null,
            colors = colors,
        )
    val badgeKey = atom.id ?: "default"
    val badgeText =
        atom.label?.takeIf { it.isNotBlank() }
            ?: atom.count?.let { formatBadgeCount(it, atom.maxCount) }
    val baseModifier =
        modifier
            .applyMotionSpec(motion = atom.motion, motionKey = "badge_$badgeKey")
            .applyLayoutConfig(atom.layout)
            .testTag("badge_$badgeKey")

    if (atom.dotOnly || badgeText == null) {
        Box(
            modifier =
                baseModifier
                    .size(BADGE_DOT_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(resolvedColors.foreground, CircleShape)
                    .semantics {
                        contentDescription = atom.label ?: "Live indicator"
                    },
        )
        return
    }

    val pillShape = RoundedCornerShape(cornerRadius.full)
    Box(
        modifier =
            baseModifier
                .defaultMinSize(minWidth = BADGE_MIN_PILL_HEIGHT_DP.dp, minHeight = BADGE_MIN_PILL_HEIGHT_DP.dp)
                .clip(pillShape)
                .background(resolvedColors.background, pillShape)
                .border(strokeWidth.thin, resolvedColors.foreground.copy(alpha = BADGE_BORDER_ALPHA), pillShape)
                .padding(horizontal = spacing.space8, vertical = spacing.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeText,
            style =
                typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
                ),
            color = resolvedColors.foreground,
            maxLines = 1,
        )
    }
}
