@file:Suppress("MagicNumber")

package dev.abeta.kmp.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Responsive horizontal viewport classification aligned with adaptive tablet, iPad, foldable,
 * desktop, and watch form factors.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Responsive vertical viewport classification.
 */
enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Coarse physical form-factor classification derived from viewport bounds or wearable flag.
 */
enum class DeviceFormFactor {
    WATCH,
    PHONE,
    TABLET,
    DESKTOP_WIDE,
}

/**
 * Immutable responsive layout metrics injected via [LocalAdaptiveLayoutMetrics].
 */
@Immutable
data class AdaptiveLayoutMetrics(
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val formFactor: DeviceFormFactor,
    val maxContentWidth: Dp,
    val recommendedGridColumns: Int,
    val useTwoPane: Boolean,
)

private const val COMPACT_MAX_WIDTH_DP = 599
private const val MEDIUM_MAX_WIDTH_DP = 839
private const val COMPACT_MAX_HEIGHT_DP = 479
private const val MEDIUM_MAX_HEIGHT_DP = 899
private const val DEFAULT_PHONE_WIDTH_DP = 390
private const val DEFAULT_PHONE_HEIGHT_DP = 844

/**
 * Pure breakpoint resolver mapping viewport dimensions in dp and wearable hint into
 * deterministic [AdaptiveLayoutMetrics].
 */
fun resolveAdaptiveLayoutMetrics(
    widthDp: Int,
    heightDp: Int,
    isWatch: Boolean = false,
): AdaptiveLayoutMetrics {
    val safeWidth = widthDp.coerceAtLeast(0)
    val safeHeight = heightDp.coerceAtLeast(0)
    val widthClass = classifyWidthClass(safeWidth)
    val heightClass = classifyHeightClass(safeHeight)
    val formFactor = classifyFormFactor(widthClass, isWatch)
    val (maxContentWidth, recommendedColumns, useTwoPane) = defaultsForFormFactor(formFactor)

    return AdaptiveLayoutMetrics(
        widthClass = widthClass,
        heightClass = heightClass,
        formFactor = formFactor,
        maxContentWidth = maxContentWidth,
        recommendedGridColumns = recommendedColumns,
        useTwoPane = useTwoPane,
    )
}

internal fun resolveViewportDimensionDp(
    value: Dp,
    fallbackDp: Int,
): Int =
    if (value.isSpecified && value.value.isFinite()) {
        value.value.toInt().coerceAtLeast(0)
    } else {
        fallbackDp
    }

private fun classifyWidthClass(widthDp: Int): WindowWidthClass =
    when {
        widthDp <= COMPACT_MAX_WIDTH_DP -> WindowWidthClass.COMPACT
        widthDp <= MEDIUM_MAX_WIDTH_DP -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }

private fun classifyHeightClass(heightDp: Int): WindowHeightClass =
    when {
        heightDp <= COMPACT_MAX_HEIGHT_DP -> WindowHeightClass.COMPACT
        heightDp <= MEDIUM_MAX_HEIGHT_DP -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }

private fun classifyFormFactor(
    widthClass: WindowWidthClass,
    isWatch: Boolean,
): DeviceFormFactor =
    when {
        isWatch -> DeviceFormFactor.WATCH
        widthClass == WindowWidthClass.COMPACT -> DeviceFormFactor.PHONE
        widthClass == WindowWidthClass.MEDIUM -> DeviceFormFactor.TABLET
        else -> DeviceFormFactor.DESKTOP_WIDE
    }

private fun defaultsForFormFactor(formFactor: DeviceFormFactor): Triple<Dp, Int, Boolean> =
    when (formFactor) {
        DeviceFormFactor.WATCH -> Triple(280.dp, 1, false)
        DeviceFormFactor.PHONE -> Triple(600.dp, 2, false)
        DeviceFormFactor.TABLET -> Triple(780.dp, 3, false)
        DeviceFormFactor.DESKTOP_WIDE -> Triple(1120.dp, 4, true)
    }

/**
 * [ProvidableCompositionLocal] providing the current viewport's [AdaptiveLayoutMetrics].
 */
val LocalAdaptiveLayoutMetrics: ProvidableCompositionLocal<AdaptiveLayoutMetrics> =
    staticCompositionLocalOf {
        resolveAdaptiveLayoutMetrics(
            widthDp = DEFAULT_PHONE_WIDTH_DP,
            heightDp = DEFAULT_PHONE_HEIGHT_DP,
        )
    }

/**
 * Resolves [AdaptiveLayoutMetrics] from [maxWidth]/[maxHeight] and provides them to [content].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ProvideAdaptiveLayoutMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    isWatch: Boolean = false,
    content: @Composable () -> Unit,
) {
    val metrics =
        resolveAdaptiveLayoutMetrics(
            widthDp = resolveViewportDimensionDp(maxWidth, DEFAULT_PHONE_WIDTH_DP),
            heightDp = resolveViewportDimensionDp(maxHeight, DEFAULT_PHONE_HEIGHT_DP),
            isWatch = isWatch,
        )
    CompositionLocalProvider(LocalAdaptiveLayoutMetrics provides metrics, content = content)
}
