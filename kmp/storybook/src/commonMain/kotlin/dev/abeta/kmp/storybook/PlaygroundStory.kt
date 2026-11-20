package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiProgress
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.atoms.UiTextField
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private enum class PlaygroundTemplate(
    val label: String,
) {
    METRIC_CARD("SLO Metric Card"),
    ALERT_BANNER("Status Alert Banner"),
    QUICK_FORM("Quick Form Card"),
}

private const val SLO_RATIO = 0.994f

/**
 * Interactive sandbox allowing adopters to switch SDUI contract templates, test actions live,
 * and inspect emitted wire JSON.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun PlaygroundStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var template by remember { mutableStateOf(PlaygroundTemplate.METRIC_CARD) }
    var formInput by remember { mutableStateOf("production-cluster") }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
            PlaygroundTemplate.entries.forEach { item ->
                FilterChip(
                    selected = template == item,
                    onClick = { template = item },
                    label = { Text(item.label) },
                )
            }
        }

        val jsonPreview =
            when (template) {
                PlaygroundTemplate.METRIC_CARD -> renderMetricTemplate(onAction)
                PlaygroundTemplate.ALERT_BANNER -> renderAlertTemplate(onAction)
                PlaygroundTemplate.QUICK_FORM -> renderQuickFormTemplate(formInput, { formInput = it }, onAction)
            }

        JsonContractPreviewCard(
            title = "Serialized SDUI Contract (${template.label})",
            jsonText = jsonPreview,
        )
    }
}

@Composable
private fun renderMetricTemplate(onAction: (ActionRef) -> Unit): String {
    val title = TextAtom(text = "HTTP/2 + Brotli Availability", typography = TypographyTokenRef("title.medium"))
    val metric =
        TextAtom(
            text = "99.94%",
            typography = TypographyTokenRef("display.small"),
            color = ColorTokenRef("text.brand"),
        )
    val delta = TagAtom(label = "+0.18% vs p7d", variant = TagVariant.SUCCESS)
    val progress = ProgressAtom(kind = ProgressKind.LINEAR, progress = SLO_RATIO, label = "30-day SLO target")
    val cta =
        ButtonAtom(
            label = "Refresh Metrics",
            variant = ButtonVariant.OUTLINE,
            size = ButtonSize.SMALL,
            leadingIcon = IconAtom(name = "refresh"),
            onClick =
                ActionRef(
                    id = "playground_refresh",
                    type = ActionTypes.REFRESH_PAGE,
                    payload = buildJsonObject { put("source", "storybook") },
                ),
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText(title)
            UiTag(delta)
        }
        UiSpacer(SpacerAtom(heightDp = 8))
        UiText(metric)
        UiSpacer(SpacerAtom(heightDp = 8))
        UiProgress(progress)
        UiSpacer(SpacerAtom(heightDp = 12))
        UiButton(cta, onAction = onAction)
    }
    return SduiJson.encodeToString(ButtonAtom.serializer(), cta)
}

@Composable
private fun renderAlertTemplate(onAction: (ActionRef) -> Unit): String {
    val tag = TagAtom(label = "H2 + BR ACTIVE", variant = TagVariant.INFO, icon = IconAtom(name = "info"))
    val title = TextAtom(text = "Zero-Release SDUI Ready", typography = TypographyTokenRef("title.large"))
    val body =
        TextAtom(
            text = "OkHttp ALPN HTTP/2 and RFC-7932 Brotli decompression reduce SDUI payload bytes cleanly.",
            typography = TypographyTokenRef("body.medium"),
            color = ColorTokenRef("text.secondary"),
        )
    val cta =
        ButtonAtom(
            label = "Inspect Payload",
            variant = ButtonVariant.PRIMARY,
            onClick =
                ActionRef(
                    id = "playground_open_dialog",
                    type = ActionTypes.OPEN_DIALOG,
                    payload = buildJsonObject { put("title", "Protocol Ready") },
                ),
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiTag(tag)
        UiSpacer(SpacerAtom(heightDp = 8))
        UiText(title)
        UiSpacer(SpacerAtom(heightDp = 4))
        UiText(body)
        UiSpacer(SpacerAtom(heightDp = 12))
        UiButton(cta, onAction = onAction)
    }
    return SduiJson.encodeToString(TextAtom.serializer(), body)
}

@Composable
private fun renderQuickFormTemplate(
    inputValue: String,
    onInputChange: (String) -> Unit,
    onAction: (ActionRef) -> Unit,
): String {
    val field =
        TextFieldAtom(
            fieldId = "env_name",
            label = "Target Environment",
            helperText = "Enter cluster slug to dispatch action payload",
            prefixIcon = IconAtom(name = "settings"),
        )
    val submit =
        ButtonAtom(
            label = "Dispatch Action",
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
            onClick =
                ActionRef(
                    id = "playground_submit",
                    type = ActionTypes.CALL_API,
                    payload = buildJsonObject { put("env", inputValue) },
                ),
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiTextField(atom = field, valueOverride = inputValue, onValueChange = onInputChange, onAction = onAction)
        UiSpacer(SpacerAtom(heightDp = 12))
        UiButton(atom = submit, onAction = onAction)
    }
    return SduiJson.encodeToString(TextFieldAtom.serializer(), field)
}
