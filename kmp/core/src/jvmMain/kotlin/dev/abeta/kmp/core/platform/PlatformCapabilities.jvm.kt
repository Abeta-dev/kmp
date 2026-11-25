package dev.abeta.kmp.core.platform

import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * Safely checks whether AWT reports a headless environment, degrading to `true` when `java.awt.*`
 * is missing or unavailable (for example on Android/Wear OS ART).
 */
@Suppress("TooGenericExceptionCaught")
internal fun isJvmHeadlessSafely(): Boolean =
    try {
        GraphicsEnvironment.isHeadless()
    } catch (_: Throwable) {
        true
    }

/** Inspects JVM system properties, AWT desktop environment, and host locale/timezone. */
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val isHeadless = isJvmHeadlessSafely()
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
