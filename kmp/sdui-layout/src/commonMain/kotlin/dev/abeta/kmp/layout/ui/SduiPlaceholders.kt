package dev.abeta.kmp.layout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiSkeleton
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.tokens.ThemeTokens

private const val SKELETON_AVATAR_DP = 44
private const val SKELETON_TITLE_WIDTH_DP = 180
private const val SKELETON_TITLE_HEIGHT_DP = 16
private const val SKELETON_SUBTITLE_WIDTH_DP = 120
private const val SKELETON_SUBTITLE_HEIGHT_DP = 12
private const val SKELETON_CARD_HEIGHT_DP = 120
private const val SKELETON_BODY_TEXT_WIDTH_DP = 240
private const val SKELETON_BODY_TEXT_HEIGHT_DP = 14
private const val SKELETON_SECONDARY_CARD_HEIGHT_DP = 96

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SkeletonScreenPlaceholder(modifier: Modifier = Modifier) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background.primary)
                .padding(spacing.space16)
                .testTag("sdui_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(spacing.space16),
    ) {
        SkeletonHeaderRow()
        UiSkeleton(
            atom =
                SkeletonAtom(
                    kind = SkeletonKind.RECTANGLE,
                    heightDp = SKELETON_CARD_HEIGHT_DP,
                ),
        )
        UiSkeleton(
            atom =
                SkeletonAtom(
                    kind = SkeletonKind.TEXT,
                    widthDp = SKELETON_BODY_TEXT_WIDTH_DP,
                    heightDp = SKELETON_BODY_TEXT_HEIGHT_DP,
                ),
        )
        UiSkeleton(
            atom =
                SkeletonAtom(
                    kind = SkeletonKind.RECTANGLE,
                    heightDp = SKELETON_SECONDARY_CARD_HEIGHT_DP,
                ),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SkeletonHeaderRow() {
    val spacing = ThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        UiSkeleton(
            atom =
                SkeletonAtom(
                    kind = SkeletonKind.CIRCLE,
                    widthDp = SKELETON_AVATAR_DP,
                    heightDp = SKELETON_AVATAR_DP,
                ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            UiSkeleton(
                atom =
                    SkeletonAtom(
                        kind = SkeletonKind.TEXT,
                        widthDp = SKELETON_TITLE_WIDTH_DP,
                        heightDp = SKELETON_TITLE_HEIGHT_DP,
                    ),
            )
            UiSkeleton(
                atom =
                    SkeletonAtom(
                        kind = SkeletonKind.TEXT,
                        widthDp = SKELETON_SUBTITLE_WIDTH_DP,
                        heightDp = SKELETON_SUBTITLE_HEIGHT_DP,
                    ),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun UnknownSnippetPlaceholder(envelope: SnippetEnvelope) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    UiSurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Unsupported snippet type '${envelope.snippetType}' (${envelope.snippetId})",
            style = typography.labelSmall,
            color = colors.text.tertiary,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun DecodeErrorPlaceholder(
    envelope: SnippetEnvelope,
    error: AppError,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    UiSurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Failed to decode '${envelope.snippetType}': ${error.message}",
            style = typography.labelSmall,
            color = colors.text.error,
        )
    }
}
