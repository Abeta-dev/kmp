package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Pure helper truncating [value] to [maxLength] if specified and non-negative.
 */
fun enforceMaxLength(
    value: String,
    maxLength: Int?,
): String =
    if (maxLength != null && maxLength >= 0 && value.length > maxLength) {
        value.take(maxLength)
    } else {
        value
    }

/**
 * Pure mapper from [KeyboardKind] wire enum to Compose [KeyboardType].
 */
fun resolveKeyboardType(kind: KeyboardKind): KeyboardType =
    when (kind) {
        KeyboardKind.TEXT -> KeyboardType.Text
        KeyboardKind.NUMBER -> KeyboardType.Number
        KeyboardKind.EMAIL -> KeyboardType.Email
        KeyboardKind.PHONE -> KeyboardType.Phone
        KeyboardKind.PASSWORD -> KeyboardType.Password
        KeyboardKind.DECIMAL -> KeyboardType.Decimal
        KeyboardKind.URI -> KeyboardType.Uri
    }

/**
 * Stateless text input UI atom rendered from a [TextFieldAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiTextField(
    atom: TextFieldAtom,
    modifier: Modifier = Modifier,
    valueOverride: String? = null,
    onValueChange: (String) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val currentValue = enforceMaxLength(valueOverride ?: atom.initialValue, atom.maxLength)
    Column(modifier = modifier.applyLayoutConfig(atom.layout)) {
        UiTextFieldInput(
            atom = atom,
            value = currentValue,
            onValueChange = onValueChange,
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiTextFieldInput(
    atom: TextFieldAtom,
    value: String,
    onValueChange: (String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val isError = !atom.errorText.isNullOrBlank()
    val supportingMessage = atom.errorText?.takeIf { it.isNotBlank() } ?: atom.helperText
    val visualTransformation =
        if (atom.keyboardType == KeyboardKind.PASSWORD) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }

    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(enforceMaxLength(raw, atom.maxLength))
            atom.onValueChange?.let(onAction)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = atom.enabled,
        readOnly = atom.readOnly,
        textStyle = typography.bodyLarge.copy(color = colors.text.primary),
        label = textFieldTextSlot(text = atom.label, style = typography.bodyMedium),
        placeholder = textFieldTextSlot(atom.placeholder, typography.bodyLarge, colors.text.tertiary),
        leadingIcon = textFieldIconSlot(atom.prefixIcon, onAction),
        trailingIcon = textFieldIconSlot(atom.suffixIcon, onAction),
        supportingText =
            textFieldTextSlot(
                text = supportingMessage,
                style = typography.labelSmall,
                color = if (isError) colors.text.error else colors.text.tertiary,
            ),
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = resolveKeyboardType(atom.keyboardType),
                imeAction = resolveImeAction(atom),
            ),
        keyboardActions =
            KeyboardActions(
                onDone = { atom.onSubmit?.let(onAction) },
                onSend = { atom.onSubmit?.let(onAction) },
                onSearch = { atom.onSubmit?.let(onAction) },
            ),
        singleLine = atom.singleLine,
        maxLines = atom.maxLines ?: if (atom.singleLine) 1 else Int.MAX_VALUE,
        colors = resolveTextFieldColors(colors),
    )
}

private fun resolveImeAction(atom: TextFieldAtom): ImeAction =
    when {
        atom.onSubmit != null -> ImeAction.Done
        atom.singleLine -> ImeAction.Next
        else -> ImeAction.Default
    }

private fun textFieldTextSlot(
    text: String?,
    style: TextStyle,
    color: Color = Color.Unspecified,
): (@Composable () -> Unit)? =
    text?.let { content ->
        {
            Text(
                text = content,
                style = style,
                color = color,
            )
        }
    }

private fun textFieldIconSlot(
    icon: IconAtom?,
    onAction: (ActionRef) -> Unit,
): (@Composable () -> Unit)? =
    icon?.let { spec ->
        {
            UiIcon(atom = spec, onAction = onAction)
        }
    }

@Composable
private fun resolveTextFieldColors(colors: ColorTokens): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.text.primary,
        unfocusedTextColor = colors.text.primary,
        disabledTextColor = colors.text.disabled,
        focusedBorderColor = colors.border.focus,
        unfocusedBorderColor = colors.border.default,
        disabledBorderColor = colors.border.subtle,
        errorBorderColor = colors.border.error,
        focusedLabelColor = colors.text.brand,
        unfocusedLabelColor = colors.text.secondary,
        errorLabelColor = colors.text.error,
        cursorColor = colors.border.focus,
    )
