package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.MutableConnectivityMonitor
import dev.abeta.kmp.core.platform.OsPermission
import dev.abeta.kmp.core.platform.PermissionPort
import dev.abeta.kmp.core.platform.PermissionStatus
import dev.abeta.kmp.core.platform.RecordingExternalLauncherPort
import dev.abeta.kmp.core.platform.RecordingHapticEngine
import dev.abeta.kmp.core.platform.RecordingSharePort
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.BiometricAvailability
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.core.security.FakeBiometricAuthenticator
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutControllerBridgesTest {
    private fun readGoldenStoreHome(): PageResponse {
        val candidates =
            listOf(
                File("../bff/testdata/golden/store_home.json"),
                File("bff/testdata/golden/store_home.json"),
                File("../../bff/testdata/golden/store_home.json"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Could not locate golden store_home.json")
        return SduiJson.decodeFromString(PageResponse.serializer(), file.readText())
    }

    private fun buildHarness(
        scope: TestScope,
        platformBridges: PlatformBridgeBundle,
        offlineCoordinator: OfflineActionCoordinator? = null,
        apiExecutor: ApiCallExecutor =
            ApiCallExecutor { payload, _ ->
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = true,
                        statusCode = 200,
                    ),
                )
            },
    ): Pair<LayoutController, RecordingAnalyticsTracker> {
        val tracker = RecordingAnalyticsTracker()
        val storePage = readGoldenStoreHome()
        val repo =
            ScreenRepository { id ->
                if (id == "store-home") {
                    AppResult.Success(storePage)
                } else {
                    AppResult.Failure(NotFoundError(resource = id, message = "Unknown: $id"))
                }
            }
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.registerDefaults(ActionDecoderRegistry.builder()).build(),
                actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor),
            )
        val controller =
            LayoutController(
                screenRepository = repo,
                actionDispatcher = dispatcher,
                sessionStore = InMemorySessionStore(),
                analyticsTracker = tracker,
                scope = scope,
                offlineCoordinator = offlineCoordinator,
                platformBridges = platformBridges,
            )
        return controller to tracker
    }

    @Test
    fun executesUrlSharePermissionAndBiometricActions() =
        runTest(StandardTestDispatcher()) {
            val launcher = RecordingExternalLauncherPort()
            val sharePort = RecordingSharePort()
            val bio = FakeBiometricAuthenticator(BiometricAvailability.AVAILABLE, BiometricResult.Authenticated)
            val grantedPerms =
                object : PermissionPort {
                    override suspend fun checkStatus(permission: OsPermission) = PermissionStatus.GRANTED

                    override suspend fun request(
                        permission: OsPermission,
                        rationale: String?,
                    ) = PermissionStatus.GRANTED
                }
            val bridges =
                PlatformBridgeBundle.createTestDefault().copy(
                    externalLauncher = launcher,
                    sharePort = sharePort,
                    permissionPort = grantedPerms,
                    biometricAuthenticator = bio,
                )
            val (controller, tracker) = buildHarness(scope = this, platformBridges = bridges)
            controller.loadScreen("store-home").join()

            dispatchOsBridgeSamples(controller)
            val ready = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(listOf("https://abeta.dev/support"), launcher.openedUrls)
            assertEquals(1, sharePort.requests.size)
            assertEquals("Location enabled", ready.activeToast?.message)
            assertTrue(tracker.events.any { it.name == "bio_verified" })
        }

    @Test
    fun handlesDeepLinksAndShakeErrorHaptics() =
        runTest(StandardTestDispatcher()) {
            val haptics = RecordingHapticEngine()
            val bridges = PlatformBridgeBundle.createTestDefault().copy(hapticEngine = haptics)
            val (controller, _) = buildHarness(scope = this, platformBridges = bridges)

            val job = assertNotNull(controller.handleDeepLink("abeta://store-home?promo=FRESH10"))
            job.join()
            advanceUntilIdle()
            val deepReady = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("store-home", deepReady.page.pageId)
            assertEquals("FRESH10", deepReady.formValues["promo"])

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.TRIGGER_FEEDBACK,
                        payload =
                            buildJsonObject {
                                put("effect", "shake_error")
                                put("target_id", "store_hero_banner")
                            },
                    ),
                ).join()
            val afterShake = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(1L, afterShake.errorShakeSequence)
            assertEquals("store_hero_banner", afterShake.errorShakeTargetId)
            assertTrue(HapticStyle.ERROR in haptics.performed)
        }

    @Test
    fun observeConnectivityUpdatesOfflineStateAndAutoFlushesQueue() =
        runTest(StandardTestDispatcher()) {
            var isOnline = false
            val executor = buildFlakyApiExecutor { isOnline }
            val coordinator =
                OfflineActionCoordinator(
                    mutationQueue =
                        OfflineMutationQueue(
                            backingStore = InMemoryKeyValueStore(),
                            clock = MutableClock(1_000L),
                            uuidGenerator = SequentialUuidGenerator(),
                        ),
                    apiCallExecutor = executor,
                )
            val monitor = MutableConnectivityMonitor(ConnectivityStatus.ONLINE)
            val bridges = PlatformBridgeBundle.createTestDefault().copy(connectivityMonitor = monitor)
            val (controller, _) =
                buildHarness(
                    scope = this,
                    platformBridges = bridges,
                    offlineCoordinator = coordinator,
                    apiExecutor = executor,
                )

            controller.loadScreen("store-home").join()
            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.CALL_API,
                        payload = buildJsonObject { put("endpoint", "/api/v1/orders/express") },
                    ),
                ).join()
            assertEquals(1, assertIs<LayoutUiState.Ready>(controller.uiState.value).pendingOfflineActions)

            val connectivityJob = launch { controller.observeConnectivity() }
            try {
                advanceUntilIdle()
                monitor.setStatus(ConnectivityStatus.OFFLINE)
                advanceUntilIdle()
                assertTrue(assertIs<LayoutUiState.Ready>(controller.uiState.value).isOffline)

                isOnline = true
                monitor.setStatus(ConnectivityStatus.ONLINE)
                advanceUntilIdle()
                val recovered = assertIs<LayoutUiState.Ready>(controller.uiState.value)
                assertFalse(recovered.isOffline)
                assertEquals(0, recovered.pendingOfflineActions)
            } finally {
                connectivityJob.cancel()
            }
        }

    private fun buildFlakyApiExecutor(isOnline: () -> Boolean): ApiCallExecutor =
        ApiCallExecutor { payload, _ ->
            if (isOnline()) {
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = true,
                        statusCode = 200,
                    ),
                )
            } else {
                AppResult.Failure(OfflineError("No connection"))
            }
        }

    private suspend fun dispatchOsBridgeSamples(controller: LayoutController) {
        controller
            .onAction(
                ActionRef(
                    type = ActionTypes.OPEN_URL,
                    payload = buildJsonObject { put("url", "https://abeta.dev/support") },
                ),
            ).join()
        controller
            .onAction(
                ActionRef(
                    type = ActionTypes.SHARE,
                    payload =
                        buildJsonObject {
                            put("title", "Abeta Fresh")
                            put("text", "8-min grocery delivery")
                        },
                ),
            ).join()
        controller
            .onAction(
                ActionRef(
                    type = ActionTypes.REQUEST_PERMISSION,
                    payload =
                        buildJsonObject {
                            put("permission", "location")
                            put(
                                "on_granted",
                                buildJsonObject {
                                    put("type", ActionTypes.SHOW_TOAST)
                                    put("payload", buildJsonObject { put("message", "Location enabled") })
                                },
                            )
                        },
                ),
            ).join()
        controller
            .onAction(
                ActionRef(
                    type = ActionTypes.BIOMETRIC_AUTH,
                    payload =
                        buildJsonObject {
                            put("reason", "Authorize express checkout")
                            put(
                                "on_authenticated",
                                buildJsonObject {
                                    put("type", ActionTypes.TRACK_EVENT)
                                    put("payload", buildJsonObject { put("event_name", "bio_verified") })
                                },
                            )
                        },
                ),
            ).join()
    }
}
