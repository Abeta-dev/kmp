package dev.abeta.kmp.core.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult

private const val TICK_DURATION_MS = 10L
private const val CLICK_DURATION_MS = 18L
private const val WARNING_DURATION_MS = 35L
private const val HEAVY_DURATION_MS = 45L

private class AndroidHapticEngine : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
        val context = AbetaAndroidContext.applicationContextOrNull() ?: return AppResult.Success(Unit)
        return runCatching {
            val vibrator = resolveVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrateWithStyle(vibrator, style)
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { err ->
                AppResult.Failure(
                    UnexpectedError(message = "Android haptic feedback failed: ${err.message}", cause = err),
                )
            },
        )
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun vibrateWithStyle(
        vibrator: Vibrator,
        style: HapticStyle,
    ) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val effectId =
                    when (style) {
                        HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                        HapticStyle.CLICK, HapticStyle.SUCCESS -> VibrationEffect.EFFECT_CLICK
                        HapticStyle.WARNING, HapticStyle.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        HapticStyle.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                    }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationForStyle(style), VibrationEffect.DEFAULT_AMPLITUDE),
                )
            }

            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationForStyle(style))
            }
        }
    }

    private fun durationForStyle(style: HapticStyle): Long =
        when (style) {
            HapticStyle.TICK -> TICK_DURATION_MS
            HapticStyle.CLICK, HapticStyle.SUCCESS -> CLICK_DURATION_MS
            HapticStyle.WARNING, HapticStyle.ERROR -> WARNING_DURATION_MS
            HapticStyle.HEAVY -> HEAVY_DURATION_MS
        }
}

/** Creates an Android [HapticEngine] backed by [VibratorManager] / [Vibrator] and [VibrationEffect]. */
actual fun createPlatformHapticEngine(): HapticEngine = AndroidHapticEngine()
