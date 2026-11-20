@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.atoms.modifiers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Abstract UI tactile feedback levels decouple Layer 1 atoms from Layer 0 OS implementations.
 */
enum class UiHapticKind {
    TICK,
    CLICK,
    SUCCESS,
    WARNING,
    ERROR,
}

/**
 * [ProvidableCompositionLocal] allowing Layer 3 (`sdui-layout`) to bind an OS [HapticEngine]
 * while keeping Layer 1 (`ui-atoms`) strictly isolated from `core`.
 */
val LocalUiHapticFeedback: ProvidableCompositionLocal<(UiHapticKind) -> Unit> =
    staticCompositionLocalOf { {} }

/**
 * Creates a default Compose-backed haptic callback mapping [UiHapticKind] onto [LocalHapticFeedback].
 */
@Composable
fun rememberComposeHapticFallback(): (UiHapticKind) -> Unit {
    val haptic = LocalHapticFeedback.current
    return { kind ->
        val composeType =
            when (kind) {
                UiHapticKind.TICK, UiHapticKind.CLICK -> HapticFeedbackType.TextHandleMove
                UiHapticKind.SUCCESS, UiHapticKind.WARNING, UiHapticKind.ERROR -> HapticFeedbackType.LongPress
            }
        haptic.performHapticFeedback(composeType)
    }
}
