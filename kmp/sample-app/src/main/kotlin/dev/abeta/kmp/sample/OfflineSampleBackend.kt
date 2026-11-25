package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.repository.ScreenRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val HEX_RADIX = 16
private const val PERCENT_SEQ_LENGTH = 3
private val FILTER_SYNONYMS: Map<String, Set<String>> =
    mapOf(
        "produce" to setOf("produce", "avocado", "strawberr", "organic", "farm"),
        "dairy" to setOf("dairy", "milk", "chilled"),
        "snacks" to setOf("snack", "almond", "sourdough", "bakery"),
        "drinks" to setOf("drink", "brew", "lassi"),
        "pure_veg" to setOf("veg"),
        "rating_4" to setOf("4."),
        "fast" to setOf("fast", "20m", "20 mins", "24 mins"),
        "electronics" to setOf("electronic", "anc", "mechanical", "iot", "headset"),
        "audio" to setOf("audio", "anc", "headset"),
        "home" to setOf("home", "lamp", "desk"),
        "fashion" to setOf("fashion", "hoodie", "merino"),
        "gst" to setOf("gst"),
        "bills" to setOf("bill", "power", "fiber", "wireless", "utilities"),
    )

/**
 * Offline [ScreenRepository] serving rich built-in sample screens matching the Go BFF catalog,
 * honoring `?q=` search queries and `?filter=` chip selections to filter screen items and sync UI state.
 */
class OfflineScreenRepository : ScreenRepository {
    private val screens: Map<String, PageResponse> = SampleScreens.allPages()

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val trimmed =
            screenId
                .trim()
                .removePrefix("/screens/")
                .removePrefix("/")
        val cleanId = trimmed.substringBefore("?")
        val params = OfflinePageQueryFilter.parseQueryParams(trimmed.substringAfter("?", ""))
        val page = screens[cleanId]
        return if (page != null) {
            val filtered =
                OfflinePageQueryFilter.apply(
                    page = page,
                    query = params["q"].orEmpty(),
                    filter = params["filter"].orEmpty(),
                )
            AppResult.Success(filtered)
        } else {
            AppResult.Failure(
                NotFoundError(
                    resource = cleanId,
                    message = "Screen '$cleanId' not found in offline sample repository.",
                ),
            )
        }
    }
}

@Suppress("TooManyFunctions")
private object OfflinePageQueryFilter {
    fun apply(
        page: PageResponse,
        query: String,
        filter: String,
    ): PageResponse {
        val cleanQuery = query.trim()
        val cleanFilter = filter.trim()
        if (cleanQuery.isEmpty() && cleanFilter.isEmpty()) return page
        val activeFilter =
            cleanFilter
                .takeUnless { it.equals("all", ignoreCase = true) || it.equals("nearby", ignoreCase = true) }
                .orEmpty()
        val updatedSnippets =
            page.snippets.mapNotNull { envelope ->
                transformSnippet(
                    envelope = envelope,
                    query = cleanQuery,
                    selectedFilter = cleanFilter,
                    activeFilter = activeFilter,
                )
            }
        return page.copy(snippets = updatedSnippets)
    }

