package dev.abeta.kmp.platforms.wearos

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.wear.compose.material.MaterialTheme
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.platforms.android.AndroidConnectivityMonitor
import dev.abeta.kmp.platforms.android.AndroidExternalLauncher
import dev.abeta.kmp.platforms.android.AndroidKeyStoreSecureStore
import dev.abeta.kmp.platforms.android.AndroidSharePort
import dev.abeta.kmp.platforms.android.AndroidSharedPreferencesKeyValueStore
import dev.abeta.kmp.watch.WatchCompanionController
import dev.abeta.kmp.watch.WatchOfflineStarterDefaults
import dev.abeta.kmp.watch.createWatchCompanionModule
import dev.abeta.kmp.watch.offlineWatchStarterApiExecutor
import org.koin.dsl.koinApplication

/**
 * Reference Android Wear OS [ComponentActivity] wiring `:watch-companion`'s [WatchCompanionController]
 * and Android-native bridges ([AndroidSharedPreferencesKeyValueStore], [AndroidKeyStoreSecureStore],
 * [AndroidConnectivityMonitor], [AndroidExternalLauncher], [AndroidSharePort], and [WearHapticEngine])
 * into the modular [WearSduiScreen] renderer without relying on JVM Desktop `java.awt.*` or
 * `java.util.prefs.Preferences.userRoot()` fallbacks.
 *
 * Copy alongside `platforms/android/AndroidPlatformBridgeFactory.kt` into a Wear OS Gradle
 * application module (`compileSdk >= 35`, `androidx.wear.compose`).
 */
class WearCompanionHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bffBaseUrl =
            intent?.getStringExtra(EXTRA_BFF_BASE_URL)?.trim()?.takeIf { it.isNotEmpty() }
        val initialScreenId =
            intent?.getStringExtra(EXTRA_SCREEN_ID)?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_WATCH_SCREEN_ID
        val appContext = applicationContext
        val haptic = WearHapticEngine(appContext)
        val keyValueStore = AndroidSharedPreferencesKeyValueStore(appContext, "abeta.watch.settings")
        val secureStore = AndroidKeyStoreSecureStore(appContext, prefsName = "dev.abeta.kmp.watch.secure")
        val connectivityMonitor = AndroidConnectivityMonitor(appContext)
        val externalLauncher = AndroidExternalLauncher(appContext)
        val sharePort = AndroidSharePort(this)
        setContent {
            MaterialTheme {
                WearSduiHostRoot(
                    bffBaseUrl = bffBaseUrl,
                    initialScreenId = initialScreenId,
                    hapticEngine = haptic,
                    keyValueStore = keyValueStore,
                    secureKeyValueStore = secureStore,
                    connectivityMonitor = connectivityMonitor,
                    externalLauncher = externalLauncher,
                    sharePort = sharePort,
                )
            }
        }
    }

    companion object {
        const val EXTRA_BFF_BASE_URL: String = "bff_base_url"
        const val EXTRA_SCREEN_ID: String = "screen_id"
        private const val DEFAULT_WATCH_SCREEN_ID: String = "watch-status"
        internal const val DEFAULT_ANDROID_BFF_URL: String = "http://10.0.2.2:8080"
    }
}

@Composable
@Suppress("LongParameterList")
private fun WearSduiHostRoot(
    bffBaseUrl: String?,
    initialScreenId: String,
    hapticEngine: HapticEngine,
    keyValueStore: KeyValueStore,
    secureKeyValueStore: SecureKeyValueStore,
    connectivityMonitor: ConnectivityMonitor,
    externalLauncher: ExternalLauncherPort,
    sharePort: SharePort,
) {
    val scope = rememberCoroutineScope()
    val koinApp =
        remember(bffBaseUrl) {
            koinApplication {
                modules(
                    createWatchCompanionModule(
                        baseUrl = bffBaseUrl ?: WearCompanionHostActivity.DEFAULT_ANDROID_BFF_URL,
                        scope = scope,
                        keyValueStore = keyValueStore,
                        secureKeyValueStore = secureKeyValueStore,
                        hapticEngine = hapticEngine,
                        externalLauncher = externalLauncher,
                        sharePort = sharePort,
                        connectivityMonitor = connectivityMonitor,
                        pageSource =
                            if (bffBaseUrl == null) {
                                WatchOfflineStarterDefaults.createStarterPageSource()
                            } else {
                                null
                            },
                        apiCallExecutor =
                            if (bffBaseUrl == null) {
                                offlineWatchStarterApiExecutor()
                            } else {
                                null
                            },
                    ),
                )
            }
        }
    DisposableEffect(koinApp) { onDispose { koinApp.close() } }

    val controller = remember(koinApp) { koinApp.koin.get<WatchCompanionController>() }
    LaunchedEffect(controller) { controller.observeConnectivity() }
    LaunchedEffect(controller, initialScreenId) { controller.loadScreen(initialScreenId) }

    WearSduiScreen(controller = controller)
}

private class WearHapticEngine(
    private val context: Context,
) : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> =
        runCatching {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val id =
                    when (style) {
                        HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                        HapticStyle.ERROR, HapticStyle.WARNING -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        else -> VibrationEffect.EFFECT_CLICK
                    }
                vibrator.vibrate(VibrationEffect.createPredefined(id))
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(UnexpectedError(message = "Wear haptic failed", cause = it)) },
        )
}
