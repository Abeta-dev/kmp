package dev.abeta.kmp.layout.di

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ActionRegistry
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.coroutines.DefaultDispatcherProvider
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.core.platform.ClipboardPort
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.FilePickerPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.LocationPort
import dev.abeta.kmp.core.platform.PermissionPort
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.platform.PushNotificationPort
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.security.BiometricAuthenticator
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.layout.analytics.RemoteBffAnalyticsTracker
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.engine.OfflineActionCoordinator
import dev.abeta.kmp.layout.media.ImageBytesFetcher
import dev.abeta.kmp.layout.media.KtorImageBytesFetcher
import dev.abeta.kmp.layout.media.KtorImageRenderer
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.CachedScreenRepository
import dev.abeta.kmp.layout.repository.RemoteApiCallExecutor
import dev.abeta.kmp.layout.repository.RemoteScreenManifestRepository
import dev.abeta.kmp.layout.repository.RemoteScreenRepository
import dev.abeta.kmp.layout.repository.ScreenCachePolicy
import dev.abeta.kmp.layout.repository.ScreenManifestRepository
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.ports.CsrfTokenProvider
import dev.abeta.kmp.network.sse.KtorSseTransport
import dev.abeta.kmp.network.sse.SseTransport
import dev.abeta.kmp.state.flags.FeatureFlagProvider
import dev.abeta.kmp.state.flags.PersistentFeatureFlagStore
import dev.abeta.kmp.state.preferences.DefaultPreferenceStore
import dev.abeta.kmp.state.preferences.PreferenceStore
import dev.abeta.kmp.state.session.PersistentSessionStore
import dev.abeta.kmp.state.session.SessionStore
import dev.abeta.kmp.state.storage.createDiskKeyValueStore
import dev.abeta.kmp.state.storage.createPersistentKeyValueStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Creates a self-contained Koin [Module] wiring Layer 0–3 SDUI components cleanly via
 * constructor injection without global singletons.
 */
@Suppress("LongParameterList")
fun createSduiKoinModule(
    baseUrl: String = "http://localhost:8080",
    scope: CoroutineScope? = null,
    httpEngine: HttpClientEngine? = null,
    keyValueStore: KeyValueStore? = null,
    logger: AppLogger = KermitAppLogger(),
    cachePolicy: ScreenCachePolicy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
    cacheTtlMillis: Long = CachedScreenRepository.DEFAULT_TTL_MILLIS,
    navigationListener: (ActionOutcome.NavigationRequested) -> Unit = {},
    backListener: (ActionOutcome.BackRequested) -> Unit = {},
    clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit = {},
    isDebug: Boolean = false,
    remoteScreenRepository: ScreenRepository? = null,
    apiCallExecutor: ApiCallExecutor? = null,
    platformBridges: PlatformBridgeBundle? = null,
    certificatePins: Map<String, List<String>> = emptyMap(),
    enableRemoteAnalytics: Boolean = false,
    analyticsEndpoint: String = RemoteBffAnalyticsTracker.DEFAULT_ANALYTICS_ENDPOINT,
    csrfTokenProvider: CsrfTokenProvider? = null,
    appVersion: String? = null,
    manifestPath: String? = null,
    useDiskCacheStore: Boolean = false,
): Module =
    module {
        registerFoundationBindings(
            scope = scope,
            keyValueStore = keyValueStore,
            logger = logger,
            enableRemoteAnalytics = enableRemoteAnalytics,
            analyticsEndpoint = analyticsEndpoint,
            useDiskCacheStore = useDiskCacheStore,
        )
        registerPlatformBridgeBindings(platformBridges = platformBridges)
        registerNetworkAndRepositoryBindings(
            baseUrl = baseUrl,
            httpEngine = httpEngine,
            isDebug = isDebug,
            cachePolicy = cachePolicy,
            cacheTtlMillis = cacheTtlMillis,
            remoteScreenRepository = remoteScreenRepository,
            apiCallExecutor = apiCallExecutor,
            certificatePins = certificatePins,
            csrfTokenProvider = csrfTokenProvider,
            appVersion = appVersion,
            manifestPath = manifestPath,
        )
        registerOrchestrationBindings(
            navigationListener = navigationListener,
            backListener = backListener,
            clipboardListener = clipboardListener,
        )
    }

private fun Module.registerPlatformBridgeBindings(platformBridges: PlatformBridgeBundle?) {
    single { platformBridges ?: PlatformBridgeBundle.createPlatformDefault() }
    single<PlatformCapabilities> { get<PlatformBridgeBundle>().capabilities }
    single<HapticEngine> { get<PlatformBridgeBundle>().hapticEngine }
    single<ClipboardPort> { get<PlatformBridgeBundle>().clipboardPort }
    single<ExternalLauncherPort> { get<PlatformBridgeBundle>().externalLauncher }
    single<SharePort> { get<PlatformBridgeBundle>().sharePort }
    single<ConnectivityMonitor> { get<PlatformBridgeBundle>().connectivityMonitor }
    single<SecureKeyValueStore> { get<PlatformBridgeBundle>().secureStore }
    single<BiometricAuthenticator> { get<PlatformBridgeBundle>().biometricAuthenticator }
    single<PermissionPort> { get<PlatformBridgeBundle>().permissionPort }
    single<LocationPort> { get<PlatformBridgeBundle>().locationPort }
    single<FilePickerPort> { get<PlatformBridgeBundle>().filePickerPort }
    single<PushNotificationPort> { get<PlatformBridgeBundle>().pushNotificationPort }
}

