package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult
import platform.AppKit.NSHapticFeedbackManager
import platform.AppKit.NSHapticFeedbackPatternAlignment
import platform.AppKit.NSHapticFeedbackPatternGeneric
import platform.AppKit.NSHapticFeedbackPatternLevelChange
import platform.AppKit.NSHapticFeedbackPerformanceTimeNow

private class MacOsHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
        val pattern =
            when (style) {
                HapticStyle.TICK -> {
                    NSHapticFeedbackPatternAlignment
                }

                HapticStyle.CLICK -> {
                    NSHapticFeedbackPatternGeneric
                }

                HapticStyle.SUCCESS, HapticStyle.WARNING, HapticStyle.ERROR, HapticStyle.HEAVY -> {
                    NSHapticFeedbackPatternLevelChange
                }
            }
        NSHapticFeedbackManager.defaultPerformer.performFeedbackPattern(
            pattern = pattern,
            performanceTime = NSHapticFeedbackPerformanceTimeNow,
        )
        return AppResult.Success(Unit)
    }
}

/** Creates a macOS trackpad/Force Touch [HapticEngine] via [NSHapticFeedbackManager]. */
actual fun createPlatformHapticEngine(): HapticEngine = MacOsHapticEngine()
