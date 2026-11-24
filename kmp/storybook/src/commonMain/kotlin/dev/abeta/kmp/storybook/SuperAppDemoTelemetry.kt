package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import dev.abeta.kmp.analytics.AnalyticsEvent
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.AnalyticsTrackingPayload
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_TELEMETRY_HISTORY = 24
private const val EXPANDED_TELEMETRY_ROWS = 4

internal enum class LiveTelemetryKind(
    val label: String,
    val tone: TagVariant,
) {
    PAGE_OPEN("page_open", TagVariant.BRAND),
    SNIPPET_IMPRESSION("snippet_impression", TagVariant.INFO),
    NODE_IMPRESSION("node_impression", TagVariant.SUCCESS),
    INTERACTION_CLICK("interaction_click", TagVariant.WARNING),
    OTHER("event", TagVariant.NEUTRAL),
}

internal data class LiveTelemetryEntry(
    val sequence: Int,
    val kind: LiveTelemetryKind,
    val eventName: String,
    val summary: String,
)

internal data class LiveTelemetrySnapshot(
    val pageOpenCount: Int = 0,
    val snippetImpressionCount: Int = 0,
    val nodeImpressionCount: Int = 0,
    val interactionClickCount: Int = 0,
    val totalCount: Int = 0,
    val latestEntries: List<LiveTelemetryEntry> = emptyList(),
)

/**
 * Reactive [AnalyticsTracker] wrapping [RecordingAnalyticsTracker] to stream real-time
 * `page_open`, `snippet_impression`, `node_impression`, and `interaction_click` events into Storybook UI.
 */
internal class LiveStorybookAnalyticsTracker(
    val recorder: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
) : AnalyticsTracker {
    private val _snapshot = MutableStateFlow(LiveTelemetrySnapshot())
    val snapshot: StateFlow<LiveTelemetrySnapshot> = _snapshot.asStateFlow()

    override fun track(event: AnalyticsEvent) {
        recorder.track(event)
        val kind =
            when (event.name) {
                "page_open" -> LiveTelemetryKind.PAGE_OPEN
                "interaction_click" -> LiveTelemetryKind.INTERACTION_CLICK
                else -> LiveTelemetryKind.OTHER
            }
        val target =
            listOfNotNull(
                event.properties["page_id"] ?: event.screenName,
                event.properties["action_id"],
                event.properties["action_type"],
                event.properties["component_id"],
                event.properties["product_id"],
            ).distinct().joinToString(" · ")
        appendRecord(kind = kind, eventName = event.name, summary = target.ifEmpty { event.name })
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        recorder.trackImpression(payload)
        val kind =
            when {
                "node_id" in payload.properties || payload.eventName == "node_impression" -> {
                    LiveTelemetryKind.NODE_IMPRESSION
                }

                "snippet_id" in payload.properties || payload.eventName == "snippet_impression" -> {
                    LiveTelemetryKind.SNIPPET_IMPRESSION
                }

                else -> {
                    LiveTelemetryKind.OTHER
                }
            }
        val target =
            listOfNotNull(
                payload.properties["page_id"],
                payload.properties["node_id"] ?: payload.properties["snippet_id"],
                payload.properties["molecule"],
            ).distinct().joinToString(" · ")
        appendRecord(kind = kind, eventName = payload.eventName, summary = target.ifEmpty { payload.eventName })
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        recorder.identify(userId, traits)
    }

    override fun reset() {
        recorder.reset()
    }

    fun clear() {
        recorder.clear()
        _snapshot.value = LiveTelemetrySnapshot()
    }

    private fun appendRecord(
        kind: LiveTelemetryKind,
        eventName: String,
        summary: String,
    ) {
        _snapshot.update { current ->
            val nextSeq = current.totalCount + 1
            val entry = LiveTelemetryEntry(sequence = nextSeq, kind = kind, eventName = eventName, summary = summary)
            current.copy(
                pageOpenCount = current.pageOpenCount + if (kind == LiveTelemetryKind.PAGE_OPEN) 1 else 0,
                snippetImpressionCount =
                    current.snippetImpressionCount + if (kind == LiveTelemetryKind.SNIPPET_IMPRESSION) 1 else 0,
                nodeImpressionCount =
                    current.nodeImpressionCount + if (kind == LiveTelemetryKind.NODE_IMPRESSION) 1 else 0,
                interactionClickCount =
                    current.interactionClickCount + if (kind == LiveTelemetryKind.INTERACTION_CLICK) 1 else 0,
                totalCount = nextSeq,
                latestEntries = (listOf(entry) + current.latestEntries).take(MAX_TELEMETRY_HISTORY),
            )
        }
    }
}

/**
 * Side-by-side Live Analytics Telemetry Drawer displayed alongside the phone/watch frame on wide
 * viewports so users see real-time counts and streaming events without reducing phone height.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SuperAppAnalyticsDrawerPanel(
    snapshot: LiveTelemetrySnapshot,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.lg)

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .testTag("super_app_analytics_strip")
                .clip(shape)
                .background(colors.surface.primary, shape)
                .border(ThemeTokens.strokeWidth.thin, colors.border.subtle, shape)
                .padding(spacing.space12),
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live Analytics (${snapshot.totalCount})",
                style = typography.titleSmall,
                color = colors.text.primary,
            )
            TextButton(onClick = onClear) { Text("Clear Log") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            TelemetryCounterTags(snapshot = snapshot)
        }
        UiDivider(DividerAtom())
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            if (snapshot.latestEntries.isEmpty()) {
                Text(
                    text = "Navigate, scroll, or tap controls to stream live analytics events.",
                    style = typography.caption,
                    color = colors.text.tertiary,
                )
            } else {
                snapshot.latestEntries.forEach { entry ->
                    Text(
                        text = "#${entry.sequence} [${entry.eventName}] ${entry.summary}",
                        style = typography.caption.copy(fontFamily = typography.fonts.mono),
                        color = colors.text.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compact collapsible Live Analytics Telemetry Strip used below the viewport on narrow widths or
 * full-width tablet/desktop viewport modes.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun SuperAppAnalyticsTelemetryStrip(
    snapshot: LiveTelemetrySnapshot,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.md)
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("super_app_analytics_strip")
                .clip(shape)
                .background(colors.surface.primary, shape)
                .border(ThemeTokens.strokeWidth.thin, colors.border.subtle, shape)
                .padding(horizontal = spacing.space12, vertical = spacing.space4),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Live Analytics (${snapshot.totalCount})",
                    style = typography.labelMedium,
                    color = colors.text.primary,
                )
                TelemetryCounterTags(snapshot = snapshot)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Compact" else "Inspect") }
                TextButton(onClick = onClear) { Text("Clear Log") }
            }
        }
        if (expanded) {
            snapshot.latestEntries.take(EXPANDED_TELEMETRY_ROWS).forEach { entry ->
                Text(
                    text = "#${entry.sequence} [${entry.eventName}] ${entry.summary}",
                    style = typography.caption.copy(fontFamily = typography.fonts.mono),
                    color = colors.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun TelemetryCounterTags(snapshot: LiveTelemetrySnapshot) {
    UiTag(TagAtom(label = "page_open: ${snapshot.pageOpenCount}", variant = TagVariant.BRAND))
    UiTag(TagAtom(label = "snippet_impression: ${snapshot.snippetImpressionCount}", variant = TagVariant.INFO))
    UiTag(TagAtom(label = "node_impression: ${snapshot.nodeImpressionCount}", variant = TagVariant.SUCCESS))
    UiTag(TagAtom(label = "interaction_click: ${snapshot.interactionClickCount}", variant = TagVariant.WARNING))
}
