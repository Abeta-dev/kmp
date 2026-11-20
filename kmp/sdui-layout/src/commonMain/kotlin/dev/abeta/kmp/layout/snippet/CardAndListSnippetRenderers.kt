package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiAvatar
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiImage
import dev.abeta.kmp.atoms.atoms.UiProgress
import dev.abeta.kmp.atoms.atoms.UiSkeleton
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class MetricCardSnippetRenderer(
    json: Json,
) : SnippetRenderer<MetricCardSnippetData> {
    override val snippetType: String = SnippetTypes.METRIC_CARD
    private val decoder = kotlinxSnippetDecoder<MetricCardSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<MetricCardSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: MetricCardSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
            onClick = model.onClick?.let { action -> { onAction(action) } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    UiText(
                        atom = model.label,
                        modifier = Modifier.weight(1f),
                        onAction = onAction,
                    )
                    model.icon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    UiText(atom = model.value, onAction = onAction)
                    model.delta?.let { delta -> UiTag(atom = delta, onAction = onAction) }
                }
                model.caption?.let { caption -> UiText(atom = caption, onAction = onAction) }
                model.progress?.let { progress ->
                    UiProgress(atom = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

internal class CardSnippetRenderer(
    json: Json,
) : SnippetRenderer<CardSnippetData> {
    override val snippetType: String = SnippetTypes.CARD
    private val decoder = kotlinxSnippetDecoder<CardSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<CardSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: CardSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
            onClick = model.onClick?.let { action -> { onAction(action) } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                model.media?.let { media ->
                    UiImage(atom = media, modifier = Modifier.fillMaxWidth(), onAction = onAction)
                }
                CardHeaderRow(model = model, onAction = onAction)
                model.description?.let { description -> UiText(atom = description, onAction = onAction) }
                if (model.skeletons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                        model.skeletons.forEach { skeleton -> UiSkeleton(atom = skeleton) }
                    }
                }
                CardButtonsRow(model = model, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun CardHeaderRow(
        model: CardSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            model.avatar?.let { avatar -> UiAvatar(atom = avatar, onAction = onAction) }
            model.leadingIcon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                UiText(atom = model.title, onAction = onAction)
                model.subtitle?.let { subtitle -> UiText(atom = subtitle, onAction = onAction) }
            }
            model.badge?.let { badge -> UiTag(atom = badge, onAction = onAction) }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun CardButtonsRow(
        model: CardSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        if (model.primaryButton == null && model.secondaryButton == null) return
        val spacing = ThemeTokens.spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            model.secondaryButton?.let { secondary -> UiButton(atom = secondary, onAction = onAction) }
            model.primaryButton?.let { primary -> UiButton(atom = primary, onAction = onAction) }
        }
    }
}

internal class ActionRowSnippetRenderer(
    json: Json,
) : SnippetRenderer<ActionRowSnippetData> {
    override val snippetType: String = SnippetTypes.ACTION_ROW
    private val decoder = kotlinxSnippetDecoder<ActionRowSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<ActionRowSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: ActionRowSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
            onClick = model.onClick?.let { action -> { onAction(action) } },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                model.leadingIcon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    UiText(atom = model.title, onAction = onAction)
                    model.subtitle?.let { subtitle -> UiText(atom = subtitle, onAction = onAction) }
                }
                model.trailingTag?.let { tag -> UiTag(atom = tag, onAction = onAction) }
                model.trailingIcon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
            }
        }
    }
}
