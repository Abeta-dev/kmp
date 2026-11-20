package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_AVATAR_SIZE_DP = 40
private const val MIN_AVATAR_SIZE_DP = 20
private const val MAX_INITIALS_LEN = 2
private const val INITIALS_FONT_SCALE = 0.4f

/**
 * Pure helper resolving monogram initials from explicit [initials] or [contentDescription].
 */
fun resolveAvatarInitials(
    initials: String?,
    contentDescription: String?,
): String {
    val explicit = initials?.trim()?.takeIf { it.isNotEmpty() }
    if (explicit != null) {
        return explicit.take(MAX_INITIALS_LEN).uppercase()
    }
    val words =
        contentDescription
            ?.trim()
            ?.split(' ')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    return when {
        words.size >= MAX_INITIALS_LEN -> {
            "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        }

        words.size == 1 -> {
            words[0].take(MAX_INITIALS_LEN).uppercase()
        }

        else -> {
            "?"
        }
    }
}

/**
 * Stateless circular avatar UI atom rendered from an [AvatarAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiAvatar(
    atom: AvatarAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val resolvedSizeDp = (atom.sizeDp ?: DEFAULT_AVATAR_SIZE_DP).coerceAtLeast(MIN_AVATAR_SIZE_DP)
    val bgColor = atom.bgColor.resolve(fallback = colors.brand.container)
    val fgColor = atom.textColor.resolve(fallback = colors.brand.onContainer)
    val clickAction = atom.onClick
    val description = atom.contentDescription?.takeIf { it.isNotBlank() }

    val avatarModifier =
        modifier
            .applyLayoutConfig(atom.layout)
            .size(resolvedSizeDp.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .let { base ->
                if (clickAction != null) {
                    base.clickable { onAction(clickAction) }
                } else {
                    base
                }
            }.let { base ->
                if (description != null) {
                    base.semantics { contentDescription = description }
                } else {
                    base
                }
            }

    val imageUrl = atom.url?.takeIf { it.isNotBlank() }
    val renderer = LocalImageRenderer.current

    Box(
        modifier = avatarModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null && renderer != null) {
            renderer.render(
                atom =
                    ImageAtom(
                        id = atom.id,
                        url = imageUrl,
                        contentDescription = atom.contentDescription,
                        widthDp = resolvedSizeDp,
                        heightDp = resolvedSizeDp,
                        scaleMode = ImageScaleMode.CROP,
                    ),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = resolveAvatarInitials(atom.initials, atom.contentDescription),
                color = fgColor,
                style =
                    typography.titleSmall.copy(
                        fontSize = (resolvedSizeDp * INITIALS_FONT_SCALE).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                maxLines = 1,
            )
        }
    }
}
