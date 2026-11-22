package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
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
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val JUICE_PRICE_CENTS = 450
private const val GRANOLA_PRICE_CENTS = 600
private const val FREE_DELIVERY_THRESHOLD_CENTS = 1500
private const val PRODUCT_MAX_QTY = 10
private const val GRID_SPACING_DP = 10
private const val LAB_MARQUEE_VELOCITY = 52
private const val LAB_NUDGE_COUNTDOWN_SEC = 140
private const val LAB_NUDGE_PROGRESS = 0.68f
private const val LAB_TRACKER_STAGE_COUNT = 4
private const val STAGE_ZERO_ETA = 540
private const val STAGE_ONE_ETA = 420
private const val STAGE_TWO_ETA = 210
private const val CENTS_PER_DOLLAR = 100
private const val CENTS_PAD_DIGITS = 2
private const val ICON_SM_DP = 18

internal data class LoopEffectSpecimen(
    val effect: MotionLoopEffect,
    val title: String,
    val useCase: String,
    val badgeLabel: String,
    val badgeVariant: TagVariant,
)

internal data class PressBounceSpecimen(
    val id: String,
    val title: String,
    val caption: String,
    val badgeLabel: String,
)

/**
 * Fixtures and SDUI envelope builders for [AnimationNudgesStory].
 */
internal object AnimationNudgesFixtures {
    const val JUICE_FIELD_ID = "lab_cart_juice"
    const val GRANOLA_FIELD_ID = "lab_cart_granola"
    const val TRACKER_STAGE_COUNT = LAB_TRACKER_STAGE_COUNT

    val loopSpecimens: List<LoopEffectSpecimen> =
        listOf(
            LoopEffectSpecimen(
                effect = MotionLoopEffect.PULSE,
                title = "PULSE",
                useCase = "8-Min Express Badges",
                badgeLabel = "8 MIN",
                badgeVariant = TagVariant.SUCCESS,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.SHIMMER,
                title = "SHIMMER",
                useCase = "Flash Deal Highlights",
                badgeLabel = "FLASH",
                badgeVariant = TagVariant.BRAND,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.WIGGLE,
                title = "WIGGLE",
                useCase = "Offer & Cart Nudge",
                badgeLabel = "SAVE",
                badgeVariant = TagVariant.WARNING,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.FLOAT,
                title = "FLOAT",
                useCase = "Hero Offer Callouts",
                badgeLabel = "NEW",
                badgeVariant = TagVariant.INFO,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.HEARTBEAT,
                title = "HEARTBEAT",
                useCase = "Urgency Timers (<30s)",
                badgeLabel = "URGENT",
                badgeVariant = TagVariant.ERROR,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.GLOW_RING,
                title = "GLOW_RING",
                useCase = "Active Rider Milestone",
                badgeLabel = "LIVE",
                badgeVariant = TagVariant.BRAND,
            ),
            LoopEffectSpecimen(
                effect = MotionLoopEffect.MARQUEE,
                title = "MARQUEE",
                useCase = "Horizontal Deal Sway",
                badgeLabel = "TICKER",
                badgeVariant = TagVariant.NEUTRAL,
            ),
        )

    val pressBounceSpecimens: List<PressBounceSpecimen> =
        listOf(
            PressBounceSpecimen(
                id = "tactile_express_tile",
                title = "Express Add Tile",
                caption = "0.96f spring press-down without blocking clicks",
                badgeLabel = "TAP ME",
            ),
            PressBounceSpecimen(
                id = "tactile_tip_tile",
                title = "Rider Tip Chip",
                caption = "Snappy rebound honors Reduced Motion cleanly",
                badgeLabel = "+\$2 TIP",
            ),
            PressBounceSpecimen(
                id = "tactile_coupon_tile",
                title = "Apply Promo Tile",
                caption = "Non-consuming pointer observer for nested CTAs",
                badgeLabel = "FRESH20",
            ),
        )

