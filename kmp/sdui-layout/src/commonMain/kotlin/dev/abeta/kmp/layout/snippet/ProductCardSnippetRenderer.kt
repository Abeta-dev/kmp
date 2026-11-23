package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.abeta.kmp.atoms.atoms.UiImage
import dev.abeta.kmp.atoms.atoms.UiStepper
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class ProductCardSnippetRenderer(
    json: Json,
) : SnippetRenderer<ProductCardSnippetData> {
    override val snippetType: String = SnippetTypes.PRODUCT_CARD
    private val decoder = kotlinxSnippetDecoder<ProductCardSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<ProductCardSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: ProductCardSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val quantityOverride = formValues[model.stepper.fieldId]?.toIntOrNull()

        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("product_card_${model.productId}"),
            layout = model.layout,
            onClick = model.onClick?.let { action -> { onAction(action) } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                ProductCardMedia(model = model, onAction = onAction)
                UiText(atom = model.title, onAction = onAction)
                model.subtitle?.let { subtitle ->
                    UiText(atom = subtitle, onAction = onAction)
                }
                model.rating?.let { rating ->
                    UiText(atom = rating, onAction = onAction)
                }
                ProductCardFooter(
                    model = model,
                    quantityOverride = quantityOverride,
                    onQuantityChange = { updatedQty ->
                        onFormValueChange(model.stepper.fieldId, updatedQty.toString())
                    },
                    onAction = onAction,
                )
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun ProductCardMedia(
        model: ProductCardSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val image = model.image
        val badge = model.badge
        if (image != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                UiImage(atom = image, modifier = Modifier.fillMaxWidth(), onAction = onAction)
                badge?.let { tag ->
                    UiTag(
                        atom = tag,
                        modifier = Modifier.align(Alignment.TopStart).padding(spacing.space4),
                        onAction = onAction,
                    )
                }
            }
        } else if (badge != null) {
            UiTag(atom = badge, onAction = onAction)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun ProductCardFooter(
        model: ProductCardSnippetData,
        quantityOverride: Int?,
        onQuantityChange: (Int) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiText(atom = model.price.copy(maxLines = 1), onAction = onAction)
                model.originalPrice?.let { original ->
                    UiText(
                        atom = original.copy(strikeThrough = true, maxLines = 1),
                        onAction = onAction,
                    )
                }
            }
            UiStepper(
                atom = model.stepper.copy(label = null),
                quantityOverride = quantityOverride,
                onQuantityChange = onQuantityChange,
                onAction = onAction,
            )
        }
    }
}
