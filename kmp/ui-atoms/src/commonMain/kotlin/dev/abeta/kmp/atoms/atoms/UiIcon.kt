package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_ICON_SIZE_DP = 20
private const val MIN_ICON_SIZE_DP = 12
private const val FALLBACK_GLYPH_SCALE = 0.5f
private const val VIEWPORT_SIZE = 24f
private const val STROKE_WIDTH = 2f

/**
 * Extensible port for resolving named icons into Compose [ImageVector] instances.
 *
 * Host applications can supply custom icon sets via [LocalIconResolver].
 */
fun interface UiIconResolver {
    fun resolve(name: String): ImageVector?
}

/**
 * [ProvidableCompositionLocal] allowing host apps to extend or override icon lookup.
 */
val LocalIconResolver: ProvidableCompositionLocal<UiIconResolver?> =
    staticCompositionLocalOf { null }

/**
 * Built-in multiplatform stroke-based vector icon registry for common SDUI icon names.
 */
object UiBuiltinIcons {
    private val icons: Map<String, ImageVector> =
        mapOf(
            "arrow_back" to
                strokeIcon("arrow_back") {
                    moveTo(19f, 12f)
                    lineTo(5f, 12f)
                    moveTo(12f, 19f)
                    lineTo(5f, 12f)
                    lineTo(12f, 5f)
                },
            "arrow_forward" to
                strokeIcon("arrow_forward") {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                    moveTo(12f, 5f)
                    lineTo(19f, 12f)
                    lineTo(12f, 19f)
                },
            "chevron_right" to
                strokeIcon("chevron_right") {
                    moveTo(9f, 6f)
                    lineTo(15f, 12f)
                    lineTo(9f, 18f)
                },
            "chevron_left" to
                strokeIcon("chevron_left") {
                    moveTo(15f, 6f)
                    lineTo(9f, 12f)
                    lineTo(15f, 18f)
                },
            "close" to
                strokeIcon("close") {
                    moveTo(6f, 6f)
                    lineTo(18f, 18f)
                    moveTo(18f, 6f)
                    lineTo(6f, 18f)
                },
            "check" to
                strokeIcon("check") {
                    moveTo(5f, 12f)
                    lineTo(10f, 17f)
                    lineTo(19f, 7f)
                },
            "add" to
                strokeIcon("add") {
                    moveTo(12f, 5f)
                    lineTo(12f, 19f)
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                },
            "remove" to
                strokeIcon("remove") {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                },
            "menu" to
                strokeIcon("menu") {
                    moveTo(4f, 6f)
                    lineTo(20f, 6f)
                    moveTo(4f, 12f)
                    lineTo(20f, 12f)
                    moveTo(4f, 18f)
                    lineTo(20f, 18f)
                },
            "search" to
                strokeIcon("search") {
                    moveTo(11f, 4f)
                    arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 18f)
                    arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 4f)
                    moveTo(20f, 20f)
                    lineTo(16f, 16f)
                },
            "info" to
                strokeIcon("info") {
                    moveTo(12f, 3f)
                    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 21f)
                    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 3f)
                    moveTo(12f, 11f)
                    lineTo(12f, 16f)
                    moveTo(12f, 8f)
                    lineTo(12f, 8.2f)
                },
            "warning" to
                strokeIcon("warning") {
                    moveTo(12f, 3f)
                    lineTo(21f, 19f)
                    lineTo(3f, 19f)
                    close()
                    moveTo(12f, 9f)
                    lineTo(12f, 13f)
                    moveTo(12f, 16f)
                    lineTo(12f, 16.2f)
                },
            "error" to
                strokeIcon("error") {
                    moveTo(12f, 3f)
                    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 21f)
                    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 3f)
                    moveTo(9f, 9f)
                    lineTo(15f, 15f)
                    moveTo(15f, 9f)
                    lineTo(9f, 15f)
                },
            "home" to
                strokeIcon("home") {
                    moveTo(4f, 11f)
                    lineTo(12f, 4f)
                    lineTo(20f, 11f)
                    lineTo(20f, 20f)
                    lineTo(4f, 20f)
                    close()
                },
            "person" to
                strokeIcon("person") {
                    moveTo(12f, 5f)
                    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 12f)
                    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 5f)
                    moveTo(5f, 20f)
                    curveTo(5f, 16f, 8f, 14f, 12f, 14f)
                    curveTo(16f, 14f, 19f, 16f, 19f, 20f)
                },
            "settings" to
                strokeIcon("settings") {
                    moveTo(12f, 8.5f)
                    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 15.5f)
                    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 8.5f)
                    moveTo(12f, 3f)
                    lineTo(12f, 5.5f)
                    moveTo(12f, 18.5f)
                    lineTo(12f, 21f)
                    moveTo(3f, 12f)
                    lineTo(5.5f, 12f)
                    moveTo(18.5f, 12f)
                    lineTo(21f, 12f)
                },
            "notifications" to
                strokeIcon("notifications") {
                    moveTo(6f, 16f)
                    lineTo(18f, 16f)
                    lineTo(17f, 10f)
                    arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 10f)
                    close()
                    moveTo(10f, 19f)
                    lineTo(14f, 19f)
                },
            "refresh" to
                strokeIcon("refresh") {
                    moveTo(19f, 12f)
                    arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.95f, 7.05f)
                    moveTo(19f, 4f)
                    lineTo(19f, 8f)
                    lineTo(15f, 8f)
                },
            "edit" to
                strokeIcon("edit") {
                    moveTo(4f, 20f)
                    lineTo(8f, 19f)
                    lineTo(19f, 8f)
                    lineTo(16f, 5f)
                    lineTo(5f, 16f)
                    close()
                },
            "delete" to
                strokeIcon("delete") {
                    moveTo(5f, 7f)
                    lineTo(19f, 7f)
                    moveTo(9f, 7f)
                    lineTo(9f, 4f)
                    lineTo(15f, 4f)
                    lineTo(15f, 7f)
                    moveTo(7f, 7f)
                    lineTo(8f, 20f)
                    lineTo(16f, 20f)
                    lineTo(17f, 7f)
                },
            "star" to
                strokeIcon("star") {
                    moveTo(12f, 3f)
                    lineTo(14.8f, 8.8f)
                    lineTo(21f, 9.7f)
                    lineTo(16.5f, 14.1f)
                    lineTo(17.6f, 20.3f)
                    lineTo(12f, 17.3f)
                    lineTo(6.4f, 20.3f)
                    lineTo(7.5f, 14.1f)
                    lineTo(3f, 9.7f)
                    lineTo(9.2f, 8.8f)
                    close()
                },
            "more_vert" to
                strokeIcon("more_vert") {
                    moveTo(12f, 6f)
                    lineTo(12f, 6.2f)
                    moveTo(12f, 12f)
                    lineTo(12f, 12.2f)
                    moveTo(12f, 18f)
                    lineTo(12f, 18.2f)
                },
        )

    /** Returns the sorted list of built-in icon names. */
    fun names(): List<String> = icons.keys.sorted()

    /** Resolves a built-in vector icon by [name] (case-insensitive). */
    fun resolve(name: String): ImageVector? = icons[name.trim().lowercase()]
}