    fun buildFreeDeliveryCartPage(): PageResponse {
        val grid =
            GridSnippetData(
                title = SnippetsSamples.txt("Add items to cross the \$15.00 threshold", "label.large"),
                columns = 2,
                spacingDp = GRID_SPACING_DP,
                items = listOf(buildJuiceProductEnvelope(), buildGranolaProductEnvelope()),
            )
        val gridEnvelope =
            SnippetEnvelope(
                snippetId = "lab_prod_grid",
                snippetType = SnippetTypes.GRID,
                data = SnippetsSamples.encode(grid),
            )
        return PageResponse(
            pageId = "lab_cart_threshold_sim",
            snippets = listOf(gridEnvelope),
            floatingCart =
                FloatingCartSpec(
                    label = "Cart Summary",
                    etaBadge = TagAtom(label = "8 MIN EXPRESS", variant = TagVariant.SUCCESS),
                    cta =
                        ButtonAtom(
                            label = "Express Checkout",
                            size = ButtonSize.SMALL,
                            onClick =
                                ActionRef(
                                    id = "lab_cart_checkout",
                                    type = ActionTypes.TRIGGER_FEEDBACK,
                                    payload = buildJsonObject { put("effect", "celebration") },
                                ),
                        ),
                    motion = MotionSpec(enter = MotionTransition.SLIDE_UP),
                    freeDeliveryThresholdCents = FREE_DELIVERY_THRESHOLD_CENTS,
                    nudgePrefix = "Add",
                    unlockedLabel = "FREE delivery unlocked!",
                ),
        )
    }

    private fun buildJuiceProductEnvelope(): SnippetEnvelope {
        val juice =
            ProductCardSnippetData(
                productId = "lab_cold_juice",
                title = SnippetsSamples.txt("Cold-Pressed Citrus", "title.small"),
                subtitle = SnippetsSamples.txt("500ml · Zero added sugar", "body.small", "text.secondary"),
                price = SnippetsSamples.txt("\$4.50", "label.large"),
                originalPrice = SnippetsSamples.txt("\$5.50", "body.small", "text.tertiary"),
                unitPriceCents = JUICE_PRICE_CENTS,
                badge = TagAtom(label = "18% OFF", variant = TagVariant.SUCCESS),
                stepper = StepperAtom(fieldId = JUICE_FIELD_ID, quantity = 1, maxQuantity = PRODUCT_MAX_QTY),
                rating = SnippetsSamples.txt("4.9 ★", "label.small", "status.success"),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "lab_prod_juice",
            snippetType = SnippetTypes.PRODUCT_CARD,
            data = SnippetsSamples.encode(juice),
        )
    }

    private fun buildGranolaProductEnvelope(): SnippetEnvelope {
        val granola =
            ProductCardSnippetData(
                productId = "lab_artisan_granola",
                title = SnippetsSamples.txt("Honey Oat Granola", "title.small"),
                subtitle = SnippetsSamples.txt("400g · Toasted almonds", "body.small", "text.secondary"),
                price = SnippetsSamples.txt("\$6.00", "label.large"),
                originalPrice = SnippetsSamples.txt("\$7.20", "body.small", "text.tertiary"),
                unitPriceCents = GRANOLA_PRICE_CENTS,
                badge = TagAtom(label = "POPULAR", variant = TagVariant.BRAND),
                stepper = StepperAtom(fieldId = GRANOLA_FIELD_ID, quantity = 1, maxQuantity = PRODUCT_MAX_QTY),
                rating = SnippetsSamples.txt("4.8 ★", "label.small", "status.success"),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "lab_prod_granola",
            snippetType = SnippetTypes.PRODUCT_CARD,
            data = SnippetsSamples.encode(granola),
        )
    }

