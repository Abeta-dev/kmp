package dev.abeta.kmp.core.platform

import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

/** Inspects Apple watchOS metadata, locale, timezone, and Taptic Engine capabilities. */
actual fun currentPlatformCapabilities(): PlatformCapabilities =
    PlatformCapabilities(
        platform = currentPlatformInfo(),
        osVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
        localeTag = currentLocaleTag(),
        timezoneId = NSTimeZone.localTimeZone.name,
        isReducedMotionPreferred = false,
        isDarkModePreferred = true,
        supportsHaptics = true,
        supportsBiometrics = false,
        supportsClipboard = false,
        supportsExternalUrl = false,
        supportsShareSheet = false,
    )
