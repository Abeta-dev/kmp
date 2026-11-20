@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.abeta.kmp.core.platform

import platform.AppKit.NSWorkspace
import platform.AppKit.accessibilityDisplayShouldReduceMotion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/** Inspects macOS version, accessibility reduce-motion, locale, timezone, and TouchID capability. */
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val hasBiometrics = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)
    return PlatformCapabilities(
        platform = currentPlatformInfo(),
        osVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
        localeTag = currentLocaleTag(),
        timezoneId = NSTimeZone.localTimeZone.name,
        isReducedMotionPreferred = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceMotion,
        isDarkModePreferred = false,
        supportsHaptics = true,
        supportsBiometrics = hasBiometrics,
        supportsClipboard = true,
        supportsExternalUrl = true,
        supportsShareSheet = true,
    )
}
