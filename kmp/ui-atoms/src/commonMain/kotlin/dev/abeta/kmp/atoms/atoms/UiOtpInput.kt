package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.OtpInputAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.StrokeWidthTokens
import dev.abeta.kmp.tokens.ThemeTokens

private const val MIN_OTP_LENGTH = 4
private const val MAX_OTP_LENGTH = 8
private const val COMPACT_OTP_THRESHOLD = 6
private const val OTP_CELL_WIDTH_DP = 44
private const val OTP_COMPACT_CELL_WIDTH_DP = 36
private const val OTP_CELL_HEIGHT_DP = 52
private const val OTP_FILLED_POP_SCALE = 1.04f
private const val FULL_SCALE = 1f
private const val MASKED_BULLET = "•"
private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Pure helper clamping an OTP box count into the supported 4–8 digit range.
 */
fun resolveOtpLength(length: Int): Int = length.coerceIn(MIN_OTP_LENGTH, MAX_OTP_LENGTH)

/**
 * Pure helper keeping only ASCII/Unicode decimal digits and capping at the resolved OTP length.
 */
fun sanitizeOtpDigits(
    raw: String,
    maxLength: Int,
): String = raw.filter { it.isDigit() }.take(resolveOtpLength(maxLength))

/**
 * Stateless fixed-length OTP / PIN digit box input UI atom rendered from an [OtpInputAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiOtpInput(
    atom: OtpInputAtom,
    modifier: Modifier = Modifier,
    valueOverride: String? = null,
    onValueChange: (String) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val digitCount = resolveOtpLength(atom.length)
    val currentValue = sanitizeOtpDigits(valueOverride ?: atom.initialValue, digitCount)
    val isError = !atom.errorText.isNullOrBlank()
    val supportingMessage = atom.errorText?.takeIf { it.isNotBlank() } ?: atom.helperText?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier.applyLayoutConfig(atom.layout),
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        atom.label?.takeIf { it.isNotBlank() }?.let { fieldLabel ->
            Text(
                text = fieldLabel,
                style = typography.labelLarge,
                color = if (atom.enabled) colors.text.primary else colors.text.disabled,
            )
        }
        UiOtpBasicField(
            atom = atom,
            currentValue = currentValue,
            digitCount = digitCount,
            isError = isError,
            onValueChange = onValueChange,
            onAction = onAction,
        )
        if (supportingMessage != null) {
            Text(
                text = supportingMessage,
                style = typography.labelSmall,
                color = if (isError) colors.text.error else colors.text.tertiary,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiOtpBasicField(
    atom: OtpInputAtom,
    currentValue: String,
    digitCount: Int,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val haptic = LocalUiHapticFeedback.current
    var isFocused by remember(atom.fieldId) { mutableStateOf(false) }
    var completedCode by remember(atom.fieldId, digitCount) {
        mutableStateOf(if (currentValue.length == digitCount) currentValue else null)
    }

    BasicTextField(
        value = TextFieldValue(text = currentValue, selection = TextRange(currentValue.length)),
        onValueChange = { fieldValue ->
            val sanitized = sanitizeOtpDigits(fieldValue.text, digitCount)
            if (sanitized != currentValue) {
                onValueChange(sanitized)
                if (sanitized.length == digitCount && sanitized != completedCode) {
                    completedCode = sanitized
                    haptic(UiHapticKind.SUCCESS)
                    atom.onComplete?.let(onAction)
                } else if (sanitized.length < digitCount) {
                    completedCode = null
                    haptic(UiHapticKind.TICK)
                }
            }
        },
        enabled = atom.enabled,
        singleLine = true,
        cursorBrush = SolidColor(colors.border.focus),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
        modifier =
            Modifier
                .testTag("otp_${atom.fieldId}")
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier =
                        Modifier
                            .size(1.dp)
                            .alpha(0f),
                ) {
                    innerTextField()
                }
                UiOtpCellsRow(
                    value = currentValue,
                    digitCount = digitCount,
                    masked = atom.masked,
                    enabled = atom.enabled,
                    focused = isFocused,
                    isError = isError,
                )
            }
        },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiOtpCellsRow(
    value: String,
    digitCount: Int,
    masked: Boolean,
    enabled: Boolean,
    focused: Boolean,
    isError: Boolean,
) {
    val spacing = ThemeTokens.spacing
    val cellGap = if (digitCount > COMPACT_OTP_THRESHOLD) spacing.space4 else spacing.space8
    val cellWidth = if (digitCount > COMPACT_OTP_THRESHOLD) OTP_COMPACT_CELL_WIDTH_DP.dp else OTP_CELL_WIDTH_DP.dp
    val activeIndex = value.length.coerceAtMost(digitCount - 1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(cellGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(digitCount) { index ->
            val digitChar = value.getOrNull(index)
            val isActive = enabled && focused && index == activeIndex
            UiOtpDigitCell(
                glyph =
                    when {
                        digitChar == null -> ""
                        masked -> MASKED_BULLET
                        else -> digitChar.toString()
                    },
                isFilled = digitChar != null,
                isActive = isActive,
                isError = isError,
                enabled = enabled,
                cellWidth = cellWidth,
            )
        }
    }
}

private data class ResolvedOtpCellStyle(
    val background: Color,
    val border: Color,
    val borderWidth: Dp,
    val textColor: Color,
)

private fun resolveOtpCellStyle(
    isFilled: Boolean,
    isActive: Boolean,
    isError: Boolean,
    enabled: Boolean,
    colors: ColorTokens,
    strokeWidth: StrokeWidthTokens,
): ResolvedOtpCellStyle {
    val bgColor =
        when {
            !enabled -> colors.input.disabledBackground
            isActive -> colors.surface.brandSubtle
            else -> colors.input.background
        }
    val borderColor =
        when {
            !enabled -> colors.border.subtle
            isError -> colors.input.errorBorder
            isActive -> colors.input.focusBorder
            isFilled -> colors.border.strong
            else -> colors.input.border
        }
    return ResolvedOtpCellStyle(
        background = bgColor,
        border = borderColor,
        borderWidth = if (isActive || isError) strokeWidth.thick else strokeWidth.thin,
        textColor = if (enabled) colors.text.primary else colors.text.disabled,
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiOtpDigitCell(
    glyph: String,
    isFilled: Boolean,
    isActive: Boolean,
    isError: Boolean,
    enabled: Boolean,
    cellWidth: Dp,
) {
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val motion = ThemeTokens.motion
    val shape = RoundedCornerShape(cornerRadius.sm)
    val style =
        resolveOtpCellStyle(
            isFilled = isFilled,
            isActive = isActive,
            isError = isError,
            enabled = enabled,
            colors = ThemeTokens.colors,
            strokeWidth = ThemeTokens.strokeWidth,
        )
    val scaleState =
        animateFloatAsState(
            targetValue = if (isActive && !motion.reducedMotion) OTP_FILLED_POP_SCALE else FULL_SCALE,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "otp_cell_scale",
        )

    Box(
        modifier =
            Modifier
                .width(cellWidth)
                .height(OTP_CELL_HEIGHT_DP.dp)
                .graphicsLayer {
                    val scale = scaleState.value
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(style.background, shape)
                .border(style.borderWidth, style.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style =
                typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
                ),
            color = style.textColor,
            maxLines = 1,
        )
    }
}
