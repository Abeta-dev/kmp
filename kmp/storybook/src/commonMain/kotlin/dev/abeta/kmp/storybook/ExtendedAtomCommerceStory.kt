package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiKeyValueRow
import dev.abeta.kmp.atoms.atoms.UiPrice
import dev.abeta.kmp.atoms.atoms.UiQrCode
import dev.abeta.kmp.atoms.atoms.UiRating
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiStepper
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.KeyValueRowAtom
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.QrCodeAtom
import dev.abeta.kmp.contracts.RatingAtom
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SPACER_SM_DP = 8
private const val SPACER_MD_DP = 12
private const val READONLY_RATING = 4.8f
private const val READONLY_REVIEW_COUNT = 1420
private const val INITIAL_INTERACTIVE_RATING = 4.0f
private const val MAX_STEPPER_QTY = 10
private const val INITIAL_SECONDARY_QTY = 2
private const val QR_PREVIEW_SIZE_DP = 160
private const val AVOCADO_UNIT_CENTS = 349
private const val MILK_UNIT_CENTS = 329

private data class QrDemoPreset(
    val id: String,
    val buttonLabel: String,
    val payload: String,
    val label: String,
    val caption: String,
)

private val QR_DEMO_PRESETS =
    listOf(
        QrDemoPreset(
            id = "event_pass",
            buttonLabel = "Event Pass",
            payload = "ABETA-PULSE:PLS-7712:GATE4B:ROWC-12-13",
            label = "QR · PLS-7712 · ARENA GOLD (2x)",
            caption = "Harbor Dome Arena · Gate 4B Fast-Track",
        ),
        QrDemoPreset(
            id = "pay_scan",
            buttonLabel = "Pay Scan",
            payload = "ABETA-PAY:RECEIVE:@abeta.user:USD",
            label = "Scan to Receive · @abeta.user",
            caption = "Instant P2P Wallet · Zero-Fee Clearing",
        ),
        QrDemoPreset(
            id = "guest_wifi",
            buttonLabel = "WiFi",
            payload = "WIFI:T:WPA;S:AbetaHub-5G;P:kmp-sdui;;",
            label = "Abeta Express Hub WiFi",
            caption = "Scan to connect to 5 GHz Studio Mesh",
        ),
    )

