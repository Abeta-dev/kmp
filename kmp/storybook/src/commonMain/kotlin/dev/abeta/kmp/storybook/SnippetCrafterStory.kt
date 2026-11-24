package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
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
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Story 15: Live Atomic Snippet Molding Workshop allowing engineers and designers to mold
 * custom `composed_block` snippets from stateless atoms and inspect real-time [dev.abeta.kmp.contracts.SduiJson].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun SnippetCrafterStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    var config by remember { mutableStateOf(SnippetCrafterPresets.defaultConfigFor(CrafterPreset.FLASH_COMBO)) }
    var motionNonce by remember { mutableIntStateOf(0) }
    val formValues = remember { mutableStateMapOf(SnippetCrafterPresets.STEPPER_FIELD_ID to "1") }

    val currentQty = formValues[SnippetCrafterPresets.STEPPER_FIELD_ID]?.toIntOrNull() ?: 1
    val envelope =
        remember(config, currentQty, motionNonce) {
            SnippetCrafterPresets.buildMoldedEnvelope(
                config = config,
                stepperQty = currentQty,
                motionNonce = motionNonce,
            )
        }
    val wireJson = remember(envelope) { SnippetCrafterPresets.formatEnvelopeJson(envelope) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        PresetSelectorCard(
            activePreset = config.preset,
            onSelectPreset = { preset ->
                config = SnippetCrafterPresets.defaultConfigFor(preset)
                formValues[SnippetCrafterPresets.STEPPER_FIELD_ID] = "1"
                motionNonce += 1
            },
        )
        MoldingControlsCard(
            config = config,
            onConfigChange = { updated -> config = updated },
            onReplayMotion = { motionNonce += 1 },
        )
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UiText(
                        TextAtom(
                            text = "Live ComposedBlock Preview",
                            typography = TypographyTokenRef("title.medium"),
                        ),
                    )
                    UiTag(TagAtom(label = config.preset.recipeName, variant = TagVariant.BRAND))
                }
                registry.Render(
                    envelope = envelope,
                    formValues = formValues.toMap(),
                    onFormValueChange = { fieldId, value -> formValues[fieldId] = value },
                    onAction = onAction,
                )
            }
        }
        JsonContractPreviewCard(
            title = "Live SduiJson Wire Payload (composed_block · ready for sdui-bff)",
            jsonText = wireJson,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PresetSelectorCard(
    activePreset: CrafterPreset,
    onSelectPreset: (CrafterPreset) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(TextAtom(text = "Preset Snippet Molds", typography = TypographyTokenRef("title.large")))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    val nodeKindCount = ComposedNodeKind.entries.size
                    UiTag(TagAtom(label = "$nodeKindCount ComposedNodeKinds", variant = TagVariant.BRAND))
                    UiTag(TagAtom(label = "Zero App Releases", variant = TagVariant.SUCCESS))
                }
            }
            UiText(
                TextAtom(
                    text =
                        "Powered by ${ComposedNodeKind.entries.size} ComposedNodeKinds — including " +
                            "scroll_row, grid, media_pager, switch, text_field, and per-child box_alignment overlays.",
                    typography = TypographyTokenRef("body.small"),
                    color = ColorTokenRef("text.secondary"),
                ),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                CrafterPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = activePreset == preset,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MoldingControlsCard(
    config: CrafterConfig,
    onConfigChange: (CrafterConfig) -> Unit,
    onReplayMotion: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(TextAtom(text = "Live Molding Controls", typography = TypographyTokenRef("title.medium")))
                OutlinedButton(onClick = onReplayMotion) { Text("Replay Motion") }
            }
            RootContainerSelector(
                selected = config.rootContainer,
                onSelect = { onConfigChange(config.copy(rootContainer = it)) },
            )
            MotionTransitionSelector(
                selected = config.enterTransition,
                onSelect = {
                    onConfigChange(config.copy(enterTransition = it))
                    onReplayMotion()
                },
            )
            AmbientLoopSelector(
                selected = config.loopEffect,
                onSelect = { onConfigChange(config.copy(loopEffect = it)) },
            )
            AtomToggleChips(config = config, onConfigChange = onConfigChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RootContainerSelector(
    selected: CrafterRootContainer,
    onSelect: (CrafterRootContainer) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        Text(
            text = "Root Container",
            style = ThemeTokens.typography.labelMedium,
            color = ThemeTokens.colors.text.secondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
            CrafterRootContainer.entries.forEach { root ->
                FilterChip(
                    selected = selected == root,
                    onClick = { onSelect(root) },
                    label = { Text(root.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MotionTransitionSelector(
    selected: MotionTransition,
    onSelect: (MotionTransition) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        Text(
            text = "Enter Transition",
            style = ThemeTokens.typography.labelMedium,
            color = ThemeTokens.colors.text.secondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            SnippetCrafterPresets.enterOptions.forEach { transition ->
                FilterChip(
                    selected = selected == transition,
                    onClick = { onSelect(transition) },
                    label = { Text(transition.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AmbientLoopSelector(
    selected: MotionLoopEffect,
    onSelect: (MotionLoopEffect) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        Text(
            text = "Ambient Loop Effect",
            style = ThemeTokens.typography.labelMedium,
            color = ThemeTokens.colors.text.secondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            SnippetCrafterPresets.loopOptions.forEach { loop ->
                FilterChip(
                    selected = selected == loop,
                    onClick = { onSelect(loop) },
                    label = { Text(loop.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AtomToggleChips(
    config: CrafterConfig,
    onConfigChange: (CrafterConfig) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val toggles =
        listOf(
            Triple("Badge Tag", config.includeBadgeTag) { v: Boolean -> config.copy(includeBadgeTag = v) },
            Triple("Countdown Timer", config.includeCountdown) { v: Boolean -> config.copy(includeCountdown = v) },
            Triple("Rolling Counter", config.includeRollingCounter) { v: Boolean ->
                config.copy(includeRollingCounter = v)
            },
            Triple("Progress Bar", config.includeProgressBar) { v: Boolean -> config.copy(includeProgressBar = v) },
            Triple("Inline Stepper", config.includeInlineStepper) { v: Boolean ->
                config.copy(includeInlineStepper = v)
            },
            Triple("CTA Button", config.includeCtaButton) { v: Boolean -> config.copy(includeCtaButton = v) },
        )
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        Text(
            text = "Included Stateless Atoms",
            style = ThemeTokens.typography.labelMedium,
            color = ThemeTokens.colors.text.secondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            toggles.forEach { (label, active, updater) ->
                FilterChip(
                    selected = active,
                    onClick = { onConfigChange(updater(!active)) },
                    label = { Text(label) },
                )
            }
        }
    }
}
