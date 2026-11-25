package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlertBannerAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

private const val ACCENT_BAR_WIDTH_DP = 4
private const val BANNER_ICON_SIZE_DP = 18
private const val DISMISS_ICON_SIZE_DP = 14
private const val DISMISS_TOUCH_DP = 28
private const val BANNER_BORDER_ALPHA = 0.25f

private data class ResolvedAlertBannerStyle(
    val background: Color,
    val accent: Color,
    val titleColor: Color,
    val iconColorToken: String,
    val defaultIconName: String,
)

/**
 * Stateless inline semantic alert banner UI atom rendered from an [AlertBannerAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiAlertBanner(
    atom: AlertBannerAtom,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val haptic = LocalUiHapticFeedback.current
    val style = resolveAlertBannerStyle(atom.variant, colors)
    val bannerKey = atom.id ?: "default"

    Row(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .fillMaxWidth()
                .testTag("alert_banner_$bannerKey")
                .alertBannerSurface(style),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        val leadingIcon = atom.icon ?: IconAtom(name = style.defaultIconName)
        UiIcon(
            atom =
                leadingIcon.copy(
                    sizeDp = leadingIcon.sizeDp ?: BANNER_ICON_SIZE_DP,
                    color = leadingIcon.color ?: ColorTokenRef(token = style.iconColorToken),
                ),
        )
        UiAlertBannerContent(
            atom = atom,
            bannerKey = bannerKey,
            style = style,
            modifier = Modifier.weight(1f),
            onActionClick = {
                haptic(UiHapticKind.CLICK)
                atom.onAction?.let(onAction)
            },
        )
        if (atom.dismissible) {
            UiAlertBannerDismissButton(
                title = atom.title,
                bannerKey = bannerKey,
                onClick = {
                    haptic(UiHapticKind.CLICK)
                    onDismiss()
                    atom.onDismiss?.let(onAction)
                },
            )
        }
    }
}

@Composable
private fun Modifier.alertBannerSurface(style: ResolvedAlertBannerStyle): Modifier {
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val shape = RoundedCornerShape(cornerRadius.md)
    return this
        .clip(shape)
        .background(style.background, shape)
        .border(strokeWidth.thin, style.accent.copy(alpha = BANNER_BORDER_ALPHA), shape)
        .drawWithContent {
            drawContent()
            drawRect(
                color = style.accent,
                topLeft = Offset.Zero,
                size = Size(width = ACCENT_BAR_WIDTH_DP.dp.toPx(), height = size.height),
            )
        }.padding(
            start = spacing.space16,
            end = spacing.space12,
            top = spacing.space12,
            bottom = spacing.space12,
        )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiAlertBannerContent(
    atom: AlertBannerAtom,
    bannerKey: String,
    style: ResolvedAlertBannerStyle,
    modifier: Modifier,
    onActionClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = atom.title,
            style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = style.titleColor,
        )
        atom.message?.takeIf { it.isNotBlank() }?.let { messageText ->
            Text(
                text = messageText,
                style = typography.bodySmall,
                color = colors.text.secondary,
            )
        }
        atom.actionLabel?.takeIf { it.isNotBlank() }?.let { actionLabel ->
            val sizing = ThemeTokens.sizing
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                        .minimumInteractiveComponentSize()
                        .testTag("alert_banner_action_$bannerKey")
                        .pressBounce()
                        .clickable(role = Role.Button, onClick = onActionClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = actionLabel,
                    style = typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = style.titleColor,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiAlertBannerDismissButton(
    title: String,
    bannerKey: String,
    onClick: () -> Unit,
) {
    val sizing = ThemeTokens.sizing
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                .minimumInteractiveComponentSize()
                .testTag("alert_banner_dismiss_$bannerKey")
                .semantics { contentDescription = "Dismiss $title" }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(DISMISS_TOUCH_DP.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(
                atom =
                    IconAtom(
                        name = "close",
                        sizeDp = DISMISS_ICON_SIZE_DP,
                        color = ColorTokenRef(token = "icon.secondary"),
                    ),
            )
        }
    }
}

private fun resolveAlertBannerStyle(
    variant: TagVariant,
    colors: ColorTokens,
): ResolvedAlertBannerStyle =
    when (variant) {
        TagVariant.SUCCESS -> {
            ResolvedAlertBannerStyle(
                background = colors.status.successSubtle,
                accent = colors.status.success,
                titleColor = colors.text.success,
                iconColorToken = "icon.success",
                defaultIconName = "check",
            )
        }

        TagVariant.WARNING -> {
            ResolvedAlertBannerStyle(
                background = colors.status.warningSubtle,
                accent = colors.status.warning,
                titleColor = colors.text.warning,
                iconColorToken = "icon.warning",
                defaultIconName = "warning",
            )
        }

        TagVariant.ERROR -> {
            ResolvedAlertBannerStyle(
                background = colors.status.errorSubtle,
                accent = colors.status.error,
                titleColor = colors.text.error,
                iconColorToken = "icon.error",
                defaultIconName = "error",
            )
        }

        TagVariant.BRAND -> {
            ResolvedAlertBannerStyle(
                background = colors.brand.container,
                accent = colors.brand.primary,
                titleColor = colors.brand.onContainer,
                iconColorToken = "icon.brand",
                defaultIconName = "info",
            )
        }

        TagVariant.NEUTRAL -> {
            ResolvedAlertBannerStyle(
                background = colors.surface.secondary,
                accent = colors.border.strong,
                titleColor = colors.text.primary,
                iconColorToken = "icon.secondary",
                defaultIconName = "info",
            )
        }

        TagVariant.INFO -> {
            ResolvedAlertBannerStyle(
                background = colors.status.infoSubtle,
                accent = colors.status.info,
                titleColor = colors.text.info,
                iconColorToken = "icon.info",
                defaultIconName = "info",
            )
        }
    }
