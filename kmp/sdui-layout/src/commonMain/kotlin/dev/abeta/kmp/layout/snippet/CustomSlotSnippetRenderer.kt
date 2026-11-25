package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CustomSlotSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val UNREGISTERED_ICON_SIZE_DP = 18

internal class CustomSlotSnippetRenderer(
    json: Json,
    private val registryProvider: () -> SnippetRegistry,
) : SnippetRenderer<CustomSlotSnippetData> {
    override val snippetType: String = SnippetTypes.CUSTOM_SLOT
    private val decoder = kotlinxSnippetDecoder<CustomSlotSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<CustomSlotSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: CustomSlotSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val depth = LocalSnippetDepth.current
        if (depth >= MAX_SNIPPET_NESTING_DEPTH) return

        val hostRenderer = LocalCustomSlotRegistry.current.resolve(model.slotType)
        val fallbackEnvelope = model.fallback
        when {
            hostRenderer != null -> {
                RenderRegisteredSlot(
                    model = model,
                    envelope = envelope,
                    formValues = formValues,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                    hostRenderer = hostRenderer,
                )
            }

            fallbackEnvelope != null -> {
                val registry = LocalSnippetRegistry.current ?: registryProvider()
                CompositionLocalProvider(LocalSnippetDepth provides depth + 1) {
                    registry.Render(
                        envelope = fallbackEnvelope,
                        formValues = formValues,
                        onFormValueChange = onFormValueChange,
                        onAction = onAction,
                    )
                }
            }

            else -> {
                RenderUnregisteredSlotNotice(model = model)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderRegisteredSlot(
        model: CustomSlotSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        hostRenderer: CustomSlotRenderer,
    ) {
        val minHeightDp = model.minHeightDp
        val aspectRatio = model.aspectRatio
        val slotModifier =
            Modifier
                .fillMaxWidth()
                .applyLayoutConfig(model.layout)
                .let { mod ->
                    if (minHeightDp != null && minHeightDp > 0) {
                        mod.defaultMinSize(minHeight = minHeightDp.dp)
                    } else {
                        mod
                    }
                }.let { mod ->
                    if (aspectRatio != null && aspectRatio.isFinite() && aspectRatio > 0f) {
                        mod.aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
                    } else {
                        mod
                    }
                }.testTag("custom_slot_${model.slotId}")

        val context =
            CustomSlotContext(
                snippetId = envelope.snippetId,
                slotId = model.slotId,
                slotType = model.slotType,
                initialState = model.initialState,
                formValues = formValues,
                onFormValueChange = onFormValueChange,
                onAction = onAction,
                onEvent = model.onEvent,
            )

        Box(modifier = slotModifier) {
            hostRenderer.Render(context)
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun RenderUnregisteredSlotNotice(model: CustomSlotSnippetData) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography

        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("custom_slot_unregistered_${model.slotId}"),
            layout = model.layout,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiIcon(
                    atom =
                        IconAtom(
                            name = "info",
                            sizeDp = UNREGISTERED_ICON_SIZE_DP,
                            color = ColorTokenRef(token = "icon.tertiary"),
                            contentDescription = "Unregistered slot",
                        ),
                )
                Text(
                    text = "Slot '${model.slotType}' is not registered on this host",
                    style = typography.bodySmall,
                    color = colors.text.secondary,
                )
            }
        }
    }
}
