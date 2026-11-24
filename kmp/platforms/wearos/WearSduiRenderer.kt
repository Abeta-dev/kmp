package dev.abeta.kmp.platforms.wearos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.watch.WatchActionCard
import dev.abeta.kmp.watch.WatchCompanionController
import dev.abeta.kmp.watch.WatchGlancePage
import dev.abeta.kmp.watch.WatchMetricGlance
import dev.abeta.kmp.watch.WatchUiState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Top-level reusable Wear OS SDUI screen wired directly to a [WatchCompanionController].
 * Collects [WatchCompanionController.uiState] lifecycle-safely and delegates rendering to [WearSduiScreen].
 */
@Composable
fun WearSduiScreen(
    controller: WatchCompanionController,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    WearSduiScreen(
        state = state,
        onAction = controller::onAction,
        onRetry = { controller.refresh() },
        onDismissToast = controller::dismissToast,
        modifier = modifier,
        listState = listState,
    )
}

/**
 * Stateless reusable Wear OS SDUI screen rendering [WatchUiState] inside a Wear Compose [Scaffold]
 * with [TimeText], top/bottom [Vignette], and rotary [PositionIndicator].
 */
@Composable
fun WearSduiScreen(
    state: WatchUiState,
    onAction: (ActionRef) -> Unit,
    onRetry: () -> Unit,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
) {
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        when (state) {
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "error_message") {
                        Text(
                            text = state.error.message,
                            style = MaterialTheme.typography.body2,
                        )
                    }
                    item(key = "error_retry") {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Retry") },
                            onClick = onRetry,
                        )
                    }
                }
            }

            is WatchUiState.Ready -> {
                WearGlanceContent(
                    page = state.page,
                    activeToast = state.activeToast,
                    listState = listState,
                    onAction = onAction,
                    onDismissToast = onDismissToast,
                )
            }
        }
    }
}

/**
 * Modular Wear OS `ScalingLazyColumn` body rendering a [WatchGlancePage]'s title, optional
 * [WearOfflineChip], dismissible [WearToastChip], [WearMetricChip] rows, and [WearActionCardChip] rows.
 */
@Composable
fun WearGlanceContent(
    page: WatchGlancePage,
    activeToast: String? = null,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    onAction: (ActionRef) -> Unit,
    onDismissToast: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val toastMessage = activeToast ?: page.lastFeedbackMessage
    ScalingLazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "page_title") {
            Text(text = page.title, style = MaterialTheme.typography.title3)
        }
        if (page.isOffline) {
            item(key = "offline_banner") {
                WearOfflineChip()
            }
        }
        if (!toastMessage.isNullOrBlank()) {
            item(key = "toast_banner") {
                WearToastChip(
                    message = toastMessage,
                    onDismiss = onDismissToast,
                )
            }
        }
        items(items = page.metrics, key = { metric -> "metric_${metric.id}" }) { metric ->
            WearMetricChip(metric = metric, onAction = onAction)
        }
        items(items = page.cards, key = { card -> "card_${card.id}" }) { card ->
            WearActionCardChip(card = card, onAction = onAction)
        }
        item(key = "bottom_clearance") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Reusable Wear OS [Chip] rendering a single [WatchMetricGlance] KPI with optional progress context.
 * Automatically disables clickability when [WatchMetricGlance.onClick] is `null`.
 */
@Composable
fun WearMetricChip(
    metric: WatchMetricGlance,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryText =
        metric.progressLabel
            ?: metric.progressFraction?.let { fraction ->
                val percent = (fraction.coerceIn(0f, 1f) * 100f).toInt()
                "$percent% complete"
            }
    Chip(
        modifier = modifier.fillMaxWidth(),
        enabled = metric.onClick != null,
        label = { Text("${metric.label}: ${metric.value}") },
        secondaryLabel = secondaryText?.let { text -> { Text(text) } },
        onClick = { metric.onClick?.let(onAction) },
    )
}

/**
 * Reusable Wear OS card renderer displaying a [WatchActionCard]'s primary [Chip] (plus optional
 * secondary action [Chip] when [WatchActionCard.secondaryActionLabel] is present).
 */
@Composable
fun WearActionCardChip(
    card: WatchActionCard,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLabel =
        buildString {
            append(card.primaryActionLabel ?: card.title)
            if (!card.badgeLabel.isNullOrBlank()) {
                append(" · ")
                append(card.badgeLabel)
            }
        }
    val secondaryText =
        card.subtitle
            ?: card.title.takeIf { card.primaryActionLabel != null && it != card.primaryActionLabel }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            enabled = card.primaryAction != null,
            label = { Text(primaryLabel) },
            secondaryLabel = secondaryText?.let { sub -> { Text(sub) } },
            onClick = { card.primaryAction?.let(onAction) },
        )
        val secondaryActionLabel = card.secondaryActionLabel?.takeIf { it.isNotBlank() }
        if (secondaryActionLabel != null) {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                enabled = card.secondaryAction != null,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(secondaryActionLabel) },
                onClick = { card.secondaryAction?.let(onAction) },
            )
        }
    }
}

