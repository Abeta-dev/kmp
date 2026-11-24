package dev.abeta.kmp.layout.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.atoms.atoms.LocalImageRenderer
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.di.createSduiKoinModule
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.engine.SduiLayoutViewModel
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.platform.asUiHapticCallback
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

private const val DEFAULT_PREVIEW_BASE_URL = "http://localhost:8080"
private const val HTTP_OK = 200

/**
 * Reusable self-contained Koin-backed SDUI host composable shared across iOS, iPadOS, macOS,
 * Desktop, and Web entrypoints, with automatic offline preview fallback when [bffBaseUrl] is blank.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun SduiHostedRoot(
    screenId: String = "home",
    bffBaseUrl: String? = null,
    logger: AppLogger = KermitAppLogger(),
    platformLabel: String = "KMP",
    isWatch: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val resolvedBaseUrl = bffBaseUrl?.trim()?.takeIf { it.isNotEmpty() }
    val holder =
        remember(resolvedBaseUrl, logger, platformLabel) {
            createHostedSduiHolder(
                resolvedBaseUrl = resolvedBaseUrl,
                coroutineScope = coroutineScope,
                logger = logger,
                platformLabel = platformLabel,
            )
        }

    DisposableEffect(holder) {
        onDispose { holder.koinApp.close() }
    }

    val uiState by holder.controller.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(holder.controller) {
        holder.controller.observeConnectivity()
    }
    LaunchedEffect(holder.controller, screenId) {
        holder.controller.loadScreen(screenId)
    }

    val capabilities = holder.platformBridges.capabilities
    DesignTheme(
        darkTheme = isSystemInDarkTheme() || capabilities.isDarkModePreferred,
        reducedMotion = capabilities.isReducedMotionPreferred,
    ) {
        CompositionLocalProvider(LocalImageRenderer provides holder.imageRenderer) {
            SduiScreen(
                state = uiState,
                snippetRegistry = holder.snippetRegistry,
                onAction = holder.controller::onAction,
                onFormValueChange = holder.controller::onFormFieldChanged,
                onRetry = { holder.controller.retry() },
                onDismissOverlay = holder.controller::dismissOverlay,
                onDismissToast = holder.controller::dismissToast,
                onDismissFeedback = holder.controller::dismissFeedback,
                onSnippetImpression = holder.controller::onSnippetImpression,
                onLoadMore = { holder.controller.loadMore() },
                isWatch = isWatch,
                onHaptic = holder.platformBridges.hapticEngine.asUiHapticCallback(),
                onNodeImpression = holder.controller::onNodeImpression,
            )
        }
    }
}

/**
 * Lifecycle-aware SDUI host composable bound to a caller-managed [SduiLayoutViewModel], collecting
 * UI state via [collectAsStateWithLifecycle] so Android configuration changes preserve active screen,
 * cart, and form state cleanly.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
fun SduiHostedRoot(
    viewModel: SduiLayoutViewModel,
    snippetRegistry: SnippetRegistry,
    imageRenderer: UiImageRenderer,
    screenId: String? = null,
    isWatch: Boolean = false,
    onHaptic: ((UiHapticKind) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.observeConnectivity()
    }
    LaunchedEffect(viewModel, screenId) {
        val target = screenId?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null && viewModel.lastLoadedScreenId != target) {
            viewModel.loadScreen(target)
        }
    }

    val capabilities = viewModel.capabilities
    DesignTheme(
        darkTheme = isSystemInDarkTheme() || capabilities.isDarkModePreferred,
        reducedMotion = capabilities.isReducedMotionPreferred,
    ) {
        CompositionLocalProvider(LocalImageRenderer provides imageRenderer) {
            SduiScreen(
                state = uiState,
                snippetRegistry = snippetRegistry,
                onAction = viewModel::onAction,
                onFormValueChange = viewModel::onFormFieldChanged,
                onRetry = { viewModel.retry() },
                onDismissOverlay = viewModel::dismissOverlay,
                onDismissToast = viewModel::dismissToast,
                onDismissFeedback = viewModel::dismissFeedback,
                onSnippetImpression = viewModel::onSnippetImpression,
                onLoadMore = { viewModel.loadMore() },
                isWatch = isWatch,
                onHaptic = onHaptic,
                onNodeImpression = viewModel::onNodeImpression,
            )
        }
    }
}

private fun createHostedSduiHolder(
    resolvedBaseUrl: String?,
    coroutineScope: CoroutineScope,
    logger: AppLogger,
    platformLabel: String,
): HostedSduiHolder {
    var controllerRef: LayoutController? = null
    val platformBridges = PlatformBridgeBundle.createPlatformDefault()
    val koinApp =
        koinApplication {
            modules(
                createSduiKoinModule(
                    baseUrl = resolvedBaseUrl ?: DEFAULT_PREVIEW_BASE_URL,
                    scope = coroutineScope,
                    logger = logger,
                    platformBridges = platformBridges,
                    remoteScreenRepository =
                        if (resolvedBaseUrl == null) {
                            OfflineStarterScreenRepository(platformLabel = platformLabel)
                        } else {
                            null
                        },
                    apiCallExecutor =
                        if (resolvedBaseUrl == null) {
                            OfflineStarterApiExecutor()
                        } else {
                            null
                        },
                    navigationListener = { nav ->
                        val target =
                            nav.route
                                .trim()
                                .removePrefix("/screens/")
                                .removePrefix("/")
                        if (target.isNotEmpty()) {
                            controllerRef?.loadScreen(target)
                        }
                    },
                ),
            )
        }
    val controller = koinApp.koin.get<LayoutController>()
    controllerRef = controller
    return HostedSduiHolder(
        koinApp = koinApp,
        controller = controller,
        snippetRegistry = koinApp.koin.get(),
        imageRenderer = koinApp.koin.get(),
        platformBridges = platformBridges,
    )
}

private class HostedSduiHolder(
    val koinApp: KoinApplication,
    val controller: LayoutController,
    val snippetRegistry: SnippetRegistry,
    val imageRenderer: UiImageRenderer,
    val platformBridges: PlatformBridgeBundle,
)

private class OfflineStarterScreenRepository(
    private val platformLabel: String,
) : ScreenRepository {
    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val headerData =
            SduiJson.encodeToJsonElement(
                HeaderSnippetData.serializer(),
                HeaderSnippetData(
                    title = TextAtom(text = "Abeta SDUI Kit · $platformLabel"),
                    subtitle = TextAtom(text = "Screen '$screenId' running in offline preview mode"),
                ),
            ) as JsonObject
        val cardData =
            SduiJson.encodeToJsonElement(
                CardSnippetData.serializer(),
                CardSnippetData(
                    title = TextAtom(text = "Ready for BFF streaming"),
                    description = TextAtom(text = "Pass bffBaseUrl to connect to a live Go SDUI BFF instance."),
                ),
            ) as JsonObject
        return AppResult.Success(
            PageResponse(
                pageId = screenId,
                title = "Abeta SDUI · $platformLabel",
                toolbar = ToolbarSpec(title = TextAtom(text = "Abeta SDUI · $platformLabel")),
                snippets =
                    listOf(
                        SnippetEnvelope(
                            snippetId = "preview_header",
                            snippetType = SnippetTypes.HEADER,
                            data = headerData,
                        ),
                        SnippetEnvelope(
                            snippetId = "preview_card",
                            snippetType = SnippetTypes.CARD,
                            data = cardData,
                        ),
                    ),
            ),
        )
    }
}

private class OfflineStarterApiExecutor : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> =
        AppResult.Success(
            ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = true,
                statusCode = HTTP_OK,
                responseBody = buildJsonObject { put("status", "ok") },
            ),
        )
}