@Suppress("LongParameterList")
private fun Module.registerFoundationBindings(
    scope: CoroutineScope?,
    keyValueStore: KeyValueStore?,
    logger: AppLogger,
    enableRemoteAnalytics: Boolean,
    analyticsEndpoint: String,
    useDiskCacheStore: Boolean,
) {
    single<Clock> { SystemClock() }
    single<UuidGenerator> { RandomUuidGenerator() }
    single<AppLogger> { logger }
    single<CoroutineScope> { scope ?: CoroutineScope(SupervisorJob() + DefaultDispatcherProvider().default) }
    single<KeyValueStore> {
        keyValueStore
            ?: if (useDiskCacheStore) {
                createDiskKeyValueStore()
            } else {
                createPersistentKeyValueStore()
            }
    }
    single<SessionStore> { PersistentSessionStore(backingStore = get()) }
    single<PreferenceStore> { DefaultPreferenceStore(store = get()) }
    single<FeatureFlagProvider> { PersistentFeatureFlagStore(backingStore = get()) }
    single { OfflineMutationQueue(backingStore = get(), clock = get(), uuidGenerator = get()) }
    single {
        RemoteBffAnalyticsTracker(
            httpTransport = get(),
            backingStore = get(),
            endpointPath = analyticsEndpoint,
            sessionId = get<UuidGenerator>().nextId(),
            platform =
                get<PlatformCapabilities>()
                    .platform.kind.name
                    .lowercase(),
            clock = get(),
            uuidGenerator = get(),
            scope = get(),
            logger = get(),
        )
    }
    single<AnalyticsTracker> {
        if (enableRemoteAnalytics) {
            get<RemoteBffAnalyticsTracker>()
        } else {
            LoggerAnalyticsTracker(logger = get(), clock = get())
        }
    }
}

@Suppress("LongParameterList")
private fun Module.registerNetworkAndRepositoryBindings(
    baseUrl: String,
    httpEngine: HttpClientEngine?,
    isDebug: Boolean,
    cachePolicy: ScreenCachePolicy,
    cacheTtlMillis: Long,
    remoteScreenRepository: ScreenRepository?,
    apiCallExecutor: ApiCallExecutor?,
    certificatePins: Map<String, List<String>>,
    csrfTokenProvider: CsrfTokenProvider?,
    appVersion: String?,
    manifestPath: String?,
) {
    single<HttpClient> {
        HttpClientFactory.create(
            config =
                NetworkConfig(
                    baseUrl = baseUrl,
                    isDebug = isDebug,
                    certificatePins = certificatePins,
                ),
            json = SduiJson,
            engine = httpEngine,
        )
    }
    single<HttpTransport> {
        KtorHttpTransport(
            httpClient = get(),
            json = SduiJson,
            clock = get(),
            csrfTokenProvider = csrfTokenProvider,
        )
    }
    single<SseTransport> {
        KtorSseTransport(
            httpClient = get(),
            clock = get(),
        )
    }
    val cleanManifestPath = manifestPath?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanManifestPath != null) {
        single<ScreenManifestRepository> {
            RemoteScreenManifestRepository(
                httpClient = get(),
                manifestPath = cleanManifestPath,
            )
        }
    }
    single { RemoteScreenRepository(httpClient = get()) }
    single {
        CachedScreenRepository(
            remote = remoteScreenRepository ?: get<RemoteScreenRepository>(),
            store = get(),
            clock = get(),
            policy = cachePolicy,
            ttlMillis = cacheTtlMillis,
            appVersion = appVersion,
            manifestRepository = getOrNull<ScreenManifestRepository>(),
        )
    }
    single<ScreenRepository> { get<CachedScreenRepository>() }
    single<ApiCallExecutor> { apiCallExecutor ?: RemoteApiCallExecutor(httpClient = get()) }
    single<ImageBytesFetcher> { KtorImageBytesFetcher(httpClient = get()) }
    single<UiImageRenderer> { KtorImageRenderer(fetcher = get()) }
}

private fun Module.registerOrchestrationBindings(
    navigationListener: (ActionOutcome.NavigationRequested) -> Unit,
    backListener: (ActionOutcome.BackRequested) -> Unit,
    clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit,
) {
    single<SnippetRegistry> { BuiltInSnippetModule.createDefaultRegistry() }
    single<ActionDecoderRegistry> { BuiltInActionDecoders.createDefaultRegistry() }
    single<ActionRegistry> { BuiltInActionHandlers.createDefaultRegistry(apiExecutor = get()) }
    single<ActionDispatcher> { DefaultActionDispatcher(decoderRegistry = get(), actionRegistry = get()) }
    single {
        val sessionStore = get<SessionStore>()
        OfflineActionCoordinator(
            mutationQueue = get(),
            apiCallExecutor = get(),
            logger = get(),
            contextProvider = { ActionContext(sessionState = sessionStore.state.value) },
        )
    }
    factory {
        LayoutController(
            screenRepository = get(),
            actionDispatcher = get(),
            sessionStore = get(),
            analyticsTracker = get(),
            scope = get(),
            navigationListener = navigationListener,
            backListener = backListener,
            clipboardListener = clipboardListener,
            logger = get(),
            offlineCoordinator = get(),
            platformBridges = get(),
            sseTransport = get(),
        )
    }
}
