package dev.abeta.kmp.core.platform

/**
 * Snapshot describing host OS metadata, accessibility preferences, and hardware capabilities.
 */
data class PlatformCapabilities(
    val platform: PlatformInfo,
    val osVersion: String,
    val localeTag: String,
    val timezoneId: String,
    val isReducedMotionPreferred: Boolean = false,
    val isDarkModePreferred: Boolean = false,
    val supportsHaptics: Boolean = false,
    val supportsBiometrics: Boolean = false,
    val supportsClipboard: Boolean = false,
    val supportsExternalUrl: Boolean = false,
    val supportsShareSheet: Boolean = false,
)

/** Returns the active [PlatformCapabilities] inspected from the host operating system. */
expect fun currentPlatformCapabilities(): PlatformCapabilities
