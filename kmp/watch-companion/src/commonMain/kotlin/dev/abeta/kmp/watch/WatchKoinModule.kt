package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ActionRegistry
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.coroutines.DefaultDispatcherProvider
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.platform.createPlatformConnectivityMonitor
import dev.abeta.kmp.core.platform.createPlatformExternalLauncher
import dev.abeta.kmp.core.platform.createPlatformHapticEngine
import dev.abeta.kmp.core.platform.createPlatformSharePort
import dev.abeta.kmp.core.platform.currentPlatformCapabilities
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.security.createSecureKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.state.session.PersistentSessionStore
import dev.abeta.kmp.state.session.SessionStore
import dev.abeta.kmp.state.storage.createPersistentKeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.module.Module
import org.koin.dsl.module

private const val HTTP_OK = 200

/**
 * Creates a Compose-free Koin [Module] wiring Layer 0–2 capabilities into [WatchCompanionController]
 * for watchOS (`watchosArm64` / `watchosSimulatorArm64` / `watchosDeviceArm64`) and Wear OS JVM hosts.
 */
@Suppress("LongParameterList")
fun createWatchCompanionModule(
    baseUrl: String = "http://localhost:8080",
    scope: CoroutineScope? = null,
    httpEngine: HttpClientEngine? = null,
    keyValueStore: KeyValueStore? = null,
    logger: AppLogger = KermitAppLogger(),
    pageSource: WatchPageSource? = null,
    apiCallExecutor: ApiCallExecutor? = null,
    hapticEngine: HapticEngine? = null,
    externalLauncher: ExternalLauncherPort? = null,
    sharePort: SharePort? = null,
    connectivityMonitor: ConnectivityMonitor? = null,
    certificatePins: Map<String, List<String>> = emptyMap(),
): Module =
    module {
        single<Clock> { SystemClock() }
        single<AppLogger> { logger }
        single<CoroutineScope> { scope ?: CoroutineScope(SupervisorJob() + DefaultDispatcherProvider().default) }
        single<KeyValueStore> { keyValueStore ?: createPersistentKeyValueStore("abeta.watch.settings") }
        single<SecureKeyValueStore> { createSecureKeyValueStore("dev.abeta.kmp.watch.secure") }
        single<SessionStore> { PersistentSessionStore(backingStore = get()) }
        single<AnalyticsTracker> { LoggerAnalyticsTracker(logger = get(), clock = get()) }
        single<PlatformCapabilities> { currentPlatformCapabilities() }
        single<HapticEngine> { hapticEngine ?: createPlatformHapticEngine() }
        single<ExternalLauncherPort> { externalLauncher ?: createPlatformExternalLauncher() }
        single<SharePort> { sharePort ?: createPlatformSharePort() }
        single<ConnectivityMonitor> { connectivityMonitor ?: createPlatformConnectivityMonitor() }

        single<HttpClient> {
            HttpClientFactory.create(
                config = NetworkConfig(baseUrl = baseUrl, certificatePins = certificatePins),
                json = SduiJson,
                engine = httpEngine,
            )
        }
        single<HttpTransport> { KtorHttpTransport(httpClient = get(), json = SduiJson, clock = get()) }
        single<WatchPageSource> { pageSource ?: RemoteWatchPageSource(httpTransport = get()) }
        single<ApiCallExecutor> { apiCallExecutor ?: WatchRemoteApiCallExecutor(httpTransport = get()) }
        single { WatchScreenMapper(json = SduiJson) }
        single<ActionDecoderRegistry> { BuiltInActionDecoders.createDefaultRegistry() }
        single<ActionRegistry> { BuiltInActionHandlers.createDefaultRegistry(apiExecutor = get()) }
        single<ActionDispatcher> { DefaultActionDispatcher(decoderRegistry = get(), actionRegistry = get()) }
        factory {
            WatchCompanionController(
                pageSource = get(),
                actionDispatcher = get(),
                scope = get(),
                mapper = get(),
                sessionStore = get(),
                analyticsTracker = get(),
                hapticEngine = get(),
                externalLauncher = get(),
                sharePort = get(),
                connectivityMonitor = get(),
            )
        }
    }

/**
 * Default no-network [ApiCallExecutor] used in offline Watch simulator previews when BFF URL is unset.
 */
fun offlineWatchStarterApiExecutor(): ApiCallExecutor =
    ApiCallExecutor { payload, _ ->
        AppResult.Success(
            ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = true,
                statusCode = HTTP_OK,
                responseBody = buildJsonObject { put("status", "confirmed") },
            ),
        )
    }
