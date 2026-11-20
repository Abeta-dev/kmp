package dev.abeta.kmp.core.platform

import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.TimeZone

/** Inspects JVM system properties, AWT desktop environment, and host locale/timezone. */
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val isHeadless = GraphicsEnvironment.isHeadless()
    return PlatformCapabilities(
        platform = currentPlatformInfo(),
        osVersion = System.getProperty("os.version").orEmpty().ifBlank { "unknown" },
        localeTag = Locale.getDefault().toLanguageTag(),
        timezoneId = TimeZone.getDefault().id,
        isReducedMotionPreferred = false,
        isDarkModePreferred = false,
        supportsHaptics = false,
        supportsBiometrics = false,
        supportsClipboard = !isHeadless,
        supportsExternalUrl = !isHeadless,
        supportsShareSheet = !isHeadless,
    )
}
