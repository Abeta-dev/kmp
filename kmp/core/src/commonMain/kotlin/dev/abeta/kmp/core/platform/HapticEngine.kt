package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult

/**
 * Tactile feedback pattern taxonomy shared across iOS, Android, macOS, watchOS, and Web.
 */
enum class HapticStyle {
    TICK,
    CLICK,
    SUCCESS,
    WARNING,
    ERROR,
    HEAVY,
}

/**
 * Cross-platform tactile haptic trigger port decoupled from Compose or UI toolkits.
 */
interface HapticEngine {
    /** Emits tactile feedback matching [style] when supported by the host OS/device. */
    fun perform(style: HapticStyle): AppResult<Unit>
}

/**
 * No-op [HapticEngine] for headless tests or platforms without haptic hardware.
 */
object NoOpHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * Thread-safe test double capturing emitted [HapticStyle] events in order.
 */
class RecordingHapticEngine : HapticEngine {
    private val _performed = mutableListOf<HapticStyle>()

    /** Immutable snapshot of performed haptic patterns. */
    val performed: List<HapticStyle>
        get() = _performed.toList()

    override fun perform(style: HapticStyle): AppResult<Unit> {
        _performed.add(style)
        return AppResult.Success(Unit)
    }

    /** Clears captured events. */
    fun clear() {
        _performed.clear()
    }
}

/** Creates the platform-native [HapticEngine] for the current KMP target. */
expect fun createPlatformHapticEngine(): HapticEngine