private fun strokeIcon(
    name: String,
    block: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = VIEWPORT_SIZE.dp,
            defaultHeight = VIEWPORT_SIZE.dp,
            viewportWidth = VIEWPORT_SIZE,
            viewportHeight = VIEWPORT_SIZE,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

/**
 * Stateless icon UI atom rendered from an [IconAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiIcon(
    atom: IconAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val stroke = ThemeTokens.strokeWidth
    val sizing = ThemeTokens.sizing
    val haptic = LocalUiHapticFeedback.current
    val resolvedSize = (atom.sizeDp ?: DEFAULT_ICON_SIZE_DP).coerceAtLeast(MIN_ICON_SIZE_DP).dp
    val resolvedTint = atom.color.resolve(fallback = colors.icon.primary)
    val customResolver = LocalIconResolver.current
    val vector = customResolver?.resolve(atom.name) ?: UiBuiltinIcons.resolve(atom.name)
    val clickAction = atom.onClick
    val baseModifier = modifier.applyLayoutConfig(atom.layout)

    if (clickAction != null) {
        Box(
            modifier =
                baseModifier
                    .defaultMinSize(
                        minWidth = sizing.minTouchTarget,
                        minHeight = sizing.minTouchTarget,
                    ).clickable(
                        role = Role.Button,
                        onClickLabel = atom.contentDescription,
                    ) {
                        haptic(UiHapticKind.CLICK)
                        onAction(clickAction)
                    },
            contentAlignment = Alignment.Center,
        ) {
            UiIconGraphic(
                atom = atom,
                vector = vector,
                resolvedSize = resolvedSize,
                resolvedTint = resolvedTint,
                strokeWidth = stroke.thin,
                modifier = Modifier.size(resolvedSize),
            )
        }
    } else {
        UiIconGraphic(
            atom = atom,
            vector = vector,
            resolvedSize = resolvedSize,
            resolvedTint = resolvedTint,
            strokeWidth = stroke.thin,
            modifier = baseModifier.size(resolvedSize),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiIconGraphic(
    atom: IconAtom,
    vector: ImageVector?,
    resolvedSize: Dp,
    resolvedTint: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = atom.contentDescription,
            modifier = modifier,
            tint = resolvedTint,
        )
    } else {
        val glyph =
            atom.name
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "?"
        Box(
            modifier = modifier.border(strokeWidth, resolvedTint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                color = resolvedTint,
                fontSize = (resolvedSize.value * FALLBACK_GLYPH_SCALE).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
