@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.abeta.kmp.core.platform

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.currentTraitCollection

/** Inspects iOS / iPadOS system version, accessibility, dark mode, and FaceID/TouchID capability. */
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val info = currentPlatformInfo()
    val hasBiometrics = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)
    val isDark =
        UITraitCollection.currentTraitCollection.userInterfaceStyle ==
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
    return PlatformCapabilities(
        platform = info,
        osVersion = UIDevice.currentDevice.systemVersion,
        localeTag = currentLocaleTag(),
        timezoneId = NSTimeZone.localTimeZone.name,
        isReducedMotionPreferred = UIAccessibilityIsReduceMotionEnabled(),
        isDarkModePreferred = isDark,
        supportsHaptics = true,
        supportsBiometrics = hasBiometrics,
        supportsClipboard = true,
        supportsExternalUrl = true,
        supportsShareSheet = true,
    )
}