/**
 * Non-interactive status [Chip] indicating the wearable is showing cached offline SDUI content.
 */
@Composable
fun WearOfflineChip(
    modifier: Modifier = Modifier,
    label: String = "Offline · Cached",
) {
    Chip(
        modifier = modifier.fillMaxWidth(),
        enabled = false,
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text(label) },
        onClick = {},
    )
}

/**
 * Dismissible toast [Chip] displaying transient action feedback on Wear OS.
 */
@Composable
fun WearToastChip(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Chip(
        modifier = modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors(),
        label = { Text(message) },
        secondaryLabel = { Text("Tap to dismiss") },
        onClick = onDismiss,
    )
}

/**
 * Compact glance metric slot extracted for Wear OS Tiles, Glance widgets, and watch-face complications.
 */
data class WearTileMetricSlot(
    val id: String,
    val label: String,
    val value: String,
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
)

/**
 * Structured snapshot ready for Wear OS Tile (`TileService`), Jetpack Glance (`GlanceWearTiles`),
 * or Complication data sources without manual SDUI mapping.
 */
data class WearTileSnapshot(
    val pageId: String,
    val title: String,
    val isOffline: Boolean,
    val primaryMetric: WearTileMetricSlot?,
    val secondaryMetrics: List<WearTileMetricSlot>,
    val primaryActionLabel: String?,
    val primaryActionDeepLinkUri: String?,
    val statusMessage: String?,
)

/**
 * Pure mapper translating a [WatchGlancePage] into a bounded [WearTileSnapshot] with 1 primary metric,
 * up to 2 secondary glance metrics, and a normalized primary action deep-link URI.
 */
object WearTileSnapshotMapper {
    const val DEFAULT_DEEP_LINK_SCHEME: String = "abeta"
    const val MAX_SECONDARY_METRICS: Int = 2

    /**
     * Maps [page] into a Tile/Glance/Complication-ready [WearTileSnapshot].
     */
    fun map(
        page: WatchGlancePage,
        deepLinkScheme: String = DEFAULT_DEEP_LINK_SCHEME,
    ): WearTileSnapshot {
        val slots = page.metrics.map { metric -> metric.toSlot() }
        val primaryMetric = slots.firstOrNull()
        val secondaryMetrics = slots.drop(1).take(MAX_SECONDARY_METRICS)

        val primaryCard =
            page.cards.firstOrNull { it.primaryAction != null }
                ?: page.cards.firstOrNull()
        val resolvedAction =
            primaryCard?.primaryAction
                ?: page.metrics.firstOrNull { it.onClick != null }?.onClick
        val resolvedActionLabel =
            primaryCard?.primaryActionLabel
                ?: primaryCard?.title
                ?: primaryMetric?.label

        val deepLinkUri =
            resolveDeepLinkUri(
                pageId = page.pageId,
                action = resolvedAction,
                scheme = deepLinkScheme,
            )

        val statusMessage =
            when {
                !page.lastFeedbackMessage.isNullOrBlank() -> page.lastFeedbackMessage
                page.isOffline -> "Offline · Cached"
                else -> primaryCard?.subtitle
            }

        return WearTileSnapshot(
            pageId = page.pageId,
            title = page.title,
            isOffline = page.isOffline,
            primaryMetric = primaryMetric,
            secondaryMetrics = secondaryMetrics,
            primaryActionLabel = resolvedActionLabel,
            primaryActionDeepLinkUri = deepLinkUri,
            statusMessage = statusMessage,
        )
    }

    private fun WatchMetricGlance.toSlot(): WearTileMetricSlot =
        WearTileMetricSlot(
            id = id,
            label = label,
            value = value,
            progressFraction = progressFraction?.coerceIn(0f, 1f),
            progressLabel = progressLabel,
        )

    private fun resolveDeepLinkUri(
        pageId: String,
        action: ActionRef?,
        scheme: String,
    ): String? {
        val cleanScheme = scheme.trim().ifEmpty { DEFAULT_DEEP_LINK_SCHEME }
        val cleanPageId = pageId.trim().removePrefix("/")

        if (action != null) {
            when (action.type) {
                ActionTypes.OPEN_URL -> {
                    val url = action.stringParam("url")
                    if (!url.isNullOrBlank()) return url
                }

                ActionTypes.NAVIGATE -> {
                    val route = action.stringParam("route")
                    if (!route.isNullOrBlank()) {
                        if (route.contains("://")) return route
                        val cleanRoute = route.trim().removePrefix("/screens/").removePrefix("/")
                        if (cleanRoute.isNotEmpty()) {
                            return "$cleanScheme://screens/$cleanRoute"
                        }
                    }
                }
            }
            if (cleanPageId.isNotEmpty()) {
                val actionKey = action.id?.takeIf { it.isNotBlank() } ?: action.type
                return "$cleanScheme://screens/$cleanPageId?action=$actionKey"
            }
        }

        return cleanPageId.takeIf { it.isNotEmpty() }?.let { "$cleanScheme://screens/$it" }
    }

    private fun ActionRef.stringParam(key: String): String? =
        (payload[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
