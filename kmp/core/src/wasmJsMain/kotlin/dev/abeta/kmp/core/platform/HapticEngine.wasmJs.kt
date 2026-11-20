@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.result.AppResult

private const val TICK_MS = 10
private const val CLICK_MS = 18
private const val SUCCESS_MS = 28
private const val WARNING_MS = 40
private const val ERROR_MS = 60
private const val HEAVY_MS = 45

@JsFun(
    "(ms) => { try { if (typeof navigator !== 'undefined' && " +
        "typeof navigator.vibrate === 'function') { navigator.vibrate(ms); } } catch (e) {} }",
)
private external fun jsVibrate(durationMs: Int)

private class WasmHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
        val duration =
            when (style) {
                HapticStyle.TICK -> TICK_MS
                HapticStyle.CLICK -> CLICK_MS
                HapticStyle.SUCCESS -> SUCCESS_MS
                HapticStyle.WARNING -> WARNING_MS
                HapticStyle.ERROR -> ERROR_MS
                HapticStyle.HEAVY -> HEAVY_MS
            }
        jsVibrate(duration)
        return AppResult.Success(Unit)
    }
}

/** Creates a browser Vibration API [HapticEngine] for WasmJS targets. */
actual fun createPlatformHapticEngine(): HapticEngine = WasmHapticEngine()
