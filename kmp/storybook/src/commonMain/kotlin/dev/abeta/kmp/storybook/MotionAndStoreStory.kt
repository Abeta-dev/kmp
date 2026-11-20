package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCelebrationOverlay
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val AVOCADO_CENTS = 349
private const val SOURDOUGH_CENTS = 499
private const val CENTS_PER_DOLLAR = 100
private const val CENTS_PAD_DIGITS = 2
private const val CARD_PADDING_DP = 14
private const val CARD_RADIUS_DP = 12

/**
 * Interactive Storybook showcase for SDUI [MotionSpec] presets, ambient loop effects,
 * [ProductCardSnippetData] steppers, floating cart calculations, and celebratory order overlays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MotionAndStoreStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var selectedTransition by remember { mutableStateOf(MotionTransition.SPRING_BOUNCE) }
    var replayCount by remember { mutableIntStateOf(0) }
    var celebrationVisible by remember { mutableStateOf(false) }

    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    val products = remember { buildStoryStoreProducts() }
    val formValues =
        remember {
            mutableStateMapOf(
                "cart_qty_story_avocados" to "1",
                "cart_qty_story_sourdough" to "0",
            )
        }

    val totalItems =
        products.sumOf { (card, _) ->
            formValues[card.stepper.fieldId]?.toIntOrNull() ?: card.stepper.quantity
        }
    val totalCents =
        products.sumOf { (card, _) ->
            val qty = formValues[card.stepper.fieldId]?.toIntOrNull() ?: card.stepper.quantity
            qty * card.unitPriceCents
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
            MotionPresetSection(
                selectedTransition = selectedTransition,
                replayCount = replayCount,
                onSelectTransition = { selectedTransition = it },
                onReplay = { replayCount += 1 },
            )
            QuickCommerceShowcaseSection(
                products = products,
                registry = registry,
                formValues = formValues.toMap(),
                totalItems = totalItems,
                totalCents = totalCents,
                onFormValueChange = { fieldId, value -> formValues[fieldId] = value },
                onAction = onAction,
                onCheckout = {
                    onAction(buildStoryCheckoutAction())
                    celebrationVisible = true
                },
            )
        }

        UiCelebrationOverlay(
            visible = celebrationVisible,
            title = "Order Confirmed!",
            subtitle = "Abeta Fresh rider arriving in 8 minutes",
            onDismiss = { celebrationVisible = false },
        )
    }
}

private fun buildStoryCheckoutAction(): ActionRef =
    ActionRef(
        id = "story_express_checkout",
        type = ActionTypes.TRIGGER_FEEDBACK,
        payload =
            buildJsonObject {
                put("effect", "celebration")
                put("title", "Order Confirmed!")
            },
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MotionPresetSection(
    selectedTransition: MotionTransition,
    replayCount: Int,
    onSelectTransition: (MotionTransition) -> Unit,
    onReplay: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Declarative MotionSpec Presets", typography = TypographyTokenRef("title.medium")))
            Button(onClick = onReplay) { Text("Replay Motion") }
        }
        FlowRow(
            modifier = Modifier.padding(top = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            MotionTransition.entries.forEach { preset ->
                FilterChip(
                    selected = selectedTransition == preset,
                    onClick = { onSelectTransition(preset) },
                    label = { Text(preset.name) },
                )
            }
        }
        MotionPreviewCards(selectedTransition = selectedTransition, replayCount = replayCount)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun MotionPreviewCards(
    selectedTransition: MotionTransition,
    replayCount: Int,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    val cardMotion = MotionSpec(enter = selectedTransition, durationToken = MotionDurationToken.NORMAL)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiSurfaceCard(
            modifier =
                Modifier
                    .weight(1f)
                    .applyMotionSpec(motion = cardMotion, motionKey = "${selectedTransition.name}_$replayCount"),
        ) {
            Text(
                text = "Enter: ${selectedTransition.name}",
                style = ThemeTokens.typography.titleSmall,
                color = colors.text.primary,
            )
            Text(
                text = "Respects ThemeTokens.motion.reducedMotion automatically.",
                style = ThemeTokens.typography.bodySmall,
                color = colors.text.secondary,
            )
        }
        UiTag(
            atom =
                TagAtom(
                    label = "8 MINS PULSE",
                    variant = TagVariant.SUCCESS,
                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                ),
        )
    }
    UiSurfaceCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = spacing.space8)
                .applyMotionSpec(motion = MotionSpec(loopEffect = MotionLoopEffect.SHIMMER)),
    ) {
        Text(
            text = "Shimmer Loop Effect · Flash Express Deals",
            style = ThemeTokens.typography.labelLarge,
            color = colors.text.brand,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun QuickCommerceShowcaseSection(
    products: List<Pair<ProductCardSnippetData, SnippetEnvelope>>,
    registry: dev.abeta.kmp.layout.snippet.SnippetRegistry,
    formValues: Map<String, String>,
    totalItems: Int,
    totalCents: Int,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    onCheckout: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Abeta Fresh Quick-Commerce", typography = TypographyTokenRef("title.medium")))
            UiTag(
                atom =
                    TagAtom(
                        label = "8 MINS EXPRESS",
                        variant = TagVariant.BRAND,
                        motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                    ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            products.forEach { (_, envelope) ->
                Box(modifier = Modifier.weight(1f)) {
                    registry.Render(
                        envelope = envelope,
                        formValues = formValues,
                        onFormValueChange = onFormValueChange,
                        onAction = onAction,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cart: $totalItems items · ${formatDollars(totalCents)}",
                style = ThemeTokens.typography.titleSmall,
                color = colors.text.primary,
            )
            UiButton(
                atom =
                    ButtonAtom(
                        id = "story_checkout_cta",
                        label = "Simulate Express Checkout",
                        variant = ButtonVariant.PRIMARY,
                        onClick = buildStoryCheckoutAction(),
                        motion = MotionSpec(enter = MotionTransition.SPRING_BOUNCE),
                    ),
                onAction = { onCheckout() },
                modifier = Modifier,
            )
        }
    }
}

private fun buildStoryStoreProducts(): List<Pair<ProductCardSnippetData, SnippetEnvelope>> {
    val layout =
        LayoutConfig(
            padding = SpacingValues(all = CARD_PADDING_DP),
            bgColor = ColorTokenRef("surface.primary"),
            cornerRadius = CornerRadiusSpec(all = CARD_RADIUS_DP),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef("border.subtle")),
            fillMaxWidth = true,
        )
    val items =
        listOf(
            ProductCardSnippetData(
                productId = "story_avocados",
                title = TextAtom(text = "Hass Avocados (2 pcs)", typography = TypographyTokenRef("title.small")),
                subtitle = TextAtom(text = "Ripe & ready · 350g", color = ColorTokenRef("text.secondary")),
                price = TextAtom(text = "$3.49", typography = TypographyTokenRef("label.large")),
                originalPrice = TextAtom(text = "$4.49", color = ColorTokenRef("text.tertiary")),
                unitPriceCents = AVOCADO_CENTS,
                badge = TagAtom(label = "22% OFF", variant = TagVariant.SUCCESS),
                stepper = StepperAtom(fieldId = "cart_qty_story_avocados", quantity = 1, maxQuantity = 10),
                rating = TextAtom(text = "4.9 ★", color = ColorTokenRef("status.success")),
                layout = layout,
            ),
            ProductCardSnippetData(
                productId = "story_sourdough",
                title = TextAtom(text = "Artisan Sourdough Loaf", typography = TypographyTokenRef("title.small")),
                subtitle = TextAtom(text = "Baked fresh daily · 450g", color = ColorTokenRef("text.secondary")),
                price = TextAtom(text = "$4.99", typography = TypographyTokenRef("label.large")),
                originalPrice = TextAtom(text = "$5.99", color = ColorTokenRef("text.tertiary")),
                unitPriceCents = SOURDOUGH_CENTS,
                badge = TagAtom(label = "FRESH", variant = TagVariant.INFO),
                stepper = StepperAtom(fieldId = "cart_qty_story_sourdough", quantity = 0, maxQuantity = 10),
                rating = TextAtom(text = "4.8 ★", color = ColorTokenRef("status.success")),
                layout = layout,
            ),
        )
    return items.mapIndexed { idx, data ->
        val encoded = SduiJson.encodeToJsonElement(ProductCardSnippetData.serializer(), data) as JsonObject
        val envelope =
            SnippetEnvelope(
                snippetId = "story_prod_${data.productId}",
                snippetType = SnippetTypes.PRODUCT_CARD,
                data = encoded,
                motion =
                    MotionSpec(
                        enter = MotionTransition.SCALE_IN,
                        durationToken = MotionDurationToken.FAST,
                        staggerMs = 60,
                        delayMs = idx * 60,
                    ),
            )
        data to envelope
    }
}

private fun formatDollars(cents: Int): String {
    val safeCents = cents.coerceAtLeast(0)
    val dollars = safeCents / CENTS_PER_DOLLAR
    val remainder = safeCents % CENTS_PER_DOLLAR
    return "$$dollars.${remainder.toString().padStart(CENTS_PAD_DIGITS, '0')}"
}
