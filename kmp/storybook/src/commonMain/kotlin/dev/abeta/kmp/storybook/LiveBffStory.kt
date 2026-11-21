package dev.abeta.kmp.storybook

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.repository.RemoteApiCallExecutor
import dev.abeta.kmp.layout.repository.RemoteScreenRepository
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.network.config.TimeoutPolicy
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.tokens.ThemeTokens
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_BFF_URL = "http://127.0.0.1:8080"
private const val BFF_CONNECT_TIMEOUT_MS = 3_000L
private const val BFF_REQUEST_TIMEOUT_MS = 5_000L
private const val VIEWPORT_HEIGHT_DP = 540
private val BFF_SCREENS =
    listOf(
        "home",
        "catalog",
        "profile",
        "form-demo",
        "containers",
        "store-home",
        "watch-status",
        "fresh-home",
        "bites-home",
        "pulse-home",
        "mart-home",
        "spark-deck",
        "trade-home",
    )

private class LiveBffHolder(
    val httpClient: HttpClient,
    val snippetRegistry: SnippetRegistry,
    val controller: LayoutController,
)

/**
 * Interactive Go BFF screen explorer fetching screens via [HttpClientFactory], [KtorHttpTransport],
 * and [RemoteScreenRepository], rendered inside [SduiScreen] with overlay demo triggers.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun LiveBffStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(DEFAULT_BFF_URL) }
    var activeUrl by remember { mutableStateOf(DEFAULT_BFF_URL) }
    var selectedScreen by remember { mutableStateOf(BFF_SCREENS.first()) }
    var fetchCount by remember { mutableIntStateOf(0) }
    var previewOverlay by remember { mutableStateOf<ActionOutcome.OverlayRequested?>(null) }

    val holder = remember(activeUrl) { createLiveBffHolder(activeUrl, scope) { selectedScreen = it } }
    DisposableEffect(holder) { onDispose { holder.httpClient.close() } }
    LaunchedEffect(holder, fetchCount) {
        if (fetchCount > 0) holder.controller.loadScreen(selectedScreen)
    }

    val uiState by holder.controller.uiState.collectAsState()
    val triggerOverlayDemo: (OverlayPresentation) -> Unit = { presentation ->
        val action = createOverlayDemoAction(presentation)
        onAction(action)
        if (uiState is LayoutUiState.Ready) {
            holder.controller.onAction(action)
        } else {
            previewOverlay = decodeOverlayRequest(action)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        LiveBffControlsCard(
            urlInput = urlInput,
            selectedScreen = selectedScreen,
            uiState = uiState,
            onUrlChange = { urlInput = it },
            onSelectScreen = { selectedScreen = it },
            onFetch = {
                previewOverlay = null
                activeUrl = urlInput.trim().ifEmpty { DEFAULT_BFF_URL }
                fetchCount += 1
            },
            onPreviewOverlay = triggerOverlayDemo,
        )
        LiveBffViewportFrame(
            uiState = uiState,
            snippetRegistry = holder.snippetRegistry,
            controller = holder.controller,
            onAction = onAction,
        )
    }
    previewOverlay?.let { overlay ->
        PreviewOverlayModal(overlay = overlay, onDismiss = { previewOverlay = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun LiveBffControlsCard(
    urlInput: String,
    selectedScreen: String,
    uiState: LayoutUiState,
    onUrlChange: (String) -> Unit,
    onSelectScreen: (String) -> Unit,
    onFetch: () -> Unit,
    onPreviewOverlay: (OverlayPresentation) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val (statusText, statusTone) = statusLabelFor(uiState)

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Live Go BFF Screen Explorer", typography = TypographyTokenRef("title.large")))
            UiTag(TagAtom(label = statusText, variant = statusTone))
        }
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            label = { Text("BFF Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.space8),
        )
        FlowRow(
            modifier = Modifier.padding(top = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            BFF_SCREENS.forEach { screenId ->
                FilterChip(
                    selected = selectedScreen == screenId,
                    onClick = { onSelectScreen(screenId) },
                    label = { Text(screenId) },
                )
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            Button(onClick = onFetch) { Text("Fetch Screen") }
            OutlinedButton(onClick = { onPreviewOverlay(OverlayPresentation.BOTTOM_SHEET) }) {
                Text("Preview BottomSheet")
            }
            OutlinedButton(onClick = { onPreviewOverlay(OverlayPresentation.DIALOG) }) {
                Text("Preview Dialog Overlay")
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun LiveBffViewportFrame(
    uiState: LayoutUiState,
    snippetRegistry: SnippetRegistry,
    controller: LayoutController,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.md)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(VIEWPORT_HEIGHT_DP.dp)
                .clip(shape)
                .border(ThemeTokens.strokeWidth.thin, colors.border.default, shape),
    ) {
        if (uiState is LayoutUiState.Idle) {
            Box(
                modifier = Modifier.fillMaxSize().padding(spacing.space24),
                contentAlignment = Alignment.Center,
            ) {
                UiText(
                    TextAtom(
                        text = "Select a screen and tap 'Fetch Screen' to stream live JSON from the Go BFF.",
                        typography = TypographyTokenRef("body.medium"),
                        color = ColorTokenRef("text.secondary"),
                    ),
                )
            }
        } else {
            SduiScreen(
                state = uiState,
                snippetRegistry = snippetRegistry,
                onAction = { action ->
                    onAction(action)
                    controller.onAction(action)
                },
                onFormValueChange = controller::onFormFieldChanged,
                onRetry = { controller.retry() },
                onDismissOverlay = controller::dismissOverlay,
                onDismissToast = controller::dismissToast,
                onDismissFeedback = controller::dismissFeedback,
                onSnippetImpression = controller::onSnippetImpression,
                onLoadMore = { controller.loadMore() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PreviewOverlayModal(
    overlay: ActionOutcome.OverlayRequested,
    onDismiss: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    if (overlay.presentation == OverlayPresentation.BOTTOM_SHEET) {
        val sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.surface.primary,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.space24),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Text(text = overlay.title, style = typography.titleLarge, color = colors.text.primary)
                overlay.message?.let { Text(text = it, style = typography.bodyMedium, color = colors.text.secondary) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = overlay.title, style = typography.titleLarge) },
            text = { overlay.message?.let { Text(text = it, style = typography.bodyMedium) } },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

private fun createLiveBffHolder(
    baseUrl: String,
    scope: CoroutineScope,
    onNavigate: (String) -> Unit,
): LiveBffHolder {
    val httpClient =
        HttpClientFactory.create(
            config =
                NetworkConfig(
                    baseUrl = baseUrl,
                    timeouts =
                        TimeoutPolicy(
                            connectTimeoutMillis = BFF_CONNECT_TIMEOUT_MS,
                            requestTimeoutMillis = BFF_REQUEST_TIMEOUT_MS,
                            socketTimeoutMillis = BFF_REQUEST_TIMEOUT_MS,
                        ),
                    retry = RetryPolicy(maxRetries = 0),
                ),
            json = SduiJson,
        )
    val transport = KtorHttpTransport(httpClient = httpClient, json = SduiJson)
    val repository = RemoteScreenRepository(httpClient = transport)
    val apiExecutor = RemoteApiCallExecutor(httpClient = transport)
    val dispatcher =
        DefaultActionDispatcher(
            decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
            actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor = apiExecutor),
        )
    var controllerRef: LayoutController? = null
    val controller =
        LayoutController(
            screenRepository = repository,
            actionDispatcher = dispatcher,
            sessionStore = InMemorySessionStore(),
            analyticsTracker = LoggerAnalyticsTracker(NoOpAppLogger()),
            scope = scope,
            navigationListener = { nav ->
                val target =
                    nav.route
                        .trim()
                        .removePrefix("/screens/")
                        .removePrefix("/")
                if (target.isNotEmpty()) {
                    onNavigate(target)
                    controllerRef?.loadScreen(target)
                }
            },
        )
    controllerRef = controller
    return LiveBffHolder(
        httpClient = httpClient,
        snippetRegistry = BuiltInSnippetModule.createDefaultRegistry(),
        controller = controller,
    )
}

private fun createOverlayDemoAction(presentation: OverlayPresentation): ActionRef {
    val isSheet = presentation == OverlayPresentation.BOTTOM_SHEET
    val dialogId = if (isSheet) "storybook_demo_sheet" else "storybook_demo_dialog"
    return ActionRef(
        id = if (isSheet) "preview_bottom_sheet" else "preview_dialog_overlay",
        type = ActionTypes.OPEN_DIALOG,
        payload =
            buildJsonObject {
                put("dialog_id", dialogId)
                put("title", if (isSheet) "BottomSheet Overlay Preview" else "Dialog Overlay Preview")
                put(
                    "message",
                    "Rendered from an OpenDialogPayload with presentation=${presentation.name.lowercase()}.",
                )
                put("presentation", if (isSheet) "bottom_sheet" else "dialog")
                put("dismissible", true)
                put(
                    "positive_action",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "dismiss_demo_overlay",
                            type = ActionTypes.DISMISS_OVERLAY,
                            payload = buildJsonObject { put("overlay_id", dialogId) },
                        ),
                    ),
                )
            },
    )
}

private fun decodeOverlayRequest(action: ActionRef): ActionOutcome.OverlayRequested? {
    val decoded = BuiltInActionDecoders.createDefaultRegistry().decode(action.type, action.payload)
    val payload = (decoded as? AppResult.Success)?.value as? OpenDialogPayload ?: return null
    return ActionOutcome.OverlayRequested(
        dialogId = payload.dialogId,
        title = payload.title,
        message = payload.message,
        dismissible = payload.dismissible,
        positiveAction = payload.positiveAction,
        negativeAction = payload.negativeAction,
        presentation = payload.presentation,
    )
}

private fun statusLabelFor(state: LayoutUiState): Pair<String, TagVariant> =
    when (state) {
        LayoutUiState.Idle -> "IDLE" to TagVariant.NEUTRAL
        LayoutUiState.Loading -> "LOADING" to TagVariant.INFO
        is LayoutUiState.Ready -> "READY · ${state.page.pageId}" to TagVariant.SUCCESS
        is LayoutUiState.Error -> "ERROR · ${state.error.code}" to TagVariant.ERROR
    }
