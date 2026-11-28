package dev.abeta.kmp.core.platform

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import java.util.Locale
import java.util.TimeZone

/** Inspects Android / Wear OS version, display preferences, locale, timezone, and hardware capabilities. */
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val info = currentPlatformInfo()
    val context = AbetaAndroidContext.applicationContextOrNull()
    val isWatch = info.isWatch
    val osVersion =
        runCatching { Build.VERSION.RELEASE }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { Build.VERSION.SDK_INT }.getOrDefault(0).takeIf { it > 0 }?.toString()
            ?: "Android"
    val localeTag = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
    val timezoneId = TimeZone.getDefault().id.ifBlank { "UTC" }
    val isReducedMotion = context != null && readReducedMotionPreference(context)
    val isDarkMode = context != null && readDarkModePreference(context)
    val hasBiometrics = !isWatch && context != null && readBiometricHardwareSupport(context)
    return PlatformCapabilities(
        platform = info,
        osVersion = osVersion,
        localeTag = localeTag,
        timezoneId = timezoneId,
        isReducedMotionPreferred = isReducedMotion,
        isDarkModePreferred = isDarkMode,
        supportsHaptics = true,
        supportsBiometrics = hasBiometrics,
        supportsClipboard = !isWatch,
        supportsExternalUrl = true,
        supportsShareSheet = !isWatch,
    )
}

private fun readReducedMotionPreference(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

private fun readDarkModePreference(context: Context): Boolean =
    runCatching {
        val uiMode = context.resources.configuration.uiMode
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }.getOrDefault(false)

private fun readBiometricHardwareSupport(context: Context): Boolean =
    runCatching {
        val pm = context.packageManager
        val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFaceOrIris =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (pm.hasSystemFeature(PackageManager.FEATURE_FACE) || pm.hasSystemFeature(PackageManager.FEATURE_IRIS))
        hasFingerprint || hasFaceOrIris
    }.getOrDefault(false)