    fun buildMarqueeEnvelope(): SnippetEnvelope {
        val marquee =
            MarqueeStripSnippetData(
                stripId = "lab_promo_marquee",
                velocityDpPerSec = LAB_MARQUEE_VELOCITY,
                items =
                    listOf(
                        MarqueeItem(
                            id = "lab_mq_express",
                            text = "8-Min Express Dispatch Live",
                            icon = IconAtom(name = "star"),
                            badge = TagAtom(label = "FAST", variant = TagVariant.SUCCESS),
                            onClick = ActionRef(id = "lab_mq_express_tap", type = ActionTypes.SHOW_TOAST),
                        ),
                        MarqueeItem(
                            id = "lab_mq_free",
                            text = "FREE Delivery Above \$15.00",
                            badge = TagAtom(label = "NUDGE", variant = TagVariant.BRAND),
                            onClick = ActionRef(id = "lab_mq_free_tap", type = ActionTypes.TRACK_EVENT),
                        ),
                        MarqueeItem(
                            id = "lab_mq_fresh",
                            text = "Farm-to-Door Cold Chain Guaranteed",
                            icon = IconAtom(name = "check"),
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "lab_marquee_strip",
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data = SnippetsSamples.encode(marquee),
        )
    }

    fun buildDismissibleNudgeEnvelope(restoreKey: Int): SnippetEnvelope {
        val nudge =
            NudgeBarSnippetData(
                nudgeId = "lab_nudge_$restoreKey",
                title = SnippetsSamples.txt("Unlock Priority Packing for Free", "title.small"),
                subtitle =
                    SnippetsSamples.txt(
                        "Dismiss with × to test animated collapse, then tap Restore Nudge.",
                        "body.small",
                        "text.secondary",
                    ),
                icon = IconAtom(name = "notifications", sizeDp = ICON_SM_DP, color = ColorTokenRef("icon.brand")),
                badge = TagAtom(label = "LIVE NUDGE", variant = TagVariant.BRAND),
                countdown =
                    CountdownAtom(
                        countdownId = "lab_nudge_cd_$restoreKey",
                        remainingSeconds = LAB_NUDGE_COUNTDOWN_SEC,
                        label = "Left",
                    ),
                progress = LAB_NUDGE_PROGRESS,
                cta =
                    ButtonAtom(
                        label = "Apply Perk",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "lab_nudge_perk", type = ActionTypes.SHOW_TOAST),
                    ),
                dismissible = true,
                pointerEdge = "bottom",
                onDismiss = ActionRef(id = "lab_nudge_dismissed", type = ActionTypes.TRACK_EVENT),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "lab_nudge_env_$restoreKey",
            snippetType = SnippetTypes.NUDGE_BAR,
            data = SnippetsSamples.encode(nudge),
        )
    }

    fun buildOrderTrackerEnvelope(stageIndex: Int): SnippetEnvelope {
        val clamped = stageIndex.coerceIn(0, LAB_TRACKER_STAGE_COUNT - 1)
        val stageHeadlines =
            listOf(
                "Order confirmed at Abeta Fresh hub",
                "Fresh produce packed & quality checked",
                "Rider Kabir is 400m from your doorstep",
                "Delivered! Enjoy your Abeta Fresh order",
            )
        val stageEtas = listOf(STAGE_ZERO_ETA, STAGE_ONE_ETA, STAGE_TWO_ETA, 0)
        val isFinal = clamped == LAB_TRACKER_STAGE_COUNT - 1
        val etaSeconds = stageEtas[clamped]
        val tracker =
            OrderTrackerSnippetData(
                trackerId = "lab_order_tracker",
                orderCode = "#AB-9042",
                headline = SnippetsSamples.txt(stageHeadlines[clamped], "title.medium"),
                etaCountdown =
                    etaSeconds.takeIf { it > 0 }?.let { sec ->
                        CountdownAtom(countdownId = "lab_tracker_eta_$clamped", remainingSeconds = sec, label = "ETA")
                    },
                etaBadge =
                    TagAtom(
                        label = if (isFinal) "DELIVERED" else "STAGE ${clamped + 1} / 4",
                        variant = if (isFinal) TagVariant.SUCCESS else TagVariant.BRAND,
                    ),
                riderName = "Kabir Sharma",
                riderCaption = "EV Scooter · 4.97★ · Insulated Express Bag",
                steps = buildTrackerSteps(clamped, isFinal),
                primaryAction =
                    ButtonAtom(
                        label = "Contact Rider",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "lab_tracker_contact", type = ActionTypes.SHOW_TOAST),
                    ),
                secondaryAction =
                    ButtonAtom(
                        label = "View Receipt",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "lab_tracker_receipt", type = ActionTypes.OPEN_DIALOG),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "lab_order_tracker_env",
            snippetType = SnippetTypes.ORDER_TRACKER,
            data = SnippetsSamples.encode(tracker),
        )
    }

    private fun buildTrackerSteps(
        clamped: Int,
        isFinal: Boolean,
    ): List<OrderTrackerStep> {
        val stageSpecs =
            listOf(
                Triple("confirmed", "Confirmed", "14:02"),
                Triple("packed", "Packing", "14:04"),
                Triple("en_route", "On the Way", "400m"),
                Triple("delivered", "Delivered", "Done"),
            )
        return stageSpecs.mapIndexed { idx, (id, label, caption) ->
            OrderTrackerStep(
                id = id,
                label = label,
                caption = caption,
                icon = if (idx == clamped && !isFinal) IconAtom(name = "arrow_forward") else null,
                completed = isFinal || idx < clamped,
                active = !isFinal && idx == clamped,
            )
        }
    }

    fun formatUsdCents(cents: Int): String {
        val safe = cents.coerceAtLeast(0)
        val dollars = safe / CENTS_PER_DOLLAR
        val remainder = safe % CENTS_PER_DOLLAR
        return "\$$dollars.${remainder.toString().padStart(CENTS_PAD_DIGITS, '0')}"
    }
}