    fun parseQueryParams(queryString: String): Map<String, String> =
        if (queryString.isBlank()) {
            emptyMap()
        } else {
            buildMap {
                queryString.split('&').forEach { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) {
                        val key = decodePercent(pair.substring(0, idx)).trim().lowercase()
                        val value = decodePercent(pair.substring(idx + 1)).trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            put(key, value)
                        }
                    }
                }
            }
        }

    private fun transformSnippet(
        envelope: SnippetEnvelope,
        query: String,
        selectedFilter: String,
        activeFilter: String,
    ): SnippetEnvelope? =
        when (envelope.snippetType) {
            SnippetTypes.SEARCH_BAR -> updateSearchBar(envelope, query)
            SnippetTypes.CHIP_GROUP -> updateChipGroup(envelope, selectedFilter)
            SnippetTypes.GRID -> filterGrid(envelope, query, activeFilter)
            SnippetTypes.CAROUSEL -> filterCarousel(envelope, query, activeFilter)
            SnippetTypes.SWIPE_DECK -> filterSwipeDeck(envelope, query, activeFilter)
            SnippetTypes.CARD -> filterListingCard(envelope, query, activeFilter)
            SnippetTypes.ACTION_ROW -> filterActionRow(envelope, query, activeFilter)
            else -> envelope
        }

    private fun updateSearchBar(
        envelope: SnippetEnvelope,
        query: String,
    ): SnippetEnvelope {
        val model =
            query
                .takeIf { it.isNotEmpty() }
                ?.let { decodeOrNull(SearchBarSnippetData.serializer(), envelope.data) }
        return if (model != null) {
            envelope.copy(data = encodeObject(SearchBarSnippetData.serializer(), model.copy(query = query)))
        } else {
            envelope
        }
    }

    private fun updateChipGroup(
        envelope: SnippetEnvelope,
        selectedFilter: String,
    ): SnippetEnvelope {
        val model =
            selectedFilter
                .takeIf { it.isNotEmpty() }
                ?.let { decodeOrNull(ChipGroupSnippetData.serializer(), envelope.data) }
                ?.takeIf { data -> data.chips.any { it.id.equals(selectedFilter, ignoreCase = true) } }
        return if (model != null) {
            val updated =
                model.copy(
                    chips =
                        model.chips.map { chip ->
                            chip.copy(selected = chip.id.equals(selectedFilter, ignoreCase = true))
                        },
                )
            envelope.copy(data = encodeObject(ChipGroupSnippetData.serializer(), updated))
        } else {
            envelope
        }
    }

    private fun filterGrid(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): SnippetEnvelope {
        val model =
            if (query.isNotEmpty() || activeFilter.isNotEmpty()) {
                decodeOrNull(GridSnippetData.serializer(), envelope.data)
            } else {
                null
            }
        return if (model != null) {
            val filteredItems = model.items.filter { item -> matchesEnvelope(item, query, activeFilter) }
            envelope.copy(data = encodeObject(GridSnippetData.serializer(), model.copy(items = filteredItems)))
        } else {
            envelope
        }
    }

    private fun filterCarousel(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): SnippetEnvelope {
        val model =
            if (query.isNotEmpty() || activeFilter.isNotEmpty()) {
                decodeOrNull(CarouselSnippetData.serializer(), envelope.data)
            } else {
                null
            }
        val titleMatches = model != null && matchesText(model.title?.text.orEmpty(), query, activeFilter)
        return if (model != null && !titleMatches) {
            val filteredItems = model.items.filter { item -> matchesEnvelope(item, query, activeFilter) }
            envelope.copy(data = encodeObject(CarouselSnippetData.serializer(), model.copy(items = filteredItems)))
        } else {
            envelope
        }
    }

    private fun filterSwipeDeck(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): SnippetEnvelope {
        val model =
            if (query.isNotEmpty() || activeFilter.isNotEmpty()) {
                decodeOrNull(SwipeDeckSnippetData.serializer(), envelope.data)
            } else {
                null
            }
        return if (model != null) {
            val filteredCards =
                model.cards.filter { card ->
                    val searchable =
                        listOfNotNull(
                            card.title.text,
                            card.subtitle?.text,
                            card.bio?.text,
                            card.badge?.label,
                            card.tags.joinToString(" ") { it.label },
                        ).joinToString(" ")
                    matchesText(searchable, query, activeFilter)
                }
            envelope.copy(data = encodeObject(SwipeDeckSnippetData.serializer(), model.copy(cards = filteredCards)))
        } else {
            envelope
        }
    }

    private fun filterListingCard(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): SnippetEnvelope? {
        val isListing =
            envelope.snippetId.startsWith("rest_") ||
                envelope.snippetId.startsWith("ev_") ||
                envelope.snippetId.startsWith("sup_")
        val keep =
            (query.isEmpty() && activeFilter.isEmpty()) ||
                !isListing ||
                matchesEnvelope(envelope, query, activeFilter)
        return envelope.takeIf { keep }
    }

    private fun filterActionRow(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): SnippetEnvelope? {
        val isFilterableRow = envelope.snippetId.startsWith("pay_")
        val keep =
            (query.isEmpty() && activeFilter.isEmpty()) ||
                !isFilterableRow ||
                matchesEnvelope(envelope, query, activeFilter)
        return envelope.takeIf { keep }
    }

    private fun matchesEnvelope(
        envelope: SnippetEnvelope,
        query: String,
        activeFilter: String,
    ): Boolean = matchesText(extractSearchableText(envelope), query, activeFilter)

    private fun extractSearchableText(envelope: SnippetEnvelope): String =
        when (envelope.snippetType) {
            SnippetTypes.PRODUCT_CARD -> {
                decodeOrNull(ProductCardSnippetData.serializer(), envelope.data)
                    ?.let { p ->
                        listOfNotNull(p.productId, p.title.text, p.subtitle?.text, p.badge?.label, p.price.text)
                            .joinToString(" ")
                    }.orEmpty()
            }

            SnippetTypes.CARD -> {
                decodeOrNull(CardSnippetData.serializer(), envelope.data)
                    ?.let { c ->
                        listOfNotNull(c.title.text, c.subtitle?.text, c.description?.text, c.badge?.label)
                            .joinToString(" ")
                    }.orEmpty()
            }

            SnippetTypes.ACTION_ROW -> {
                decodeOrNull(ActionRowSnippetData.serializer(), envelope.data)
                    ?.let { r ->
                        listOfNotNull(r.title.text, r.subtitle?.text, r.trailingTag?.label).joinToString(" ")
                    }.orEmpty()
            }

            else -> {
                envelope.data.toString()
            }
        }

    private fun matchesText(
        haystack: String,
        query: String,
        activeFilter: String,
    ): Boolean {
        val lower = haystack.lowercase()
        val queryOk = query.isEmpty() || lower.contains(query.lowercase())
        val filterOk =
            if (activeFilter.isEmpty()) {
                true
            } else {
                val normalized = activeFilter.lowercase()
                val keywords = FILTER_SYNONYMS[normalized] ?: setOf(normalized, normalized.replace('_', ' '))
                keywords.any { token -> lower.contains(token) }
            }
        return queryOk && filterOk
    }

    private fun <T> decodeOrNull(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        data: JsonObject,
    ): T? = runCatching { SduiJson.decodeFromJsonElement(deserializer, data) }.getOrNull()

    private fun <T> encodeObject(
        serializer: kotlinx.serialization.SerializationStrategy<T>,
        value: T,
    ): JsonObject = SduiJson.encodeToJsonElement(serializer, value) as JsonObject

    private fun decodePercent(input: String): String {
        val normalized = input.replace('+', ' ')
        val out = StringBuilder(normalized.length)
        var index = 0
        while (index < normalized.length) {
            if (normalized[index] == '%' && index + 2 < normalized.length) {
                val bytes = mutableListOf<Byte>()
                var cursor = index
                while (cursor + 2 < normalized.length && normalized[cursor] == '%') {
                    val decoded = normalized.substring(cursor + 1, cursor + PERCENT_SEQ_LENGTH).toIntOrNull(HEX_RADIX)
                    if (decoded == null) break
                    bytes.add(decoded.toByte())
                    cursor += PERCENT_SEQ_LENGTH
                }
                if (bytes.isNotEmpty()) {
                    out.append(bytes.toByteArray().decodeToString())
                    index = cursor
                    continue
                }
            }
            out.append(normalized[index])
            index += 1
        }
        return out.toString()
    }
}

