@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiImage
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.abs

private const val CARD_IMAGE_HEIGHT_DP = 220
private const val STAMP_TILT_DEG = 12f
private const val STAMP_BG_ALPHA = 0.88f
private const val PRIMARY_ACTION_BUTTON_DP = 56
private const val SECONDARY_ACTION_BUTTON_DP = 48
private const val ACTION_GLYPH_UNDO = "↺"
private const val ACTION_GLYPH_NOPE = "✕"
private const val ACTION_GLYPH_SUPER_LIKE = "★"
private const val ACTION_GLYPH_LIKE = "♥"

internal data class SwipeThresholds(
    val horizontalPx: Float,
    val verticalPx: Float,
    val horizontalExitPx: Float,
    val verticalExitPx: Float,
    val stampActivationPx: Float,
)

internal data class SwipeDeckCallbacks(
    val onSwipe: (SwipeDirection, Offset?) -> Unit,
    val onUndo: () -> Unit,
    val onReset: () -> Unit,
    val onAction: (ActionRef) -> Unit,
)

private data class SwipeButtonSpec(
    val glyph: String,
    val label: String,
    val accentColor: Color,
    val buttonSize: Dp,
    val enabled: Boolean,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SwipeCardContent(
    card: SwipeCardItem,
    interactive: Boolean,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val clickHandler = if (interactive) card.onClick?.let { action -> { onAction(action) } } else null
    UiSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("swipe_card_${card.cardId}"),
        onClick = clickHandler,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            card.image?.let { image ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    UiImage(
                        atom = image,
                        modifier = Modifier.fillMaxWidth().height(CARD_IMAGE_HEIGHT_DP.dp),
                        onAction = onAction,
                    )
                    card.badge?.let { badge ->
                        UiTag(
                            atom = badge,
                            modifier = Modifier.align(Alignment.TopStart).padding(spacing.space8),
                            onAction = onAction,
                        )
                    }
                }
            } ?: card.badge?.let { badge -> UiTag(atom = badge, onAction = onAction) }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { UiText(atom = card.title, onAction = onAction) }
                    card.distanceText?.let { distance -> UiText(atom = distance, onAction = onAction) }
                }
                card.subtitle?.let { subtitle -> UiText(atom = subtitle, onAction = onAction) }
                card.bio?.let { bio -> UiText(atom = bio, onAction = onAction) }
            }

            if (card.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    card.tags.forEach { tag -> UiTag(atom = tag, onAction = onAction) }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SwipeStampOverlays(
    model: SwipeDeckSnippetData,
    offsetProvider: () -> Offset,
    thresholds: SwipeThresholds,
    reducedMotion: Boolean,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val horizontalSpan = (thresholds.horizontalPx - thresholds.stampActivationPx).coerceAtLeast(1f)
    val verticalSpan = (thresholds.verticalPx - thresholds.stampActivationPx).coerceAtLeast(1f)

    Box(modifier = Modifier.fillMaxWidth().padding(spacing.space16)) {
        SwipeStampBadge(
            label = model.likeLabel,
            accentColor = colors.status.success,
            backgroundColor = colors.status.successSubtle,
            alphaProvider = {
                ((offsetProvider().x - thresholds.stampActivationPx) / horizontalSpan).coerceIn(0f, 1f)
            },
            rotationDegrees = if (reducedMotion) 0f else -STAMP_TILT_DEG,
            modifier = Modifier.align(Alignment.TopStart),
        )
        SwipeStampBadge(
            label = model.nopeLabel,
            accentColor = colors.status.error,
            backgroundColor = colors.status.errorSubtle,
            alphaProvider = {
                ((-offsetProvider().x - thresholds.stampActivationPx) / horizontalSpan).coerceIn(0f, 1f)
            },
            rotationDegrees = if (reducedMotion) 0f else STAMP_TILT_DEG,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        SwipeStampBadge(
            label = model.superLikeLabel,
            accentColor = colors.status.info,
            backgroundColor = colors.status.infoSubtle,
            alphaProvider = {
                val offset = offsetProvider()
                if (abs(offset.y) > abs(offset.x)) {
                    ((-offset.y - thresholds.stampActivationPx) / verticalSpan).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            rotationDegrees = 0f,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SwipeStampBadge(
    label: String,
    accentColor: Color,
    backgroundColor: Color,
    alphaProvider: () -> Float,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    this.alpha = alphaProvider()
                    rotationZ = rotationDegrees
                }.clip(shape)
                .background(backgroundColor.copy(alpha = STAMP_BG_ALPHA), shape)
                .border(ThemeTokens.strokeWidth.thick, accentColor, shape)
                .padding(horizontal = spacing.space12, vertical = spacing.space4),
    ) {
        Text(
            text = label,
            style = ThemeTokens.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SwipeDeckEmptyCard(
    model: SwipeDeckSnippetData,
    canReset: Boolean,
    onReset: () -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space24),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            model.emptyTitle?.let { title -> UiText(atom = title, onAction = onAction) }
                ?: Text(text = "No more cards", style = typography.titleMedium, color = colors.text.primary)
            model.emptyMessage?.let { message -> UiText(atom = message, onAction = onAction) }
            if (canReset) {
                OutlinedButton(onClick = onReset) {
                    Text(text = "Reset Deck", style = typography.labelLarge, color = colors.brand.primary)
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SwipeActionBar(
    canUndo: Boolean,
    canSwipe: Boolean,
    callbacks: SwipeDeckCallbacks,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val buttons =
        listOf(
            SwipeButtonSpec(
                glyph = ACTION_GLYPH_UNDO,
                label = "Undo",
                accentColor = colors.status.warning,
                buttonSize = SECONDARY_ACTION_BUTTON_DP.dp,
                enabled = canUndo,
                testTag = "swipe_btn_undo",
                onClick = callbacks.onUndo,
            ),
            SwipeButtonSpec(
                glyph = ACTION_GLYPH_NOPE,
                label = "Nope",
                accentColor = colors.status.error,
                buttonSize = PRIMARY_ACTION_BUTTON_DP.dp,
                enabled = canSwipe,
                testTag = "swipe_btn_nope",
                onClick = { callbacks.onSwipe(SwipeDirection.NOPE, null) },
            ),
            SwipeButtonSpec(
                glyph = ACTION_GLYPH_SUPER_LIKE,
                label = "Super Like",
                accentColor = colors.status.info,
                buttonSize = SECONDARY_ACTION_BUTTON_DP.dp,
                enabled = canSwipe,
                testTag = "swipe_btn_super_like",
                onClick = { callbacks.onSwipe(SwipeDirection.SUPER_LIKE, null) },
            ),
            SwipeButtonSpec(
                glyph = ACTION_GLYPH_LIKE,
                label = "Like",
                accentColor = colors.status.success,
                buttonSize = PRIMARY_ACTION_BUTTON_DP.dp,
                enabled = canSwipe,
                testTag = "swipe_btn_like",
                onClick = { callbacks.onSwipe(SwipeDirection.LIKE, null) },
            ),
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.space16, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { spec -> CircularSwipeButton(spec) }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CircularSwipeButton(spec: SwipeButtonSpec) {
    val colors = ThemeTokens.colors
    val disabledAlpha = ThemeTokens.opacity.disabled
    val resolvedColor = if (spec.enabled) spec.accentColor else colors.text.disabled
    Box(
        modifier =
            Modifier
                .size(spec.buttonSize)
                .clip(CircleShape)
                .background(colors.surface.elevated, CircleShape)
                .border(
                    width = ThemeTokens.strokeWidth.medium,
                    color = if (spec.enabled) spec.accentColor else colors.border.subtle,
                    shape = CircleShape,
                ).graphicsLayer { alpha = if (spec.enabled) 1f else disabledAlpha }
                .clickable(enabled = spec.enabled, role = Role.Button, onClick = spec.onClick)
                .semantics { contentDescription = spec.label }
                .testTag(spec.testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = spec.glyph,
            style = ThemeTokens.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = resolvedColor,
        )
    }
}
