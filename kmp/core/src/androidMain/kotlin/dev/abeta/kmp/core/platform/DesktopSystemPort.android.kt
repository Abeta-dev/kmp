package dev.abeta.kmp.core.platform

/** Creates the Android platform [DesktopSystemPort] (defaults to [UnavailableDesktopSystemPort]). */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = UnavailableDesktopSystemPort