/**
 * Offline [ApiCallExecutor] simulating BFF API execution for sample-app and vertical actions.
 */
class OfflineApiCallExecutor : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val followUps = resolveFollowUps(payload)
        return AppResult.Success(
            ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = true,
                statusCode = HTTP_OK,
                responseBody =
                    buildJsonObject {
                        put("status", "executed")
                        put("endpoint", payload.endpoint)
                        put("offline", true)
                    },
                followUpActions = followUps,
            ),
        )
    }

    private fun resolveFollowUps(payload: CallApiPayload): List<ActionRef> =
        when (payload.endpoint.substringBefore("?")) {
            "/api/v1/orders/express",
            "/api/v1/fresh/orders/express",
            -> {
                celebrationWithToast(
                    idPrefix = "express_order",
                    title = "Order Confirmed!",
                    subtitle = "Abeta Fresh rider arriving in 8 minutes",
                    toastMessage = "Express order confirmed!",
                )
            }

            "/api/v1/bites/orders" -> {
                bitesOrderFollowUps()
            }

            "/api/v1/pulse/bookings" -> {
                celebrationWithToast(
                    idPrefix = "pulse_booking",
                    title = "Tickets Booked!",
                    subtitle = "Instant QR passes saved to your Abeta Pulse wallet",
                    toastMessage = "Tickets Booked! QR passes ready.",
                )
            }

            "/api/v1/mart/orders" -> {
                celebrationWithToast(
                    idPrefix = "mart_order",
                    title = "Order Placed!",
                    subtitle = "Same-day dispatch scheduled from Abeta Mart Hub",
                    toastMessage = "Order Placed! Tracking details sent.",
                )
            }

            "/api/v1/spark/swipe" -> {
                sparkSwipeFollowUps(payload)
            }

            "/api/v1/trade/rfq" -> {
                celebrationWithToast(
                    idPrefix = "trade_rfq",
                    title = "RFQ Submitted!",
                    subtitle = "Quote request #RFQ-9042 dispatched to supplier",
                    toastMessage = "RFQ #RFQ-9042 submitted to supplier!",
                )
            }

            "/api/v1/pay/transfer" -> {
                payTransferFollowUps()
            }

            else -> {
                emptyList()
            }
        }

    private fun bitesOrderFollowUps(): List<ActionRef> =
        celebrationWithToast(
            idPrefix = "bites_order",
            title = "Meal Order Placed!",
            subtitle = "Saffron Hearth Kitchen is preparing your order",
            toastMessage = "Order #BTS-4091 placed! Live tracking ready.",
        ) +
            ActionRef(
                id = "bites_order_nav_track",
                type = ActionTypes.NAVIGATE,
                payload = buildJsonObject { put("route", "bites-order-track") },
            )

    private fun payTransferFollowUps(): List<ActionRef> =
        celebrationWithToast(
            idPrefix = "pay_transfer",
            title = "Transfer Sent!",
            subtitle = "Instant settlement #PAY-3081 confirmed via Abeta Pay",
            toastMessage = "Transfer #PAY-3081 settled instantly!",
        ) +
            ActionRef(
                id = "pay_transfer_nav_receipt",
                type = ActionTypes.NAVIGATE,
                payload = buildJsonObject { put("route", "pay-receipt") },
            )

    private fun sparkSwipeFollowUps(payload: CallApiPayload): List<ActionRef> {
        val swipeAction =
            payload.body
                ?.get("action")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.lowercase()
        return if (swipeAction == "like" || swipeAction == "super_like") {
            celebrationWithToast(
                idPrefix = "spark_match",
                title = "It's a Match!",
                subtitle = "You both sparked — start a conversation in Matches",
                toastMessage = "It's a Match!",
            )
        } else {
            listOf(
                ActionRef(
                    id = "spark_pass_toast",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Passed — showing next profile")
                            put("duration_ms", PASS_TOAST_DURATION_MS)
                            put("variant", "info")
                        },
                ),
            )
        }
    }

    private fun celebrationWithToast(
        idPrefix: String,
        title: String,
        subtitle: String,
        toastMessage: String,
    ): List<ActionRef> =
        listOf(
            ActionRef(
                id = "${idPrefix}_celebration",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "celebration")
                        put("title", title)
                        put("subtitle", subtitle)
                        put("duration_ms", CELEBRATION_DURATION_MS)
                    },
            ),
            ActionRef(
                id = "${idPrefix}_toast",
                type = ActionTypes.SHOW_TOAST,
                payload =
                    buildJsonObject {
                        put("message", toastMessage)
                        put("duration_ms", TOAST_DURATION_MS)
                        put("variant", "success")
                    },
            ),
        )

    private companion object {
        const val HTTP_OK = 200
        const val CELEBRATION_DURATION_MS = 2400L
        const val TOAST_DURATION_MS = 2500L
        const val PASS_TOAST_DURATION_MS = 1600L
    }
}
