package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class IosHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
        dispatch_async(dispatch_get_main_queue()) {
            when (style) {
                HapticStyle.TICK -> {
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
                }

                HapticStyle.CLICK -> {
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
                }

                HapticStyle.HEAVY -> {
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
                }

                HapticStyle.SUCCESS -> {
                    UINotificationFeedbackGenerator().notificationOccurred(
                        UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
                    )
                }

                HapticStyle.WARNING -> {
                    UINotificationFeedbackGenerator().notificationOccurred(
                        UINotificationFeedbackType.UINotificationFeedbackTypeWarning,
                    )
                }

                HapticStyle.ERROR -> {
                    UINotificationFeedbackGenerator().notificationOccurred(
                        UINotificationFeedbackType.UINotificationFeedbackTypeError,
                    )
                }
            }
        }
        return AppResult.Success(Unit)
    }
}

/** Creates an iOS/iPadOS [HapticEngine] backed by UIKit feedback generators. */
actual fun createPlatformHapticEngine(): HapticEngine = IosHapticEngine()
