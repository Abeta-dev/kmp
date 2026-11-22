package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val BERRIES_PRICE_CENTS = 550
private const val LOAF_PRICE_CENTS = 600
private const val FREE_DELIVERY_THRESHOLD_CENTS = 1500
private const val FRESH_MAX_QTY = 10
private const val FRESH_MARQUEE_VELOCITY = 50
private const val FRESH_NUDGE_SEC = 180
private const val FRESH_NUDGE_PROGRESS = 0.76f
private const val GRID_SPACING_DP = 10
private const val TRACKER_STAGES = 4
private const val TRACKER_ETA_STAGE_0 = 480
private const val TRACKER_ETA_STAGE_1 = 360
private const val TRACKER_ETA_STAGE_2 = 150
private const val CHIP_ICON_DP = 16

internal data class SparkMatchRecord(
    val name: String,
    val role: String,
    val superLike: Boolean,
)

/**
 * End-to-end SDUI screen and snippet fixtures for Abeta Fresh and Abeta Spark
 * in [EndToEndJourneysStory].
 */
internal object EndToEndJourneyFixtures {
    const val FRESH_BERRIES_FIELD = "e2e_fresh_berries"
    const val FRESH_LOAF_FIELD = "e2e_fresh_loaf"
    const val FRESH_PLACE_ORDER_ACTION = "e2e_fresh_place_order"
    const val TRACKER_STAGE_COUNT = TRACKER_STAGES

    fun buildFreshBrowsePage(): PageResponse {
        val grid =
            GridSnippetData(
                title = SnippetsSamples.txt("Abeta Fresh Express Basket", "title.small"),
                columns = 2,
                spacingDp = GRID_SPACING_DP,
                items = buildFreshBasketItems(),
            )
        val gridEnvelope =
            SnippetEnvelope(
                snippetId = "e2e_fresh_grid_env",
                snippetType = SnippetTypes.GRID,
                data = SnippetsSamples.encode(grid),
            )
        return PageResponse(
            pageId = "e2e_fresh_browse",
            snippets = listOf(buildFreshMarqueeEnvelope(), buildFreshNudgeEnvelope(), gridEnvelope),
            floatingCart =
                FloatingCartSpec(
                    label = "Express Cart",
                    etaBadge = TagAtom(label = "8 MIN EXPRESS", variant = TagVariant.SUCCESS),
                    cta =
                        ButtonAtom(
                            label = "Place Express Order",
                            size = ButtonSize.SMALL,
                            onClick =
                                ActionRef(
                                    id = FRESH_PLACE_ORDER_ACTION,
                                    type = ActionTypes.CALL_API,
                                    payload = buildJsonObject { put("endpoint", "/api/v1/orders/express") },
                                ),
                        ),
                    motion = MotionSpec(enter = MotionTransition.SLIDE_UP),
                    freeDeliveryThresholdCents = FREE_DELIVERY_THRESHOLD_CENTS,
                    nudgePrefix = "Add",
                    unlockedLabel = "FREE delivery unlocked!",
                ),
        )
    }

