package dev.abeta.kmp.platforms.wearos

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.watch.WatchActionCard
import dev.abeta.kmp.watch.WatchCompanionController
import dev.abeta.kmp.watch.WatchGlancePage
import dev.abeta.kmp.watch.WatchMetricGlance
import dev.abeta.kmp.watch.WatchOfflineStarterDefaults
import dev.abeta.kmp.watch.WatchUiState
import dev.abeta.kmp.watch.createWatchCompanionModule
import dev.abeta.kmp.watch.offlineWatchStarterApiExecutor
import org.koin.dsl.koinApplication

/**
 * Reference Android Wear OS [ComponentActivity] rendering `:watch-companion`'s [WatchUiState]
 * with Wear Compose `ScalingLazyColumn`, `PositionIndicator`, `Vignette`, and native watch
 * rotary/haptic feedback.
 * Copy into a Wear OS Gradle application module (`compileSdk >= 35`, `androidx.wear.compose`).
 */
class WearCompanionHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bffBaseUrl = intent?.getStringExtra("bff_base_url")?.trim()?.takeIf { it.isNotEmpty() }
        val haptic = WearHapticEngine(applicationContext)
        setContent {
            MaterialTheme {
                WearSduiRoot(bffBaseUrl = bffBaseUrl, hapticEngine = haptic)
            }
        }
    }
}

@Composable
private fun WearSduiRoot(
    bffBaseUrl: String?,
    hapticEngine: HapticEngine,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val koinApp =
        remember(bffBaseUrl) {
            koinApplication {
                modules(
                    createWatchCompanionModule(
                        baseUrl = bffBaseUrl ?: "http://10.0.2.2:8080",
                        scope = scope,
                        hapticEngine = hapticEngine,
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
    val state by controller.uiState.collectAsState()
    LaunchedEffect(controller) { controller.observeConnectivity() }
    LaunchedEffect(controller) { controller.loadScreen("watch-status") }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        when (val s = state) {
            WatchUiState.Idle, WatchUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is WatchUiState.Error -> {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Text(text = s.error.message) }
                    item { Chip(label = { Text("Retry") }, onClick = { controller.refresh() }) }
                }
            }

            is WatchUiState.Ready -> {
                WearGlanceContent(
                    page = s.page,
                    listState = listState,
                    controller = controller,
                )
            }
        }
    }
}

@Composable
private fun WearGlanceContent(
    page: WatchGlancePage,
    listState: ScalingLazyListState,
    controller: WatchCompanionController,
) {
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(text = page.title, style = MaterialTheme.typography.title3) }
        items(page.metrics) { metric: WatchMetricGlance ->
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${metric.label}: ${metric.value}") },
                secondaryLabel = metric.progressLabel?.let { lbl -> { Text(lbl) } },
                onClick = { metric.onClick?.let(controller::onAction) },
            )
        }
        items(page.cards) { card: WatchActionCard ->
            Chip(
                modifier = Modifier.fillMaxWidth(),
                enabled = card.primaryAction != null,
                label = { Text(card.primaryActionLabel ?: card.title) },
                secondaryLabel = card.subtitle?.let { sub -> { Text(sub) } },
                onClick = { card.primaryAction?.let(controller::onAction) },
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

private class WearHapticEngine(
    private val context: Context,
) : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> {
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
        return AppResult.Success(Unit)
    }
}
