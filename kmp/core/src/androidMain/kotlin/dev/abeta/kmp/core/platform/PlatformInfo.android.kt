package dev.abeta.kmp.core.platform

import android.content.pm.PackageManager
import android.os.Build

/** Returns the Android or Wear OS [PlatformInfo] inspected from [AbetaAndroidContext] and [Build]. */
actual fun currentPlatformInfo(): PlatformInfo {
    val context = AbetaAndroidContext.applicationContextOrNull()
    val isWatch =
        runCatching {
            context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_WATCH) == true
        }.getOrDefault(false)
    val kind = if (isWatch) PlatformKind.WEAR_OS else PlatformKind.ANDROID
    val release =
        runCatching { Build.VERSION.RELEASE }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { Build.VERSION.SDK_INT }.getOrDefault(0).takeIf { it > 0 }?.toString()
    val model =
        runCatching { Build.MODEL }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    val labelPrefix = if (isWatch) "Wear OS" else "Android"
    val baseName = if (release != null) "$labelPrefix $release" else labelPrefix
    val fullName = if (model != null) "$baseName ($model)" else baseName
    return PlatformInfo(kind = kind, name = fullName)
}
