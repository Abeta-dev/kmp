package dev.abeta.kmp.watch

import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.CustomSlotSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.GraphCanvasSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TabsSnippetData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val MAX_TRAVERSAL_DEPTH = 6
private const val SECONDS_PER_MINUTE = 60
private const val MAX_SUBTITLE_PARTS = 2

/**
 * Pure mapper converting full SDUI [PageResponse] documents into glanceable [WatchGlancePage] models
 * for watchOS and Wear OS circular/rectangular viewports.
 */
@Suppress("TooManyFunctions")
class WatchScreenMapper(
    private val json: Json = SduiJson,
) {
    /** Maps [page] into compact watch metrics and 1-tap action cards, preserving offline hint [isOffline]. */
    fun map(
        page: PageResponse,
        isOffline: Boolean = false,
    ): WatchGlancePage {
        val metrics = mutableListOf<WatchMetricGlance>()
        val cards = mutableListOf<WatchActionCard>()
        page.snippets.forEach { envelope ->
            appendSnippet(envelope = envelope, metrics = metrics, cards = cards, depth = 0)
        }
        val title = page.title?.takeIf { it.isNotBlank() } ?: page.toolbar?.title?.text ?: page.pageId
        return WatchGlancePage(
            pageId = page.pageId,
            title = title,
            isOffline = isOffline,
            metrics = metrics.toList(),
            cards = cards.toList(),
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun appendSnippet(
        envelope: SnippetEnvelope,
        metrics: MutableList<WatchMetricGlance>,
        cards: MutableList<WatchActionCard>,
        depth: Int,
    ) {
        if (depth > MAX_TRAVERSAL_DEPTH) return
        when (envelope.snippetType) {
            SnippetTypes.CAROUSEL,
            SnippetTypes.GRID,
            SnippetTypes.TABS,
            SnippetTypes.ACCORDION,
            SnippetTypes.CUSTOM_SLOT,
            -> appendContainerSnippet(envelope, metrics, cards, depth)

            SnippetTypes.METRIC_CARD -> mapMetricCard(envelope)?.let(metrics::add)

            SnippetTypes.COMPOSED_BLOCK -> appendComposedBlock(envelope, metrics, cards)

            else -> mapLeafCard(envelope)?.let(cards::add)
        }
    }

    private fun appendContainerSnippet(
        envelope: SnippetEnvelope,
        metrics: MutableList<WatchMetricGlance>,
        cards: MutableList<WatchActionCard>,
        depth: Int,
    ) {
        val nextDepth = depth + 1
        when (envelope.snippetType) {
            SnippetTypes.CAROUSEL -> {
                decodeOrNull(CarouselSnippetData.serializer(), envelope.data)?.items?.forEach { child ->
                    appendSnippet(child, metrics, cards, nextDepth)
                }
            }

            SnippetTypes.GRID -> {
                decodeOrNull(GridSnippetData.serializer(), envelope.data)?.items?.forEach { child ->
                    appendSnippet(child, metrics, cards, nextDepth)
                }
            }

            SnippetTypes.TABS -> {
                decodeOrNull(TabsSnippetData.serializer(), envelope.data)?.tabs?.forEach { tab ->
                    tab.snippets.forEach { child ->
                        appendSnippet(child, metrics, cards, nextDepth)
                    }
                }
            }

            SnippetTypes.ACCORDION -> {
                decodeOrNull(AccordionSnippetData.serializer(), envelope.data)?.snippets?.forEach { child ->
                    appendSnippet(child, metrics, cards, nextDepth)
                }
            }

            SnippetTypes.CUSTOM_SLOT -> {
                decodeOrNull(CustomSlotSnippetData.serializer(), envelope.data)?.fallback?.let { fallback ->
                    appendSnippet(fallback, metrics, cards, nextDepth)
                }
            }

            else -> {}
        }
    }

    private fun mapLeafCard(envelope: SnippetEnvelope): WatchActionCard? =
        when (envelope.snippetType) {
            SnippetTypes.CARD -> mapCard(envelope)
            SnippetTypes.BANNER -> mapBanner(envelope)
            SnippetTypes.ACTION_ROW -> mapActionRow(envelope)
            SnippetTypes.HEADER -> mapHeader(envelope)
            SnippetTypes.PRODUCT_CARD -> mapProductCard(envelope)
            SnippetTypes.ORDER_TRACKER -> mapOrderTracker(envelope)
            SnippetTypes.NUDGE_BAR -> mapNudgeBar(envelope)
            SnippetTypes.MARQUEE_STRIP -> mapMarqueeStrip(envelope)
            SnippetTypes.GRAPH_CANVAS -> mapGraphCanvas(envelope)
            SnippetTypes.EMPTY_STATE -> mapEmptyState(envelope)
            else -> null
        }

    private fun mapMetricCard(envelope: SnippetEnvelope): WatchMetricGlance? =
        decodeOrNull(MetricCardSnippetData.serializer(), envelope.data)?.let { data ->
            WatchMetricGlance(
                id = envelope.snippetId,
                label = data.label.text,
                value = data.value.text,
                progressFraction = data.progress?.progress,
                progressLabel = data.progress?.label,
                onClick = data.onClick,
            )
        }

    private fun mapCard(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(CardSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.subtitle?.text ?: data.description?.text,
                badgeLabel = data.badge?.label,
                primaryActionLabel = data.primaryButton?.label,
                primaryAction = data.primaryButton?.onClick ?: data.onClick,
                secondaryActionLabel = data.secondaryButton?.label,
                secondaryAction = data.secondaryButton?.onClick,
            )
        }

    private fun mapBanner(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(BannerSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.body?.text,
                badgeLabel = data.tag?.label,
                primaryActionLabel = data.cta?.label,
                primaryAction = data.cta?.onClick ?: data.onClick,
            )
        }

    private fun mapActionRow(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(ActionRowSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.subtitle?.text,
                badgeLabel = data.trailingTag?.label,
                primaryAction = data.onClick,
            )
        }

    private fun mapHeader(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(HeaderSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.subtitle?.text,
                badgeLabel = data.trailingTag?.label,
                primaryActionLabel = data.trailingAction?.label,
                primaryAction = data.trailingAction?.onClick ?: data.onClick,
            )
        }

    private fun mapProductCard(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(ProductCardSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.price.text,
                badgeLabel = data.badge?.label,
                primaryActionLabel = "Add",
                primaryAction = data.stepper.onIncrement ?: data.onClick,
            )
        }

    private fun mapOrderTracker(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(OrderTrackerSnippetData.serializer(), envelope.data)?.let { data ->
            val activeStep =
                data.steps.firstOrNull { it.active }?.label
                    ?: data.steps.lastOrNull { it.completed }?.label
            val countdownEta = data.etaCountdown?.let(::formatCountdownEta)
            val badge = data.etaBadge?.label ?: countdownEta ?: data.orderCode.takeIf { it.isNotBlank() }
            val subtitleParts =
                listOfNotNull(
                    activeStep,
                    countdownEta ?: data.etaBadge?.label,
                    data.riderName?.takeIf { it.isNotBlank() },
                ).distinct()
            WatchActionCard(
                id = envelope.snippetId,
                title = data.headline.text.ifBlank { data.orderCode },
                subtitle = subtitleParts.joinToString(" · ").ifBlank { data.orderCode.takeIf { it.isNotBlank() } },
                badgeLabel = badge,
                primaryActionLabel = data.primaryAction?.label,
                primaryAction = data.primaryAction?.onClick,
                secondaryActionLabel = data.secondaryAction?.label,
                secondaryAction = data.secondaryAction?.onClick,
            )
        }

    private fun mapNudgeBar(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(NudgeBarSnippetData.serializer(), envelope.data)?.let { data ->
            val eta = data.countdown?.let(::formatCountdownEta)
            val subtitle =
                listOfNotNull(
                    data.subtitle?.text?.takeIf { it.isNotBlank() },
                    eta,
                ).joinToString(" · ").ifBlank { null }
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = subtitle,
                badgeLabel = data.badge?.label,
                primaryActionLabel = data.cta?.label,
                primaryAction = data.cta?.onClick ?: data.onClick,
            )
        }

    private fun mapMarqueeStrip(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(MarqueeStripSnippetData.serializer(), envelope.data)?.let { data ->
            val first = data.items.firstOrNull() ?: return@let null
            val subtitle =
                data.items
                    .drop(1)
                    .mapNotNull { it.text.takeIf(String::isNotBlank) }
                    .take(MAX_SUBTITLE_PARTS)
                    .joinToString(" ${data.separator} ")
                    .ifBlank { null }
            val badge = first.badge?.label ?: data.items.firstNotNullOfOrNull { it.badge?.label }
            val primaryAction = data.items.firstNotNullOfOrNull { it.onClick }
            WatchActionCard(
                id = envelope.snippetId,
                title = first.text,
                subtitle = subtitle,
                badgeLabel = badge,
                primaryAction = primaryAction,
            )
        }

    private fun mapGraphCanvas(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(GraphCanvasSnippetData.serializer(), envelope.data)?.let { data ->
            val summary = "${data.nodes.size} nodes · ${data.edges.size} edges"
            val subtitle =
                data.subtitle
                    ?.text
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$summary · $it" }
                    ?: summary
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title?.text?.takeIf { it.isNotBlank() } ?: data.canvasId,
                subtitle = subtitle,
                badgeLabel = "${data.nodes.size} NODES",
                primaryAction = data.onCanvasTap ?: data.nodes.firstNotNullOfOrNull { it.onClick },
            )
        }

    private fun mapEmptyState(envelope: SnippetEnvelope): WatchActionCard? =
        decodeOrNull(EmptyStateSnippetData.serializer(), envelope.data)?.let { data ->
            WatchActionCard(
                id = envelope.snippetId,
                title = data.title.text,
                subtitle = data.message?.text,
                primaryActionLabel = data.actionButton?.label,
                primaryAction = data.actionButton?.onClick,
            )
        }

    private fun appendComposedBlock(
        envelope: SnippetEnvelope,
        metrics: MutableList<WatchMetricGlance>,
        cards: MutableList<WatchActionCard>,
    ) {
        val data = decodeOrNull(ComposedBlockSnippetData.serializer(), envelope.data) ?: return
        val extracted = ComposedExtraction()
        collectComposedNode(
            snippetId = envelope.snippetId,
            node = data.root,
            depth = 0,
            out = extracted,
        )
        metrics.addAll(extracted.metrics)
        val title =
            extracted.texts.firstOrNull()
                ?: data.recipeName?.replace('_', ' ')?.takeIf { it.isNotBlank() }
        if (title != null) {
            val subtitle =
                extracted.texts
                    .drop(1)
                    .take(MAX_SUBTITLE_PARTS)
                    .joinToString(" · ")
                    .ifBlank { null }
            val primaryBtn = extracted.buttons.firstOrNull()
            val secondaryBtn = extracted.buttons.getOrNull(1)
            cards.add(
                WatchActionCard(
                    id = envelope.snippetId,
                    title = title,
                    subtitle = subtitle,
                    badgeLabel = extracted.badges.firstOrNull(),
                    primaryActionLabel = primaryBtn?.label,
                    primaryAction = primaryBtn?.onClick ?: extracted.fallbackAction,
                    secondaryActionLabel = secondaryBtn?.label,
                    secondaryAction = secondaryBtn?.onClick,
                ),
            )
        }
    }

    private fun collectComposedNode(
        snippetId: String,
        node: ComposedNode,
        depth: Int,
        out: ComposedExtraction,
    ) {
        if (depth > MAX_TRAVERSAL_DEPTH) return
        extractComposedAtomFields(snippetId = snippetId, node = node, out = out)
        node.children.forEach { child ->
            collectComposedNode(snippetId = snippetId, node = child, depth = depth + 1, out = out)
        }
    }

    private fun extractComposedAtomFields(
        snippetId: String,
        node: ComposedNode,
        out: ComposedExtraction,
    ) {
        extractComposedMetricsAndTexts(snippetId = snippetId, node = node, out = out)
        extractComposedTagsAndActions(node = node, out = out)
    }

    private fun extractComposedMetricsAndTexts(
        snippetId: String,
        node: ComposedNode,
        out: ComposedExtraction,
    ) {
        node.statTile?.let { stat ->
            val metricId = node.nodeId?.takeIf { it.isNotBlank() } ?: "${snippetId}_stat_${out.metrics.size}"
            out.metrics.add(
                WatchMetricGlance(
                    id = metricId,
                    label = stat.label,
                    value = stat.value,
                    progressLabel = stat.deltaLabel ?: stat.caption,
                    onClick = stat.onClick ?: node.onClick,
                ),
            )
        }
        node.text
            ?.text
            ?.takeIf { it.isNotBlank() }
            ?.let(out.texts::add)
        node.counterValue?.takeIf { it.isNotBlank() }?.let(out.texts::add)
        node.price
            ?.amountText
            ?.takeIf { it.isNotBlank() }
            ?.let(out.texts::add)
        node.alertBanner?.let { alert ->
            if (alert.title.isNotBlank()) out.texts.add(alert.title)
            alert.message?.takeIf { it.isNotBlank() }?.let(out.texts::add)
        }
        node.keyValueRow?.let { kv ->
            val combined = "${kv.label}: ${kv.value}".trim(':', ' ')
            if (combined.isNotEmpty()) out.texts.add(combined)
        }
    }

    private fun extractComposedTagsAndActions(
        node: ComposedNode,
        out: ComposedExtraction,
    ) {
        node.tag
            ?.label
            ?.takeIf { it.isNotBlank() }
            ?.let(out.badges::add)
        node.badge
            ?.label
            ?.takeIf { it.isNotBlank() }
            ?.let(out.badges::add)
        node.button?.let(out.buttons::add)
        val nodeAction = node.onClick ?: node.alertBanner?.onAction ?: node.chip?.onClick ?: node.text?.onClick
        if (nodeAction != null && out.fallbackAction == null) {
            out.fallbackAction = nodeAction
        }
    }

    private fun formatCountdownEta(countdown: CountdownAtom): String {
        val minutes = ((countdown.remainingSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1)
        return countdown.label
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it ${minutes}m" }
            ?: "${minutes}m"
    }

    private fun <T> decodeOrNull(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        element: JsonElement,
    ): T? = runCatching { json.decodeFromJsonElement(deserializer, element) }.getOrNull()

    private class ComposedExtraction {
        val metrics = mutableListOf<WatchMetricGlance>()
        val texts = mutableListOf<String>()
        val badges = mutableListOf<String>()
        val buttons = mutableListOf<ButtonAtom>()
        var fallbackAction: ActionRef? = null
    }
}
