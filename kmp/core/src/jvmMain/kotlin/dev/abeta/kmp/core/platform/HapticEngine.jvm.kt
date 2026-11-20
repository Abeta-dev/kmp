package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult

/** Creates a JVM desktop [HapticEngine] (graceful no-op on non-tactile hardware). */
actual fun createPlatformHapticEngine(): HapticEngine = NoOpHapticEngine

/** Unused helper marker kept internal; standard JVM desktops do not expose OS haptic APIs. */
internal fun AppResult<Unit>.ignoredOnDesktop(): AppResult<Unit> = this
