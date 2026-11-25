package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TabsSnippetData
import kotlinx.serialization.json.Json

private const val CENTS_PER_DOLLAR = 100L
private const val CENTS_PAD_DIGITS = 2

/**
 * Live aggregated shopping cart summary derived from [ProductCardSnippetData] snippets and
 * current [LayoutUiState.Ready.formValues].
 */
internal data class FloatingCartSummary(
    val hasProductCards: Boolean,
    val itemCount: Int,
    val totalCents: Long,
) {
    /** Formats `"<n> item(s) · $d.cc"` using commonMultiplatform-safe arithmetic. */
    fun formattedLabel(): String {
        val unitWord = if (itemCount == 1) "item" else "items"
        return "$itemCount $unitWord · ${formatUsdCents(totalCents)}"
    }
}

internal fun collectAllEnvelopes(
    envelopes: List<SnippetEnvelope>,
    json: Json = SduiJson,
): List<SnippetEnvelope> {
    val collected = mutableListOf<SnippetEnvelope>()
    val queue = ArrayDeque(envelopes)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        collected.add(current)
        extractChildEnvelopes(current, json).forEach(queue::addLast)
    }
    return collected
}

internal fun extractProductCards(
    page: PageResponse,
    json: Json = SduiJson,
): List<ProductCardSnippetData> =
    collectAllEnvelopes(page.snippets, json)
        .asSequence()
        .filter { it.snippetType == SnippetTypes.PRODUCT_CARD }
        .mapNotNull { decodeOrNull(json, ProductCardSnippetData.serializer(), it) }
        .toList()

internal fun computeFloatingCartSummary(
    page: PageResponse,
    formValues: Map<String, String>,
    json: Json = SduiJson,
): FloatingCartSummary = computeFloatingCartSummary(extractProductCards(page, json), formValues)

internal fun computeFloatingCartSummary(
    productCards: List<ProductCardSnippetData>,
    formValues: Map<String, String>,
): FloatingCartSummary {
    val seenProducts = hashSetOf<String>()
    var totalItems = 0L
    var totalCents = 0L

    for (product in productCards) {
        if (seenProducts.add(product.productId)) {
            val minQty = product.stepper.minQuantity.coerceAtLeast(0)
            val maxQty = product.stepper.maxQuantity.coerceAtLeast(minQty)
            val rawQty = formValues[product.stepper.fieldId]?.toIntOrNull() ?: product.stepper.quantity
            val qty = rawQty.coerceIn(minQty, maxQty)
            totalItems += qty.toLong()
            totalCents += qty.toLong() * product.unitPriceCents.coerceAtLeast(0).toLong()
        }
    }

    return FloatingCartSummary(
        hasProductCards = productCards.isNotEmpty(),
        itemCount = totalItems.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        totalCents = totalCents,
    )
}

internal fun formatUsdCents(cents: Long): String {
    val safeCents = cents.coerceAtLeast(0L)
    val dollars = safeCents / CENTS_PER_DOLLAR
    val remainder = safeCents % CENTS_PER_DOLLAR
    return "$$dollars.${remainder.toString().padStart(CENTS_PAD_DIGITS, '0')}"
}

internal fun formatUsdCents(cents: Int): String = formatUsdCents(cents.toLong())

private fun extractChildEnvelopes(
    envelope: SnippetEnvelope,
    json: Json,
): List<SnippetEnvelope> =
    when (envelope.snippetType) {
        SnippetTypes.CAROUSEL -> {
            decodeOrNull(json, CarouselSnippetData.serializer(), envelope)?.items.orEmpty()
        }

        SnippetTypes.GRID -> {
            decodeOrNull(json, GridSnippetData.serializer(), envelope)?.items.orEmpty()
        }

        SnippetTypes.TABS -> {
            decodeOrNull(json, TabsSnippetData.serializer(), envelope)
                ?.tabs
                ?.flatMap { it.snippets }
                .orEmpty()
        }

        SnippetTypes.ACCORDION -> {
            decodeOrNull(json, AccordionSnippetData.serializer(), envelope)?.snippets.orEmpty()
        }

        else -> {
            emptyList()
        }
    }

private fun <T> decodeOrNull(
    json: Json,
    serializer: kotlinx.serialization.DeserializationStrategy<T>,
    envelope: SnippetEnvelope,
): T? = runCatching { json.decodeFromJsonElement(serializer, envelope.data) }.getOrNull()
