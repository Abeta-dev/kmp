@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.watch

import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.core.platform.PlatformCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

private const val DEFAULT_WATCH_BASE_URL = "http://localhost:8080"

/**
 * Lifecycle holder for Apple Watch (`AbetaWatchKit`) and companion iOS WatchConnectivity clients.
 */
class WatchCompanionHostHolder(
    val koinApp: KoinApplication,
    val controller: WatchCompanionController,
    val capabilities: PlatformCapabilities,
    private val hostScope: CoroutineScope,
) {
    private val observationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Collects [WatchCompanionController.uiState] on an internal coroutine scope and invokes
     * [onChange] for every emitted [WatchUiState]. Returns a cancellation handle.
     */
    fun observeUiState(onChange: (WatchUiState) -> Unit): () -> Unit {
        val job =
            observationScope.launch {
                controller.uiState.collect { state ->
                    onChange(state)
                }
            }
        return { job.cancel() }
    }

    /**
     * Starts [WatchCompanionController.observeConnectivity] on a cancellable job
     * and returns a cancellation handle.
     */
    fun startConnectivityObservation(): () -> Unit {
        val job =
            observationScope.launch {
                controller.observeConnectivity()
            }
        return { job.cancel() }
    }

    /** Cancels observation scopes, cancels host coroutines, and releases the isolated Koin graph. */
    fun close() {
        observationScope.cancel()
        hostScope.cancel()
        koinApp.close()
    }
}

/**
 * Swift-friendly 0-argument overload launching with built-in offline preview defaults
 * (Kotlin/Native Objective-C export does not synthesize Swift default arguments).
 */
fun createAppleWatchCompanionHost(): WatchCompanionHostHolder =
    createAppleWatchCompanionHost(
        bffBaseUrl = null,
        certificatePins = emptyMap(),
        logger = KermitAppLogger(),
    )

/**
 * Swift-friendly 1-argument overload accepting an optional [bffBaseUrl]
 * (Kotlin/Native Objective-C export does not synthesize Swift default arguments).
 */
fun createAppleWatchCompanionHost(bffBaseUrl: String?): WatchCompanionHostHolder =
    createAppleWatchCompanionHost(
        bffBaseUrl = bffBaseUrl,
        certificatePins = emptyMap(),
        logger = KermitAppLogger(),
    )

/**
 * Creates a self-contained Apple Watch / Companion [WatchCompanionHostHolder] backed by Apple Darwin
 * HTTP/2 + Taptic Engine haptics (`WKHapticType` on watchOS) + Keychain storage, or offline preview
 * data when [bffBaseUrl] is `null` or blank.
 */
fun createAppleWatchCompanionHost(
    bffBaseUrl: String? = null,
    certificatePins: Map<String, List<String>> = emptyMap(),
    logger: AppLogger = KermitAppLogger(),
): WatchCompanionHostHolder {
    val resolvedUrl = bffBaseUrl?.trim()?.takeIf { it.isNotEmpty() }
    val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val koinApp =
        koinApplication {
            modules(
                createWatchCompanionModule(
                    baseUrl = resolvedUrl ?: DEFAULT_WATCH_BASE_URL,
                    scope = hostScope,
                    logger = logger,
                    pageSource =
                        if (resolvedUrl == null) {
                            WatchOfflineStarterDefaults.createStarterPageSource()
                        } else {
                            null
                        },
                    apiCallExecutor =
                        if (resolvedUrl == null) {
                            offlineWatchStarterApiExecutor()
                        } else {
                            null
                        },
                    certificatePins = certificatePins,
                ),
            )
        }
    return WatchCompanionHostHolder(
        koinApp = koinApp,
        controller = koinApp.koin.get(),
        capabilities = koinApp.koin.get(),
        hostScope = hostScope,
    )
}
