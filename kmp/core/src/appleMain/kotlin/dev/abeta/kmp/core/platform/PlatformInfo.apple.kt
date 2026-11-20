package dev.abeta.kmp.core.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/** Returns the Apple Darwin [PlatformInfo] using Kotlin/Native OS family introspection. */
@OptIn(ExperimentalNativeApi::class)
actual fun currentPlatformInfo(): PlatformInfo =
    when (Platform.osFamily) {
        OsFamily.IOS -> PlatformInfo(kind = PlatformKind.IOS, name = "iOS")
        OsFamily.MACOSX -> PlatformInfo(kind = PlatformKind.MACOS, name = "macOS")
        OsFamily.WATCHOS -> PlatformInfo(kind = PlatformKind.WATCHOS, name = "watchOS")
        else -> PlatformInfo(kind = PlatformKind.IOS, name = Platform.osFamily.name)
    }
