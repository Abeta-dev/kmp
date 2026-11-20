package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult
import platform.WatchKit.WKHapticType
import platform.WatchKit.WKInterfaceDevice
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class WatchOsHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
        val hapticType =
            when (style) {
                HapticStyle.TICK -> WKHapticType.WKHapticTypeClick
                HapticStyle.CLICK -> WKHapticType.WKHapticTypeDirectionUp
                HapticStyle.SUCCESS -> WKHapticType.WKHapticTypeSuccess
                HapticStyle.WARNING -> WKHapticType.WKHapticTypeRetry
                HapticStyle.ERROR -> WKHapticType.WKHapticTypeFailure
                HapticStyle.HEAVY -> WKHapticType.WKHapticTypeNotification
            }
        dispatch_async(dispatch_get_main_queue()) {
            WKInterfaceDevice.currentDevice().playHaptic(hapticType)
        }
        return AppResult.Success(Unit)
    }
}

/** Creates an Apple Watch Taptic Engine [HapticEngine] backed by [WKInterfaceDevice]. */
actual fun createPlatformHapticEngine(): HapticEngine = WatchOsHapticEngine()
