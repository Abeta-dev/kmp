package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.abeta.kmp.atoms.atoms.UiCelebrationOverlay
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.layout.snippet.LocalFormValidationErrors
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.ThemeTokens

private const val RFQ_CODE_BASE = 1040

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun TradeRfqJourneyFlow(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val chipsEnvelope = remember { EndToEndTradeFixtures.buildTradeVerifiedChipsEnvelope() }
    val rfqFormEnvelope = remember { EndToEndTradeFixtures.buildTradeRfqFormEnvelope() }
    val formValues = rememberTradeDefaultFormValues()
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var rfqCount by remember { mutableIntStateOf(0) }
    var confirmationToast by remember { mutableStateOf<String?>(null) }
    var celebrationVisible by remember { mutableStateOf(false) }

    val cartons =
        formValues[EndToEndTradeFixtures.TRADE_CARTONS_FIELD]?.toIntOrNull()
            ?: EndToEndTradeFixtures.TRADE_DEFAULT_CARTONS
    val moqBlock = remember(cartons) { EndToEndTradeFixtures.buildTradeMoqComposedBlock(cartons) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
            confirmationToast?.let { toastText ->
                RfqConfirmationToastBanner(message = toastText, onDismiss = { confirmationToast = null })
            }
            UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                registry.Render(chipsEnvelope, formValues.toMap(), { k, v -> formValues[k] = v }, onAction)
            }
            registry.Render(moqBlock, formValues.toMap(), { k, v -> formValues[k] = v }, onAction)
            TradeRfqFormCard(
                registry = registry,
                rfqFormEnvelope = rfqFormEnvelope,
                formValues = formValues.toMap(),
                validationErrors = validationErrors,
                onFormChange = { k, v ->
                    formValues[k] = v
                    if (validationErrors.containsKey(k)) validationErrors = validationErrors - k
                },
                onAction = { action ->
                    onAction(action)
                    if (action.id == EndToEndTradeFixtures.TRADE_SUBMIT_RFQ_ACTION) {
                        val errors = EndToEndTradeFixtures.validateTradeRfq(formValues)
                        validationErrors = errors
                        if (errors.isEmpty()) {
                            rfqCount += 1
                            confirmationToast = buildRfqConfirmationMessage(rfqCount, cartons)
                            celebrationVisible = true
                        }
                    }
                },
            )
        }
        UiCelebrationOverlay(
            visible = celebrationVisible,
            title = "RFQ Dispatched!",
            subtitle = confirmationToast ?: "Quotes arriving shortly on Abeta Trade",
            onDismiss = { celebrationVisible = false },
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun rememberTradeDefaultFormValues() =
    remember {
        mutableStateMapOf(
            EndToEndTradeFixtures.TRADE_CARTONS_FIELD to EndToEndTradeFixtures.TRADE_DEFAULT_CARTONS.toString(),
            EndToEndTradeFixtures.TRADE_COMPANY_FIELD to "Abeta Retail Hub Pvt Ltd",
            EndToEndTradeFixtures.TRADE_GSTIN_FIELD to "29AABCA1234F1Z5",
            EndToEndTradeFixtures.TRADE_CITY_FIELD to "Bengaluru",
        )
    }

private fun buildRfqConfirmationMessage(
    rfqCount: Int,
    cartons: Int,
): String = "RFQ #TRD-${RFQ_CODE_BASE + rfqCount} confirmed for $cartons cartons · Sent to 3 GST suppliers"

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun TradeRfqFormCard(
    registry: SnippetRegistry,
    rfqFormEnvelope: dev.abeta.kmp.contracts.SnippetEnvelope,
    formValues: Map<String, String>,
    validationErrors: Map<String, String>,
    onFormChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    CompositionLocalProvider(LocalFormValidationErrors provides validationErrors) {
        registry.Render(
            envelope = rfqFormEnvelope,
            formValues = formValues,
            onFormValueChange = onFormChange,
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RfqConfirmationToastBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.md)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.status.successSubtle, shape)
                .border(ThemeTokens.strokeWidth.thin, colors.status.success, shape)
                .padding(horizontal = spacing.space16, vertical = spacing.space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = ThemeTokens.typography.labelLarge,
            color = colors.text.success,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}
