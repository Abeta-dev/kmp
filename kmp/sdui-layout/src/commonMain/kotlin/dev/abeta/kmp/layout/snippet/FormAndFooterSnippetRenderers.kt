package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCheckbox
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiSlider
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiSwitch
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.atoms.UiTextField
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.DividerSnippetData
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacerSnippetData
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class FormSnippetRenderer(
    json: Json,
) : SnippetRenderer<FormSnippetData> {
    override val snippetType: String = SnippetTypes.FORM
    private val decoder = kotlinxSnippetDecoder<FormSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<FormSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: FormSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val validationErrors = LocalFormValidationErrors.current
        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                model.title?.let { title -> UiText(atom = title, onAction = onAction) }
                model.description?.let { description -> UiText(atom = description, onAction = onAction) }
                FormTextFields(
                    model = model,
                    formValues = formValues,
                    validationErrors = validationErrors,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                )
                FormInteractiveControls(
                    model = model,
                    formValues = formValues,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                )
                FormActionButtons(model = model, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun FormTextFields(
        model: FormSnippetData,
        formValues: Map<String, String>,
        validationErrors: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        model.fields.forEach { field ->
            val fieldError = validationErrors[field.fieldId]
            val effectiveField = if (fieldError != null) field.copy(errorText = fieldError) else field
            UiTextField(
                atom = effectiveField,
                modifier = Modifier.fillMaxWidth(),
                valueOverride = formValues[field.fieldId],
                onValueChange = { updated -> onFormValueChange(field.fieldId, updated) },
                onAction = onAction,
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun FormInteractiveControls(
        model: FormSnippetData,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        model.switches.forEach { switchAtom ->
            val checkedOverride = formValues[switchAtom.fieldId]?.lowercase()?.toBooleanStrictOrNull()
            UiSwitch(
                atom = switchAtom,
                modifier = Modifier.fillMaxWidth(),
                checkedOverride = checkedOverride,
                onCheckedChange = { updated -> onFormValueChange(switchAtom.fieldId, updated.toString()) },
                onAction = onAction,
            )
        }
        model.checkboxes.forEach { checkboxAtom ->
            val checkedOverride = formValues[checkboxAtom.fieldId]?.lowercase()?.toBooleanStrictOrNull()
            UiCheckbox(
                atom = checkboxAtom,
                modifier = Modifier.fillMaxWidth(),
                checkedOverride = checkedOverride,
                onCheckedChange = { updated -> onFormValueChange(checkboxAtom.fieldId, updated.toString()) },
                onAction = onAction,
            )
        }
        model.sliders.forEach { sliderAtom ->
            val valueOverride = formValues[sliderAtom.fieldId]?.toFloatOrNull()
            UiSlider(
                atom = sliderAtom,
                modifier = Modifier.fillMaxWidth(),
                valueOverride = valueOverride,
                onValueChange = { updated -> onFormValueChange(sliderAtom.fieldId, updated.toString()) },
                onAction = onAction,
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun FormActionButtons(
        model: FormSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        if (model.cancelButton == null && model.submitButton == null) return
        val spacing = ThemeTokens.spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            model.cancelButton?.let { cancel -> UiButton(atom = cancel, onAction = onAction) }
            model.submitButton?.let { submit -> UiButton(atom = submit, onAction = onAction) }
        }
    }
}

internal class DividerSnippetRenderer(
    json: Json,
) : SnippetRenderer<DividerSnippetData> {
    override val snippetType: String = SnippetTypes.DIVIDER
    private val decoder = kotlinxSnippetDecoder<DividerSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<DividerSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: DividerSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout)) {
            UiDivider(atom = model.divider)
        }
    }
}

internal class SpacerSnippetRenderer(
    json: Json,
) : SnippetRenderer<SpacerSnippetData> {
    override val snippetType: String = SnippetTypes.SPACER
    private val decoder = kotlinxSnippetDecoder<SpacerSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<SpacerSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: SpacerSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        UiSpacer(atom = model.spacer)
    }
}