/**
 * Interactive story showcasing [UiPrice], [UiRating], [UiKeyValueRow], [UiQrCode],
 * and [UiStepper] with live `SduiJson` wire inspection.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ExtendedAtomCommerceStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var selectedPresetId by remember { mutableStateOf(QR_DEMO_PRESETS.first().id) }
    val activePreset = QR_DEMO_PRESETS.firstOrNull { it.id == selectedPresetId } ?: QR_DEMO_PRESETS.first()
    val qrAtom =
        remember(activePreset) {
            QrCodeAtom(
                id = "story_qr_${activePreset.id}",
                payload = activePreset.payload,
                label = activePreset.label,
                caption = activePreset.caption,
                sizeDp = QR_PREVIEW_SIZE_DP,
                onClick =
                    ActionRef(
                        id = "story_qr_tap_${activePreset.id}",
                        type = ActionTypes.SHOW_TOAST,
                        payload = buildJsonObject { put("message", "Copied payload: ${activePreset.payload}") },
                    ),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        PriceSpecimenCard()
        RatingSpecimenCard(onAction = onAction)
        BillBreakdownSpecimenCard(onAction = onAction)
        QrCodeSpecimenCard(
            selectedPresetId = activePreset.id,
            qrAtom = qrAtom,
            onSelectPreset = { selectedPresetId = it },
            onAction = onAction,
        )
        StepperSpecimenCard(onAction = onAction)
        JsonContractPreviewCard(
            title = "QrCodeAtom Wire JSON",
            jsonText = SduiJson.encodeToString(QrCodeAtom.serializer(), qrAtom),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PriceSpecimenCard() {
    val spacing = ThemeTokens.spacing
    var emphasized by remember { mutableStateOf(true) }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "UiPrice · MRP, Discount & Unit", typography = TypographyTokenRef("title.large")))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Text(
                    text = "Emphasized",
                    style = ThemeTokens.typography.bodySmall,
                    color = ThemeTokens.colors.text.secondary,
                )
                Switch(checked = emphasized, onCheckedChange = { emphasized = it })
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            UiPrice(
                PriceAtom(
                    id = "story_price_fresh",
                    amountText = "\$3.49",
                    originalAmountText = "\$4.99",
                    discountLabel = "30% OFF",
                    unitSuffix = "/ 2 pcs",
                    emphasized = emphasized,
                ),
            )
            UiPrice(
                PriceAtom(
                    id = "story_price_mart",
                    amountText = "\$149.00",
                    originalAmountText = "\$219.00",
                    discountLabel = "32% OFF",
                    emphasized = emphasized,
                ),
            )
            UiPrice(
                PriceAtom(
                    id = "story_price_trade",
                    amountText = "\$0.68",
                    originalAmountText = "\$0.84",
                    discountLabel = "18% OFF",
                    unitSuffix = "/ unit",
                    emphasized = false,
                ),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RatingSpecimenCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var userRating by remember { mutableFloatStateOf(INITIAL_INTERACTIVE_RATING) }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "UiRating · Half-Star & Interactive",
                    typography = TypographyTokenRef("title.large"),
                ),
            )
            UiTag(TagAtom(label = "${userRating.toInt()} / 5 ★", variant = TagVariant.WARNING))
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            UiRating(
                atom =
                    RatingAtom(
                        id = "story_rating_readonly",
                        value = READONLY_RATING,
                        reviewCount = READONLY_REVIEW_COUNT,
                        interactive = false,
                        label = "Read-Only",
                    ),
            )
            UiRating(
                atom =
                    RatingAtom(
                        id = "story_rating_interactive",
                        fieldId = "story_user_rating",
                        value = userRating,
                        interactive = true,
                        label = "Tap to Rate",
                        onRate = ActionRef(id = "story_rate_product", type = ActionTypes.TRACK_EVENT),
                    ),
                ratingOverride = userRating,
                onRate = { userRating = it },
                onAction = onAction,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun BillBreakdownSpecimenCard(onAction: (ActionRef) -> Unit) {
    val rows =
        remember {
            listOf(
                KeyValueRowAtom(
                    id = "story_kv_sub",
                    label = "Item Subtotal",
                    sublabel = "2 fresh produce items",
                    value = "\$17.75",
                ),
                KeyValueRowAtom(
                    id = "story_kv_handling",
                    label = "Chilled Handling",
                    value = "\$0.00",
                    valueColor = ColorTokenRef("status.success"),
                ),
                KeyValueRowAtom(
                    id = "story_kv_promo",
                    label = "Promo (FRESH20)",
                    value = "-\$3.50",
                    valueColor = ColorTokenRef("status.success"),
                    showDivider = true,
                ),
                KeyValueRowAtom(
                    id = "story_kv_total",
                    label = "Grand Total",
                    sublabel = "Includes taxes & instant cashback",
                    value = "\$14.25",
                    valueColor = ColorTokenRef("brand.primary"),
                    emphasized = true,
                    onClick = ActionRef(id = "story_kv_total_tap", type = ActionTypes.SHOW_TOAST),
                ),
            )
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "UiKeyValueRow · Checkout Bill Breakdown",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        rows.forEach { rowAtom ->
            UiKeyValueRow(atom = rowAtom, onAction = onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun QrCodeSpecimenCard(
    selectedPresetId: String,
    qrAtom: QrCodeAtom,
    onSelectPreset: (String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiQrCode · Deterministic QR Generator", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            QR_DEMO_PRESETS.forEach { preset ->
                FilterChip(
                    selected = preset.id == selectedPresetId,
                    onClick = { onSelectPreset(preset.id) },
                    label = { Text(preset.buttonLabel) },
                )
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiQrCode(atom = qrAtom, onAction = onAction)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StepperSpecimenCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var avocadoQty by remember { mutableIntStateOf(0) }
    var milkQty by remember { mutableIntStateOf(INITIAL_SECONDARY_QTY) }
    val totalCents = (avocadoQty * AVOCADO_UNIT_CENTS) + (milkQty * MILK_UNIT_CENTS)

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "UiStepper · ADD ↔ Quantity Morph", typography = TypographyTokenRef("title.large")))
            UiTag(
                TagAtom(
                    label = "Basket ${SuperAppDemoBuilder.formatUsd(totalCents)}",
                    variant = TagVariant.SUCCESS,
                ),
            )
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            UiStepper(
                atom =
                    StepperAtom(
                        fieldId = "story_stepper_avocados",
                        label = "Organic Hass Avocados (\$3.49)",
                        quantity = avocadoQty,
                        maxQuantity = MAX_STEPPER_QTY,
                        onIncrement = ActionRef(id = "story_stepper_avo_inc", type = ActionTypes.SAVE_STATE),
                        onDecrement = ActionRef(id = "story_stepper_avo_dec", type = ActionTypes.SAVE_STATE),
                    ),
                quantityOverride = avocadoQty,
                onQuantityChange = { avocadoQty = it },
                onAction = onAction,
            )
            UiStepper(
                atom =
                    StepperAtom(
                        fieldId = "story_stepper_milk",
                        label = "A2 Chilled Whole Milk (\$3.29)",
                        quantity = milkQty,
                        maxQuantity = MAX_STEPPER_QTY,
                        onIncrement = ActionRef(id = "story_stepper_milk_inc", type = ActionTypes.SAVE_STATE),
                        onDecrement = ActionRef(id = "story_stepper_milk_dec", type = ActionTypes.SAVE_STATE),
                    ),
                quantityOverride = milkQty,
                onQuantityChange = { milkQty = it },
                onAction = onAction,
            )
        }
    }
}
