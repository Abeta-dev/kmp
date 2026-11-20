@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

@JsFun("() => (typeof navigator !== 'undefined' && navigator.language) ? navigator.language : 'en-US'")
private external fun jsNavigatorLanguage(): String

@JsFun(
    "() => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } " +
        "catch (e) { return 'UTC'; } }",
)
private external fun jsTimeZone(): String

@JsFun(
    "() => (typeof window !== 'undefined' && window.matchMedia) ? " +
        "window.matchMedia('(prefers-reduced-motion: reduce)').matches : false",
)
private external fun jsPrefersReducedMotion(): Boolean

@JsFun(
    "() => (typeof window !== 'undefined' && window.matchMedia) ? " +
        "window.matchMedia('(prefers-color-scheme: dark)').matches : false",
)
private external fun jsPrefersDarkMode(): Boolean

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function')")
private external fun jsSupportsVibrate(): Boolean

@JsFun("() => (typeof navigator !== 'undefined' && !!navigator.clipboard)")
private external fun jsSupportsClipboard(): Boolean

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.share === 'function')")
private external fun jsSupportsShare(): Boolean

/** Inspects browser locale, timezone, media-query preferences, and Web API support on WasmJS. */
actual fun currentPlatformCapabilities(): PlatformCapabilities =
    PlatformCapabilities(
        platform = currentPlatformInfo(),
        osVersion = "WebAssembly",
        localeTag = jsNavigatorLanguage(),
        timezoneId = jsTimeZone(),
        isReducedMotionPreferred = jsPrefersReducedMotion(),
        isDarkModePreferred = jsPrefersDarkMode(),
        supportsHaptics = jsSupportsVibrate(),
        supportsBiometrics = false,
        supportsClipboard = jsSupportsClipboard(),
        supportsExternalUrl = true,
        supportsShareSheet = jsSupportsShare(),
    )
