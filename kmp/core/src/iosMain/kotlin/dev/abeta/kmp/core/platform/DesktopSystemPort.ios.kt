package dev.abeta.kmp.core.platform

/** Creates the iOS platform [DesktopSystemPort] (defaults to [UnavailableDesktopSystemPort]). */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = UnavailableDesktopSystemPort
