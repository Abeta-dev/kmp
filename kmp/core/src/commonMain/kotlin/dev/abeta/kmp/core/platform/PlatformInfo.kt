package dev.abeta.kmp.core.platform

/**
 * Supported host platform runtime environments.
 */
enum class PlatformKind {
    JVM_DESKTOP,
    ANDROID,
    IOS,
    MACOS,
    WASM_WEB,
    WATCHOS,
    WEAR_OS,
    LINUX_NATIVE,
    WINDOWS_NATIVE,
}

/**
 * Snapshot describing the current runtime platform without leaking host SDK classes.
 */
data class PlatformInfo(
    val kind: PlatformKind,
    val name: String,
) {
    val isMobile: Boolean
        get() = kind == PlatformKind.ANDROID || kind == PlatformKind.IOS

    val isDesktop: Boolean
        get() =
            kind == PlatformKind.JVM_DESKTOP ||
                kind == PlatformKind.MACOS ||
                kind == PlatformKind.LINUX_NATIVE ||
                kind == PlatformKind.WINDOWS_NATIVE

    val isWeb: Boolean
        get() = kind == PlatformKind.WASM_WEB

    val isWatch: Boolean
        get() = kind == PlatformKind.WATCHOS || kind == PlatformKind.WEAR_OS

    val isApple: Boolean
        get() = kind == PlatformKind.IOS || kind == PlatformKind.MACOS || kind == PlatformKind.WATCHOS
}

/** Returns the [PlatformInfo] for the active Kotlin Multiplatform target. */
expect fun currentPlatformInfo(): PlatformInfo
