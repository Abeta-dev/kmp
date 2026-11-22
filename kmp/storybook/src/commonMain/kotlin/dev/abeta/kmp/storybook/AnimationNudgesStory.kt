package dev.abeta.kmp.storybook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiCountdown
import dev.abeta.kmp.atoms.atoms.UiRollingCounter
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.DefaultMotionTokens
import dev.abeta.kmp.tokens.DesignTheme
import dev.abeta.kmp.tokens.DesignTokenBundle
import dev.abeta.kmp.tokens.ThemeTokens

private const val LOOP_CARD_WIDTH_DP = 176
private const val TACTILE_CARD_WIDTH_DP = 220
private const val INITIAL_COUNTER_CENTS = 349
private const val DELTA_SMALL_CENTS = 150
private const val DELTA_LARGE_CENTS = 499
private const val COUNTDOWN_NORMAL_SEC = 95
private const val COUNTDOWN_URGENT_SEC = 12
private const val COUNTDOWN_URGENT_THRESHOLD_SEC = 30
private const val MAX_EXPIRE_LOGS = 4

/**
 * Story 14: Interactive test bench for all 7 ambient [dev.abeta.kmp.contracts.MotionLoopEffect] presets,
 * [pressBounce], [UiRollingCounter], [UiCountdown], Free-Delivery Threshold Nudge,
 * `marquee_strip`, dismissible `nudge_bar`, and live 4-stage `order_tracker`.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun AnimationNudgesStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val parentReducedMotion = ThemeTokens.motion.reducedMotion
    var labReducedMotion by remember(parentReducedMotion) { mutableStateOf(parentReducedMotion) }
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        LabReducedMotionHeaderCard(
            reducedMotion = labReducedMotion,
            onReducedMotionChange = { labReducedMotion = it },
        )

        DesignTheme(
            reducedMotion = labReducedMotion,
            tokens =
                DesignTokenBundle(
                    colors = ThemeTokens.colors,
                    typography = ThemeTokens.typography,
                    spacing = ThemeTokens.spacing,
                    cornerRadius = ThemeTokens.cornerRadius,
                    strokeWidth = ThemeTokens.strokeWidth,
                    elevation = ThemeTokens.elevation,
                    motion = DefaultMotionTokens.copy(reducedMotion = labReducedMotion),
                    opacity = ThemeTokens.opacity,
                    sizing = ThemeTokens.sizing,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
                MotionLoopsAndPressBounceSection(onAction = onAction)
                RollingCounterCard()
                UrgencyCountdownCard(onAction = onAction)
                FreeDeliveryThresholdSimulatorSection(registry = registry, onAction = onAction)
                LiveNudgesAndTrackerSection(registry = registry, onAction = onAction)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun LabReducedMotionHeaderCard(
    reducedMotion: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = spacing.space12)) {
                UiText(
                    TextAtom(
                        text = "Animation & Nudges Test Bench",
                        typography = TypographyTokenRef("title.large"),
                    ),
                )
                Text(
                    text =
                        "Toggle local Reduced Motion to verify WCAG zero-motion fallbacks " +
                            "across every widget.",
                    style = ThemeTokens.typography.bodySmall,
                    color = colors.text.secondary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiTag(
                    TagAtom(
                        label = if (reducedMotion) "Reduced Motion" else "Full Motion",
                        variant = if (reducedMotion) TagVariant.WARNING else TagVariant.SUCCESS,
                    ),
                )
                Switch(checked = reducedMotion, onCheckedChange = onReducedMotionChange)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MotionLoopsAndPressBounceSection(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "All 7 Ambient MotionLoopEffects",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiTag(TagAtom(label = "7 Effects + PressBounce", variant = TagVariant.BRAND))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                AnimationNudgesFixtures.loopSpecimens.forEach { specimen ->
                    UiSurfaceCard(
                        modifier =
                            Modifier
                                .width(LOOP_CARD_WIDTH_DP.dp)
                                .applyMotionSpec(
                                    motion = MotionSpec(loopEffect = specimen.effect),
                                    motionKey = specimen.effect.name,
                                ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                            UiTag(TagAtom(label = specimen.badgeLabel, variant = specimen.badgeVariant))
                            Text(
                                text = specimen.title,
                                style = ThemeTokens.typography.titleSmall,
                                color = colors.text.primary,
                            )
                            Text(
                                text = specimen.useCase,
                                style = ThemeTokens.typography.caption,
                                color = colors.text.secondary,
                            )
                        }
                    }
                }
            }
            PressBounceTilesRow(onAction = onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PressBounceTilesRow(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    var bounceTapCount by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
        UiText(
            TextAtom(
                text = "Tactile Modifier.pressBounce() Cards (Taps: $bounceTapCount)",
                typography = TypographyTokenRef("label.large"),
            ),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            AnimationNudgesFixtures.pressBounceSpecimens.forEach { tile ->
                UiSurfaceCard(
                    modifier =
                        Modifier
                            .width(TACTILE_CARD_WIDTH_DP.dp)
                            .pressBounce()
                            .clickable {
                                bounceTapCount += 1
                                onAction(ActionRef(id = tile.id, type = ActionTypes.TRIGGER_FEEDBACK))
                            },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                        UiTag(TagAtom(label = tile.badgeLabel, variant = TagVariant.INFO))
                        Text(
                            text = tile.title,
                            style = ThemeTokens.typography.titleSmall,
                            color = colors.text.primary,
                        )
                        Text(
                            text = tile.caption,
                            style = ThemeTokens.typography.caption,
                            color = colors.text.secondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RollingCounterCard() {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    var counterCents by remember { mutableIntStateOf(INITIAL_COUNTER_CENTS) }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "UiRollingCounter Odometer",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiRollingCounter(
                    value = AnimationNudgesFixtures.formatUsdCents(counterCents),
                    style = ThemeTokens.typography.headlineMedium,
                    color = colors.text.brand,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Button(onClick = { counterCents += DELTA_SMALL_CENTS }) { Text("+\$1.50") }
                Button(onClick = { counterCents += DELTA_LARGE_CENTS }) { Text("+\$4.99") }
                OutlinedButton(onClick = { counterCents = 0 }) { Text("Reset") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UrgencyCountdownCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    var countdownSec by remember { mutableIntStateOf(COUNTDOWN_NORMAL_SEC) }
    var countdownNonce by remember { mutableIntStateOf(0) }
    val expireLogs = remember { mutableStateListOf<String>() }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "UiCountdown Urgency Pulse",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiCountdown(
                    atom =
                        CountdownAtom(
                            countdownId = "lab_urgency_$countdownNonce",
                            remainingSeconds = countdownSec,
                            label = "Deal",
                            urgentThresholdSeconds = COUNTDOWN_URGENT_THRESHOLD_SEC,
                            expiredText = "EXPIRED",
                            onExpire = ActionRef(id = "lab_countdown_expired", type = ActionTypes.TRACK_EVENT),
                        ),
                    onAction = { action ->
                        onAction(action)
                        expireLogs.add(0, "Expired: ${action.id} (run #${countdownNonce + 1})")
                        if (expireLogs.size > MAX_EXPIRE_LOGS) expireLogs.removeAt(expireLogs.lastIndex)
                    },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Button(
                    onClick = {
                        countdownSec = COUNTDOWN_URGENT_SEC
                        countdownNonce += 1
                    },
                ) { Text("Set 12s Urgent") }
                OutlinedButton(
                    onClick = {
                        countdownSec = COUNTDOWN_NORMAL_SEC
                        countdownNonce += 1
                    },
                ) { Text("Set 95s Normal") }
            }
            val logLine =
                expireLogs.firstOrNull()
                    ?: "Urgency heartbeat activates under ${COUNTDOWN_URGENT_THRESHOLD_SEC}s; onExpire logs here."
            Text(text = logLine, style = ThemeTokens.typography.caption, color = colors.text.secondary)
        }
    }
}
