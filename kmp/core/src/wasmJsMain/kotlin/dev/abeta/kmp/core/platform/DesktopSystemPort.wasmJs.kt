package dev.abeta.kmp.core.platform

/** Creates the WasmJS platform [DesktopSystemPort] (defaults to [UnavailableDesktopSystemPort]). */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = UnavailableDesktopSystemPort
