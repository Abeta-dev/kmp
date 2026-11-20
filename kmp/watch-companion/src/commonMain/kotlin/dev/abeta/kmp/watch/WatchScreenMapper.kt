package dev.abeta.kmp.watch

import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Pure mapper converting full SDUI [PageResponse] documents into glanceable [WatchGlancePage] models
 * for watchOS and Wear OS circular/rectangular viewports.
 */
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
            appendSnippet(envelope = envelope, metrics = metrics, cards = cards)
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

    private fun appendSnippet(
        envelope: SnippetEnvelope,
        metrics: MutableList<WatchMetricGlance>,
        cards: MutableList<WatchActionCard>,
    ) {
        when (envelope.snippetType) {
            SnippetTypes.METRIC_CARD -> mapMetricCard(envelope)?.let(metrics::add)
            SnippetTypes.CARD -> mapCard(envelope)?.let(cards::add)
            SnippetTypes.BANNER -> mapBanner(envelope)?.let(cards::add)
            SnippetTypes.ACTION_ROW -> mapActionRow(envelope)?.let(cards::add)
            SnippetTypes.HEADER -> mapHeader(envelope)?.let(cards::add)
            SnippetTypes.PRODUCT_CARD -> mapProductCard(envelope)?.let(cards::add)
            else -> Unit
        }
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

    private fun <T> decodeOrNull(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        element: JsonElement,
    ): T? = runCatching { json.decodeFromJsonElement(deserializer, element) }.getOrNull()
}
