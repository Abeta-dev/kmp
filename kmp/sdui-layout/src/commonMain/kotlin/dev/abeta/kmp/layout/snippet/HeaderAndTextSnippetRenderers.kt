package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.clickable
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
import dev.abeta.kmp.atoms.atoms.UiSkeleton
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class HeaderSnippetRenderer(
    json: Json,
) : SnippetRenderer<HeaderSnippetData> {
    override val snippetType: String = SnippetTypes.HEADER
    private val decoder = kotlinxSnippetDecoder<HeaderSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<HeaderSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: HeaderSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val clickAction = model.onClick
        val containerModifier =
            Modifier
                .fillMaxWidth()
                .applyLayoutConfig(model.layout)
                .let { base ->
                    if (clickAction != null) base.clickable { onAction(clickAction) } else base
                }

        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
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
                model.trailingTag?.let { tag -> UiTag(atom = tag, onAction = onAction) }
                model.trailingAction?.let { button -> UiButton(atom = button, onAction = onAction) }
                model.trailingIcon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
            }
            if (model.skeletons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    model.skeletons.forEach { skeleton -> UiSkeleton(atom = skeleton) }
                }
            }
        }
    }
}

internal class BannerSnippetRenderer(
    json: Json,
) : SnippetRenderer<BannerSnippetData> {
    override val snippetType: String = SnippetTypes.BANNER
    private val decoder = kotlinxSnippetDecoder<BannerSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<BannerSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: BannerSnippetData,
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
                BannerHeaderRow(model = model, onAction = onAction)
                model.image?.let { image ->
                    UiImage(atom = image, modifier = Modifier.fillMaxWidth(), onAction = onAction)
                }
                model.cta?.let { cta -> UiButton(atom = cta, onAction = onAction) }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun BannerHeaderRow(
        model: BannerSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            model.icon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                UiText(atom = model.title, onAction = onAction)
                model.body?.let { body -> UiText(atom = body, onAction = onAction) }
            }
            model.tag?.let { tag -> UiTag(atom = tag, onAction = onAction) }
            model.dismissAction?.let { dismiss ->
                UiIcon(
                    atom =
                        IconAtom(
                            name = "close",
                            sizeDp = DISMISS_ICON_SIZE_DP,
                            contentDescription = "Dismiss",
                            onClick = dismiss,
                        ),
                    onAction = onAction,
                )
            }
        }
    }

    private companion object {
        const val DISMISS_ICON_SIZE_DP = 18
    }
}

internal class EmptyStateSnippetRenderer(
    json: Json,
) : SnippetRenderer<EmptyStateSnippetData> {
    override val snippetType: String = SnippetTypes.EMPTY_STATE
    private val decoder = kotlinxSnippetDecoder<EmptyStateSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<EmptyStateSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: EmptyStateSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                model.image?.let { image -> UiImage(atom = image, onAction = onAction) }
                model.icon?.let { icon -> UiIcon(atom = icon, onAction = onAction) }
                UiText(atom = model.title, onAction = onAction)
                model.message?.let { message -> UiText(atom = message, onAction = onAction) }
                model.actionButton?.let { button -> UiButton(atom = button, onAction = onAction) }
            }
        }
    }
}
