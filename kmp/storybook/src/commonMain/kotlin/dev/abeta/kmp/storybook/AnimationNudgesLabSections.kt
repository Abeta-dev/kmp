package dev.abeta.kmp.storybook

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.ThemeTokens

private const val CART_SIM_HEIGHT_DP = 440

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun FreeDeliveryThresholdSimulatorSection(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    val page = remember { AnimationNudgesFixtures.buildFreeDeliveryCartPage() }
    val formValues =
        remember {
            mutableStateMapOf(
                AnimationNudgesFixtures.JUICE_FIELD_ID to "1",
                AnimationNudgesFixtures.GRANOLA_FIELD_ID to "1",
            )
        }
    val frameShape = RoundedCornerShape(ThemeTokens.cornerRadius.md)

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Live Free-Delivery Threshold Nudge (\$15.00)",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiTag(TagAtom(label = "FloatingCartDockHost", variant = TagVariant.SUCCESS))
            }
            Text(
                text =
                    "Tap + on either item to watch the FloatingCartDockHost progress bar " +
                        "fill, auto-jiggle, and unlock.",
                style = ThemeTokens.typography.bodySmall,
                color = colors.text.secondary,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CART_SIM_HEIGHT_DP.dp)
                        .clip(frameShape)
                        .border(ThemeTokens.strokeWidth.thin, colors.border.default, frameShape),
            ) {
                SduiScreen(
                    state = LayoutUiState.Ready(page = page, formValues = formValues.toMap()),
                    snippetRegistry = registry,
                    onAction = onAction,
                    onFormValueChange = { fieldId, value -> formValues[fieldId] = value },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun LiveNudgesAndTrackerSection(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        MarqueeAndDismissibleNudgeCard(registry = registry, onAction = onAction)
        InteractiveOrderTrackerCard(registry = registry, onAction = onAction)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MarqueeAndDismissibleNudgeCard(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val marqueeEnvelope = remember { AnimationNudgesFixtures.buildMarqueeEnvelope() }
    var nudgeRestoreKey by remember { mutableIntStateOf(0) }
    var nudgeDismissed by remember { mutableStateOf(false) }
    val nudgeEnvelope =
        remember(nudgeRestoreKey) {
            AnimationNudgesFixtures.buildDismissibleNudgeEnvelope(nudgeRestoreKey)
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            UiText(
                TextAtom(
                    text = "Live Marquee Strip & Dismissible Nudge Bar",
                    typography = TypographyTokenRef("title.medium"),
                ),
            )
            registry.Render(marqueeEnvelope, emptyMap(), { _, _ -> }, onAction)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiTag(
                    TagAtom(
                        label = if (nudgeDismissed) "Nudge Dismissed" else "Nudge Visible",
                        variant = if (nudgeDismissed) TagVariant.NEUTRAL else TagVariant.BRAND,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        nudgeRestoreKey += 1
                        nudgeDismissed = false
                    },
                ) { Text("Restore Nudge") }
            }
            registry.Render(
                envelope = nudgeEnvelope,
                formValues = emptyMap(),
                onFormValueChange = { _, _ -> },
                onAction = { action ->
                    if (action.id == "lab_nudge_dismissed") nudgeDismissed = true
                    onAction(action)
                },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun InteractiveOrderTrackerCard(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    var trackerStage by remember { mutableIntStateOf(0) }
    val trackerEnvelope =
        remember(trackerStage) {
            AnimationNudgesFixtures.buildOrderTrackerEnvelope(trackerStage)
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Interactive 4-Stage Order Tracker",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                Button(
                    onClick = {
                        trackerStage = (trackerStage + 1) % AnimationNudgesFixtures.TRACKER_STAGE_COUNT
                    },
                ) { Text("Advance Rider Step") }
            }
            registry.Render(trackerEnvelope, emptyMap(), { _, _ -> }, onAction)
        }
    }
}
