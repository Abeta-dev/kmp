package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val TIME_PAD_DIGITS = 2
private const val TICK_INTERVAL_MS = 1000L
private const val MIN_TICK_DELAY_MS = 50L
private const val BRAND_BORDER_ALPHA = 0.30f

private data class ResolvedCountdownColors(
    val background: Color,
    val foreground: Color,
    val border: Color,
)

/**
 * Pure helper formatting remaining [seconds] into `"MM:SS"` or `"HH:MM:SS"`, returning
 * [expiredText] when [seconds] is zero or negative.
 */
fun formatCountdownSeconds(
    seconds: Int,
    showHours: Boolean = false,
    expiredText: String = "00:00",
): String {
    if (seconds <= 0) {
        return expiredText
    }
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    val minPart = minutes.toString().padStart(TIME_PAD_DIGITS, '0')
    val secPart = secs.toString().padStart(TIME_PAD_DIGITS, '0')
    return if (showHours || hours > 0) {
        val hourPart = hours.toString().padStart(TIME_PAD_DIGITS, '0')
        "$hourPart:$minPart:$secPart"
    } else {
        "$minPart:$secPart"
    }
}

/**
 * Pure helper determining whether a live countdown is inside its urgent warning window.
 */
fun isCountdownUrgent(
    remainingSeconds: Int,
    urgentThresholdSeconds: Int,
): Boolean = remainingSeconds > 0 && remainingSeconds <= urgentThresholdSeconds.coerceAtLeast(0)

/**
 * Pure helper computing whole remaining seconds anchored to [expiresAtEpochMs] relative to
 * [nowEpochMs], falling back to [fallbackRemainingSeconds] when [expiresAtEpochMs] is `null`.
 */
fun resolveCountdownRemainingSeconds(
    fallbackRemainingSeconds: Int,
    expiresAtEpochMs: Long?,
    nowEpochMs: Long,
): Int =
    if (expiresAtEpochMs == null) {
        fallbackRemainingSeconds.coerceAtLeast(0)
    } else {
        val remainingMs = expiresAtEpochMs - nowEpochMs
        if (remainingMs <= 0L) {
            0
        } else {
            ((remainingMs + TICK_INTERVAL_MS - 1L) / TICK_INTERVAL_MS)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

@OptIn(ExperimentalTime::class)
private fun currentSystemEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Live ticking countdown pill UI atom rendered from a [CountdownAtom] contract.
 *
 * Uses [UiRollingCounter] for crisp digit transitions, switches to danger styling with a
 * [MotionLoopEffect.HEARTBEAT] pulse inside the urgent threshold window, and invokes
 * [CountdownAtom.onExpire] once when reaching zero.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiCountdown(
    atom: CountdownAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    val remaining = rememberCountdownSeconds(atom = atom, onAction = onAction)
    val isUrgent = isCountdownUrgent(remaining, atom.urgentThresholdSeconds)
    val palette = resolveCountdownColors(remaining = remaining, isUrgent = isUrgent, colors = colors)
    val effectiveMotion = resolveCountdownMotion(atom.motion, isUrgent)
    val formatted =
        formatCountdownSeconds(
            seconds = remaining,
            showHours = atom.showHours,
            expiredText = atom.expiredText,
        )
    val pillShape = RoundedCornerShape(cornerRadius.full)
    val labelText = atom.label?.takeIf { it.isNotBlank() }
    val accessibleLabel = if (labelText != null) "$labelText $formatted" else formatted

    Row(
        modifier =
            modifier
                .testTag("countdown_${atom.countdownId}")
                .applyMotionSpec(motion = effectiveMotion, motionKey = atom.countdownId)
                .clip(pillShape)
                .background(palette.background, pillShape)
                .border(strokeWidth.thin, palette.border, pillShape)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                }.padding(horizontal = spacing.space8, vertical = spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (labelText != null) {
            Text(
                text = labelText,
                style = typography.labelSmall,
                color = palette.foreground,
                maxLines = 1,
            )
        }
        UiRollingCounter(
            value = formatted,
            style = typography.labelLarge,
            color = palette.foreground,
        )
    }
}

@Composable
private fun rememberCountdownSeconds(
    atom: CountdownAtom,
    onAction: (ActionRef) -> Unit,
): Int {
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnExpire by rememberUpdatedState(atom.onExpire)
    val initialRemaining =
        remember(atom.countdownId, atom.remainingSeconds, atom.expiresAtEpochMs) {
            resolveCountdownRemainingSeconds(
                fallbackRemainingSeconds = atom.remainingSeconds,
                expiresAtEpochMs = atom.expiresAtEpochMs,
                nowEpochMs = currentSystemEpochMs(),
            )
        }
    var remaining by remember(atom.countdownId, atom.remainingSeconds, atom.expiresAtEpochMs) {
        mutableIntStateOf(initialRemaining)
    }
    var expireDispatched by remember(atom.countdownId, atom.remainingSeconds, atom.expiresAtEpochMs) {
        mutableStateOf(initialRemaining <= 0)
    }

    LaunchedEffect(atom.countdownId, atom.remainingSeconds, atom.expiresAtEpochMs) {
        if (expireDispatched) return@LaunchedEffect
        val expiresAt = atom.expiresAtEpochMs
        if (expiresAt != null) {
            while (remaining > 0) {
                val now = currentSystemEpochMs()
                val next = resolveCountdownRemainingSeconds(atom.remainingSeconds, expiresAt, now)
                remaining = next
                if (next <= 0) break
                val msUntilBoundary = ((expiresAt - now - 1L) % TICK_INTERVAL_MS) + 1L
                delay(msUntilBoundary.coerceIn(MIN_TICK_DELAY_MS, TICK_INTERVAL_MS))
            }
        } else {
            while (remaining > 0) {
                delay(TICK_INTERVAL_MS)
                remaining -= 1
            }
        }
        if (!expireDispatched && remaining <= 0) {
            expireDispatched = true
            currentOnExpire?.let(currentOnAction)
        }
    }
    return remaining
}

private fun resolveCountdownColors(
    remaining: Int,
    isUrgent: Boolean,
    colors: ColorTokens,
): ResolvedCountdownColors =
    when {
        isUrgent -> {
            ResolvedCountdownColors(
                background = colors.status.errorSubtle,
                foreground = colors.status.error,
                border = colors.status.error,
            )
        }

        remaining <= 0 -> {
            ResolvedCountdownColors(
                background = colors.tag.neutralBackground,
                foreground = colors.tag.neutralForeground,
                border = colors.border.subtle,
            )
        }

        else -> {
            ResolvedCountdownColors(
                background = colors.tag.brandBackground,
                foreground = colors.tag.brandForeground,
                border = colors.brand.primary.copy(alpha = BRAND_BORDER_ALPHA),
            )
        }
    }

private fun resolveCountdownMotion(
    motion: MotionSpec?,
    isUrgent: Boolean,
): MotionSpec? =
    when {
        isUrgent && (motion == null || motion.loopEffect == MotionLoopEffect.NONE) -> {
            (motion ?: MotionSpec()).copy(loopEffect = MotionLoopEffect.HEARTBEAT)
        }

        else -> {
            motion
        }
    }
