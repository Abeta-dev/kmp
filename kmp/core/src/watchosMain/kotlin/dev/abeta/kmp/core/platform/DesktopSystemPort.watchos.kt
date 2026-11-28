package dev.abeta.kmp.core.platform

/** Creates the watchOS platform [DesktopSystemPort] (defaults to [UnavailableDesktopSystemPort]). */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = UnavailableDesktopSystemPort
