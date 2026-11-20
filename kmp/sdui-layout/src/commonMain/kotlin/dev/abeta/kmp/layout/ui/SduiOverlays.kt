package dev.abeta.kmp.layout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.tokens.ThemeTokens

private const val TOAST_ICON_SIZE_DP = 18

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun OverlayHost(
    overlay: ActionOutcome.OverlayRequested,
    onAction: (ActionRef) -> Unit,
    onDismiss: () -> Unit,
) {
    when (overlay.presentation) {
        OverlayPresentation.DIALOG -> {
            OverlayDialog(overlay = overlay, onAction = onAction, onDismiss = onDismiss)
        }

        OverlayPresentation.BOTTOM_SHEET -> {
            OverlayBottomSheet(overlay = overlay, onAction = onAction, onDismiss = onDismiss)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun OverlayDialog(
    overlay: ActionOutcome.OverlayRequested,
    onAction: (ActionRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography

    AlertDialog(
        onDismissRequest = {
            if (overlay.dismissible) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = overlay.title,
                style = typography.titleLarge,
                color = colors.text.primary,
            )
        },
        text = {
            overlay.message?.let { msg ->
                Text(
                    text = msg,
                    style = typography.bodyMedium,
                    color = colors.text.secondary,
                )
            }
        },
        confirmButton = {
            OverlayPositiveAction(overlay = overlay, onAction = onAction, onDismiss = onDismiss)
        },
        dismissButton = {
            overlay.negativeAction?.let { negative ->
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Cancel",
                            variant = ButtonVariant.GHOST,
                            onClick = negative,
                        ),
                    onAction = onAction,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun OverlayBottomSheet(
    overlay: ActionOutcome.OverlayRequested,
    onAction: (ActionRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { target -> overlay.dismissible || target != SheetValue.Hidden },
        )

    ModalBottomSheet(
        onDismissRequest = {
            if (overlay.dismissible) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = colors.surface.primary,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.space24, vertical = spacing.space16),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Text(
                text = overlay.title,
                style = typography.titleLarge,
                color = colors.text.primary,
            )
            overlay.message?.let { msg ->
                Text(
                    text = msg,
                    style = typography.bodyMedium,
                    color = colors.text.secondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                overlay.negativeAction?.let { negative ->
                    UiButton(
                        atom =
                            ButtonAtom(
                                label = "Cancel",
                                variant = ButtonVariant.GHOST,
                                onClick = negative,
                            ),
                        onAction = onAction,
                    )
                }
                OverlayPositiveAction(overlay = overlay, onAction = onAction, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun OverlayPositiveAction(
    overlay: ActionOutcome.OverlayRequested,
    onAction: (ActionRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val typography = ThemeTokens.typography
    val positive = overlay.positiveAction
    if (positive != null) {
        UiButton(
            atom =
                ButtonAtom(
                    label = "OK",
                    variant = ButtonVariant.PRIMARY,
                    onClick = positive,
                ),
            onAction = onAction,
        )
    } else {
        TextButton(onClick = onDismiss) {
            Text(text = "OK", style = typography.labelLarge)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun ToastBanner(
    toast: ActionOutcome.ToastRequested,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    val (bg, border, iconName) =
        when (toast.variant.lowercase()) {
            "success" -> Triple(colors.status.successSubtle, colors.status.success, "check")
            "error" -> Triple(colors.status.errorSubtle, colors.status.error, "error")
            "warning" -> Triple(colors.status.warningSubtle, colors.status.warning, "warning")
            else -> Triple(colors.status.infoSubtle, colors.status.info, "info")
        }
    val shape = RoundedCornerShape(cornerRadius.md)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clip(shape)
                .background(bg, shape)
                .border(strokeWidth.thin, border, shape)
                .padding(horizontal = spacing.space16, vertical = spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        UiIcon(
            atom =
                IconAtom(
                    name = iconName,
                    sizeDp = TOAST_ICON_SIZE_DP,
                ),
        )
        Text(
            text = toast.message,
            modifier = Modifier.weight(1f),
            style = typography.bodyMedium,
            color = colors.text.primary,
        )
        TextButton(onClick = onDismiss) {
            Text(
                text = "Dismiss",
                style = typography.labelSmall,
                color = colors.text.secondary,
            )
        }
    }
}