    fun buildFreshTrackerEnvelope(stageIndex: Int): SnippetEnvelope {
        val clamped = stageIndex.coerceIn(0, TRACKER_STAGES - 1)
        val headlines =
            listOf(
                "Order #FR-8824 confirmed at dark store",
                "Quality-checked & sealed in insulated bag",
                "Aarav is 350m away on EV scooter",
                "Delivered to your doorstep in 7 minutes!",
            )
        val etas = listOf(TRACKER_ETA_STAGE_0, TRACKER_ETA_STAGE_1, TRACKER_ETA_STAGE_2, 0)
        val isDone = clamped == TRACKER_STAGES - 1
        val tracker =
            OrderTrackerSnippetData(
                trackerId = "e2e_fresh_tracker",
                orderCode = "#FR-8824",
                headline = SnippetsSamples.txt(headlines[clamped], "title.medium"),
                etaCountdown =
                    etas[clamped].takeIf { it > 0 }?.let { sec ->
                        CountdownAtom(countdownId = "e2e_tracker_eta_$clamped", remainingSeconds = sec, label = "ETA")
                    },
                etaBadge =
                    TagAtom(
                        label = if (isDone) "DELIVERED" else "STEP ${clamped + 1} OF 4",
                        variant = if (isDone) TagVariant.SUCCESS else TagVariant.BRAND,
                    ),
                riderName = "Aarav Patel",
                riderCaption = "EV Scooter · 4.98★ · Contactless Drop",
                steps = buildFreshTrackerSteps(clamped, isDone),
                primaryAction =
                    ButtonAtom(
                        label = "Call Rider",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "e2e_fresh_call_rider", type = ActionTypes.SHOW_TOAST),
                    ),
                secondaryAction =
                    ButtonAtom(
                        label = "Share ETA",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "e2e_fresh_share_eta", type = ActionTypes.SHARE),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "e2e_fresh_tracker_env",
            snippetType = SnippetTypes.ORDER_TRACKER,
            data = SnippetsSamples.encode(tracker),
        )
    }

    fun buildSparkFilterChipsEnvelope(): SnippetEnvelope {
        val chips =
            ChipGroupSnippetData(
                groupId = "e2e_spark_filters",
                title = SnippetsSamples.txt("Abeta Spark Discovery Filters", "label.large", "text.secondary"),
                multiSelect = true,
                chips =
                    listOf(
                        filterChip("verified", "Verified", selected = true, iconName = "check"),
                        filterChip("nearby", "Within 5 km", selected = true),
                        filterChip("design", "Design & Music", badge = "HOT"),
                        filterChip("coffee", "Coffee & Food"),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "e2e_spark_chips_env",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = SnippetsSamples.encode(chips),
        )
    }

    fun buildSparkSwipeDeckEnvelope(): SnippetEnvelope {
        val deck =
            SwipeDeckSnippetData(
                deckId = "e2e_spark_deck",
                cards =
                    listOf(
                        sparkProfileCard(
                            id = "e2e_ananya",
                            name = "Ananya Rao, 27",
                            role = "Design Lead · Bengaluru",
                            bio = "Obsessed with kinetic typography, indie vinyl nights, and weekend pour-overs.",
                            badge = "VERIFIED",
                            variant = TagVariant.SUCCESS,
                            distance = "1.9 km away",
                            tags = listOf("Design", "Vinyl", "Espresso"),
                        ),
                        sparkProfileCard(
                            id = "e2e_devon",
                            name = "Devon Brooks, 30",
                            role = "Sound Architect · Indiranagar",
                            bio = "Synthesizers, rooftop cinema clubs, and late-night ramen spots.",
                            badge = "SUPER PICK",
                            variant = TagVariant.BRAND,
                            distance = "3.2 km away",
                            tags = listOf("Audio", "Cinema", "Ramen"),
                        ),
                        sparkProfileCard(
                            id = "e2e_priya",
                            name = "Priya Menon, 26",
                            role = "Food Stylist · Koramangala",
                            bio = "Baking cardamom brioche and scouting craft roasters across the city.",
                            badge = "NEW",
                            variant = TagVariant.INFO,
                            distance = "4.1 km away",
                            tags = listOf("Baking", "Photography", "Travel"),
                        ),
                    ),
                emptyTitle = SnippetsSamples.txt("Deck Complete", "title.medium"),
                emptyMessage =
                    SnippetsSamples.txt(
                        "Tap Reset Deck to replay profiles or view your Matches Inbox below.",
                        "body.small",
                        "text.secondary",
                    ),
                onUndo = ActionRef(id = "e2e_spark_undo", type = ActionTypes.TRACK_EVENT),
            )
        return SnippetEnvelope(
            snippetId = "e2e_spark_deck_env",
            snippetType = SnippetTypes.SWIPE_DECK,
            data = SnippetsSamples.encode(deck),
        )
    }

    internal fun filterChip(
        id: String,
        label: String,
        selected: Boolean = false,
        iconName: String? = null,
        badge: String? = null,
    ): FilterChipItem =
        FilterChipItem(
            id = id,
            label = label,
            selected = selected,
            icon = iconName?.let { IconAtom(name = it, sizeDp = CHIP_ICON_DP) },
            badge = badge,
            onClick = ActionRef(id = "e2e_chip_$id", type = ActionTypes.TRACK_EVENT),
        )

    private fun buildFreshMarqueeEnvelope(): SnippetEnvelope {
        val marquee =
            MarqueeStripSnippetData(
                stripId = "e2e_fresh_marquee",
                velocityDpPerSec = FRESH_MARQUEE_VELOCITY,
                items =
                    listOf(
                        MarqueeItem(
                            id = "e2e_mq_1",
                            text = "8-Min Express Delivery Active",
                            icon = IconAtom(name = "star"),
                            badge = TagAtom(label = "FRESH", variant = TagVariant.SUCCESS),
                        ),
                        MarqueeItem(
                            id = "e2e_mq_2",
                            text = "Add \$15+ for FREE Express Shipping",
                            badge = TagAtom(label = "DEAL", variant = TagVariant.BRAND),
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "e2e_fresh_mq_env",
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data = SnippetsSamples.encode(marquee),
        )
    }

    private fun buildFreshNudgeEnvelope(): SnippetEnvelope {
        val nudge =
            NudgeBarSnippetData(
                nudgeId = "e2e_fresh_nudge",
                title = SnippetsSamples.txt("Express Slot Reserved · Cross \$15 for Free Delivery", "title.small"),
                icon = IconAtom(name = "notifications", color = ColorTokenRef("icon.brand")),
                badge = TagAtom(label = "8 MIN", variant = TagVariant.BRAND),
                countdown = CountdownAtom(countdownId = "e2e_fresh_cd", remainingSeconds = FRESH_NUDGE_SEC),
                progress = FRESH_NUDGE_PROGRESS,
                dismissible = true,
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "e2e_fresh_nudge_env",
            snippetType = SnippetTypes.NUDGE_BAR,
            data = SnippetsSamples.encode(nudge),
        )
    }

    private fun buildFreshTrackerSteps(
        clamped: Int,
        isDone: Boolean,
    ): List<OrderTrackerStep> {
        val specs =
            listOf(
                Triple("confirmed", "Confirmed", "Now"),
                Triple("packed", "Packed", "+2m"),
                Triple("on_way", "On the Way", "350m"),
                Triple("delivered", "Delivered", "Done"),
            )
        return specs.mapIndexed { idx, (id, label, caption) ->
            OrderTrackerStep(
                id = id,
                label = label,
                caption = caption,
                icon = if (!isDone && idx == clamped) IconAtom(name = "arrow_forward") else null,
                completed = isDone || idx < clamped,
                active = !isDone && idx == clamped,
            )
        }
    }

    private fun buildFreshBasketItems(): List<SnippetEnvelope> {
        val berries =
            ProductCardSnippetData(
                productId = "e2e_berries",
                title = SnippetsSamples.txt("Organic Berries (250g)", "title.small"),
                subtitle = SnippetsSamples.txt("Hand-picked · Chilled", "body.small", "text.secondary"),
                price = SnippetsSamples.txt("\$5.50", "label.large"),
                unitPriceCents = BERRIES_PRICE_CENTS,
                badge = TagAtom(label = "FRESH", variant = TagVariant.SUCCESS),
                stepper = StepperAtom(fieldId = FRESH_BERRIES_FIELD, quantity = 1, maxQuantity = FRESH_MAX_QTY),
                layout = SnippetsSamples.cardLayout,
            )
        val loaf =
            ProductCardSnippetData(
                productId = "e2e_loaf",
                title = SnippetsSamples.txt("Sourdough Country Loaf", "title.small"),
                subtitle = SnippetsSamples.txt("Baked this morning · 450g", "body.small", "text.secondary"),
                price = SnippetsSamples.txt("\$6.00", "label.large"),
                unitPriceCents = LOAF_PRICE_CENTS,
                badge = TagAtom(label = "ARTISAN", variant = TagVariant.BRAND),
                stepper = StepperAtom(fieldId = FRESH_LOAF_FIELD, quantity = 1, maxQuantity = FRESH_MAX_QTY),
                layout = SnippetsSamples.cardLayout,
            )
        return listOf(
            SnippetEnvelope(
                snippetId = "e2e_fresh_berries_env",
                snippetType = SnippetTypes.PRODUCT_CARD,
                data = SnippetsSamples.encode(berries),
            ),
            SnippetEnvelope(
                snippetId = "e2e_fresh_loaf_env",
                snippetType = SnippetTypes.PRODUCT_CARD,
                data = SnippetsSamples.encode(loaf),
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun sparkProfileCard(
        id: String,
        name: String,
        role: String,
        bio: String,
        badge: String,
        variant: TagVariant,
        distance: String,
        tags: List<String>,
    ): SwipeCardItem =
        SwipeCardItem(
            cardId = id,
            title = SnippetsSamples.txt(name, "title.large"),
            subtitle = SnippetsSamples.txt(role, "label.medium", "text.brand"),
            bio = SnippetsSamples.txt(bio, "body.medium", "text.secondary"),
            badge = TagAtom(label = badge, variant = variant),
            distanceText = SnippetsSamples.txt(distance, "label.small", "text.tertiary"),
            tags = tags.map { TagAtom(label = it, variant = TagVariant.NEUTRAL) },
            onLike = sparkDecision(id, name, role, "like", ActionTypes.TRIGGER_FEEDBACK),
            onNope = sparkDecision(id, name, role, "nope", ActionTypes.TRACK_EVENT),
            onSuperLike = sparkDecision(id, name, role, "super_like", ActionTypes.TRIGGER_FEEDBACK),
        )

    private fun sparkDecision(
        id: String,
        name: String,
        role: String,
        decision: String,
        actionType: String,
    ): ActionRef =
        ActionRef(
            id = "e2e_spark_${decision}_$id",
            type = actionType,
            payload =
                buildJsonObject {
                    put("profile_name", name)
                    put("profile_role", role)
                    put("decision", decision)
                },
        )
}
