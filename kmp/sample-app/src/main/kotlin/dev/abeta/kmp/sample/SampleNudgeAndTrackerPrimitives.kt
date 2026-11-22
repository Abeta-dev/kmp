package dev.abeta.kmp.sample

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom

internal const val FREE_DELIVERY_THRESHOLD_CENTS = 1500
internal const val FREE_DELIVERY_NUDGE_PREFIX = "Add"
internal const val FREE_DELIVERY_UNLOCKED_LABEL = "FREE 8-min delivery unlocked!"

private const val FLASH_DROP_SECONDS = 420
private const val BITES_ETA_SECONDS = 660
private const val BITES_URGENT_SECONDS = 120
private const val PULSE_EARLY_BIRD_SECONDS = 540
private const val URGENT_SECONDS = 60
private const val MARQUEE_VELOCITY_DP = 48
private const val ICON_SM = 16
private const val ICON_MD = 20

/**
 * Reusable Wave 6 nudge bars, marquee tickers, floating cart docks, and live order trackers
 * for offline `sample-app` store and vertical showcase screens.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SampleNudgeAndTrackerPrimitives {
    private val ui = SampleScreens
    private val marqueeLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 12, vertical = 8),
            margin = SpacingValues(horizontal = 16, vertical = 4),
            bgColor = ColorTokenRef("surface.secondary"),
            cornerRadius = CornerRadiusSpec(all = 12),
            fillMaxWidth = true,
        )

    fun expressPerksMarquee(
        snippetId: String,
        stripId: String,
    ): SnippetEnvelope =
        marqueeEnvelope(
            snippetId = snippetId,
            stripId = stripId,
            items =
                listOf(
                    MarqueeItem(
                        id = "${stripId}_dispatch",
                        text = "8-min dispatch",
                        icon = IconAtom(name = "star", sizeDp = ICON_SM, color = ColorTokenRef("brand.primary")),
                        badge = TagAtom(label = "EXPRESS", bgColor = ColorTokenRef("status.success_container")),
                        onClick = ui.toastAction("tap_${stripId}_dispatch", "Chilled express dispatch in 8 mins"),
                    ),
                    MarqueeItem(
                        id = "${stripId}_free_delivery",
                        text = "Free delivery over \$15",
                        icon = IconAtom(name = "check", sizeDp = ICON_SM, color = ColorTokenRef("status.success")),
                        badge = TagAtom(label = "FREE", bgColor = ColorTokenRef("brand.container")),
                        onClick = ui.toastAction("tap_${stripId}_free", "Baskets over \$15 ship free!", "success"),
                    ),
                    MarqueeItem(
                        id = "${stripId}_zero_surge",
                        text = "Zero surge pricing",
                        icon = IconAtom(name = "info", sizeDp = ICON_SM, color = ColorTokenRef("status.info")),
                        badge = TagAtom(label = "24/7", bgColor = ColorTokenRef("status.info_container")),
                    ),
                ),
        )

    fun flashDropNudgeBar(
        snippetId: String,
        nudgeId: String,
    ): SnippetEnvelope {
        val claimToast = ui.toastAction("toast_${nudgeId}_claim", "Flash Drop 20% applied!", "success")
        val subtitle = ui.text("Express chilled slot reserved for your basket", "body.small", "text.secondary")
        val icon = IconAtom(name = "notifications", sizeDp = ICON_MD, color = ColorTokenRef("brand.primary"))
        val badge =
            TagAtom(
                label = "FLASH DROP",
                bgColor = ColorTokenRef("status.warning_container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.WIGGLE),
            )
        val countdown =
            CountdownAtom(
                countdownId = "${nudgeId}_cd",
                remainingSeconds = FLASH_DROP_SECONDS,
                label = "Flash Drop ends in",
                urgentThresholdSeconds = URGENT_SECONDS,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val cta = ui.button("btn_${nudgeId}_claim", "Claim Drop", claimToast, ButtonVariant.PRIMARY, ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.NUDGE_BAR,
            data =
                ui.encode(
                    NudgeBarSnippetData(
                        nudgeId = nudgeId,
                        title = ui.text("Flash Drop · Extra 20% off fresh produce", "title.small"),
                        subtitle = subtitle,
                        icon = icon,
                        badge = badge,
                        countdown = countdown,
                        progress = 0.68f,
                        cta = cta,
                        dismissible = true,
                        pointerEdge = "bottom",
                        onDismiss = ui.toastAction("dismiss_$nudgeId", "Flash Drop reminder dismissed"),
                        layout = ui.cardLayout,
                    ),
                ),
            motion = MotionSpec(enter = MotionTransition.SLIDE_DOWN, durationToken = MotionDurationToken.FAST),
        )
    }

    fun expressFloatingCart(
        label: String,
        ctaLabel: String,
        action: ActionRef,
    ): FloatingCartSpec {
        val bounceMotion = MotionSpec(enter = MotionTransition.SPRING_BOUNCE, durationToken = MotionDurationToken.FAST)
        return FloatingCartSpec(
            label = label,
            etaBadge =
                TagAtom(
                    label = "8 MINS",
                    bgColor = ColorTokenRef("status.success_container"),
                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                ),
            cta = ui.button("btn_cart_${action.id}", ctaLabel, action).copy(motion = bounceMotion),
            motion =
                MotionSpec(
                    enter = MotionTransition.SLIDE_UP,
                    exit = MotionTransition.SLIDE_DOWN,
                    durationToken = MotionDurationToken.NORMAL,
                ),
            freeDeliveryThresholdCents = FREE_DELIVERY_THRESHOLD_CENTS,
            nudgePrefix = FREE_DELIVERY_NUDGE_PREFIX,
            unlockedLabel = FREE_DELIVERY_UNLOCKED_LABEL,
        )
    }

    fun bitesOrderTrackerEnvelope(): SnippetEnvelope {
        val callToast = ui.toastAction("toast_bites_call", "Connecting masked call to Karan M.")
        val helpToast = ui.toastAction("toast_bites_help", "Abeta Bites support opened for #BTS-4091")
        val primary =
            ui.button(
                id = "btn_bites_call_rider",
                label = "Call Rider",
                onClick = callToast,
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.SMALL,
            )
        val secondary = ui.button("btn_bites_help", "Order Help", helpToast, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val etaCountdown =
            CountdownAtom(
                countdownId = "bites_order_eta_cd",
                remainingSeconds = BITES_ETA_SECONDS,
                label = "Arriving in",
                urgentThresholdSeconds = BITES_URGENT_SECONDS,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val etaBadge =
            TagAtom(
                label = "11 MINS",
                bgColor = ColorTokenRef("status.success_container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        return SnippetEnvelope(
            snippetId = "bites_live_order_tracker",
            snippetType = SnippetTypes.ORDER_TRACKER,
            data =
                ui.encode(
                    OrderTrackerSnippetData(
                        trackerId = "bites_order_4091",
                        orderCode = "#BTS-4091",
                        headline = ui.text("Karan M. is on the way from Saffron Hearth", "title.medium"),
                        etaCountdown = etaCountdown,
                        etaBadge = etaBadge,
                        riderName = "Karan M.",
                        riderCaption = "4.9 ★ · 1.2 km away on e-bike · Thermal insulated bag",
                        steps = bitesOrderSteps(),
                        primaryAction = primary,
                        secondaryAction = secondary,
                        layout = ui.cardLayout,
                    ),
                ),
            motion = MotionSpec(enter = MotionTransition.SLIDE_UP, durationToken = MotionDurationToken.NORMAL),
        )
    }

    fun martPerksMarquee(): SnippetEnvelope =
        marqueeEnvelope(
            snippetId = "mart_perks_marquee",
            stripId = "mart_live_perks",
            items =
                listOf(
                    MarqueeItem(
                        id = "mart_mq_dispatch",
                        text = "Same-day metro dispatch",
                        icon = IconAtom(name = "star", sizeDp = ICON_SM, color = ColorTokenRef("brand.primary")),
                        badge = TagAtom(label = "FAST", bgColor = ColorTokenRef("brand.container")),
                    ),
                    MarqueeItem(
                        id = "mart_mq_returns",
                        text = "30-day instant returns",
                        icon = IconAtom(name = "check", sizeDp = ICON_SM, color = ColorTokenRef("status.success")),
                        badge = TagAtom(label = "PROTECTED", bgColor = ColorTokenRef("status.success_container")),
                    ),
                    MarqueeItem(
                        id = "mart_mq_cashback",
                        text = "10% instant bank cashback",
                        icon = IconAtom(name = "info", sizeDp = ICON_SM, color = ColorTokenRef("status.warning")),
                        badge = TagAtom(label = "DEAL", bgColor = ColorTokenRef("status.warning_container")),
                        onClick = ui.toastAction("toast_mart_cashback", "10% bank cashback applies!", "success"),
                    ),
                ),
        )

    fun pulseUrgencyNudgeBar(detailRoute: String): SnippetEnvelope {
        val subtitle = ui.text("Neon Horizon Live Symphony · Instant QR pass", "body.small", "text.secondary")
        val icon = IconAtom(name = "notifications", sizeDp = ICON_MD, color = ColorTokenRef("brand.primary"))
        val badge =
            TagAtom(
                label = "SELLING FAST",
                bgColor = ColorTokenRef("status.warning_container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.WIGGLE),
            )
        val countdown =
            CountdownAtom(
                countdownId = "pulse_early_bird_cd",
                remainingSeconds = PULSE_EARLY_BIRD_SECONDS,
                label = "Tier closes",
                urgentThresholdSeconds = URGENT_SECONDS,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val nav = ui.navAction("nav_pulse_nudge_detail", detailRoute)
        val cta = ui.button("btn_pulse_nudge_book", "Reserve Now", nav, ButtonVariant.PRIMARY, ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = "pulse_early_bird_nudge",
            snippetType = SnippetTypes.NUDGE_BAR,
            data =
                ui.encode(
                    NudgeBarSnippetData(
                        nudgeId = "pulse_early_bird",
                        title = ui.text("Early-Bird Arena Gold · 18 seats left", "title.small"),
                        subtitle = subtitle,
                        icon = icon,
                        badge = badge,
                        countdown = countdown,
                        progress = 0.82f,
                        cta = cta,
                        dismissible = true,
                        layout = ui.cardLayout,
                    ),
                ),
            motion = MotionSpec(enter = MotionTransition.SLIDE_DOWN, durationToken = MotionDurationToken.FAST),
        )
    }

    fun sparkBoostNudgeBar(): SnippetEnvelope {
        val subtitle = ui.text("12 active matches nearby — stay at top of deck", "body.small", "text.secondary")
        val boostToast = ui.toastAction("toast_spark_boost", "3× Prime Boost active for 30 mins!", "success")
        val badge =
            TagAtom(
                label = "3× BOOST",
                bgColor = ColorTokenRef("brand.container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.HEARTBEAT),
            )
        val cta = ui.button("btn_spark_boost", "Boost Deck", boostToast, ButtonVariant.PRIMARY, ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = "spark_boost_nudge",
            snippetType = SnippetTypes.NUDGE_BAR,
            data =
                ui.encode(
                    NudgeBarSnippetData(
                        nudgeId = "spark_prime_boost",
                        title = ui.text("Prime Evening Boost · 3× Visibility", "title.small"),
                        subtitle = subtitle,
                        icon = IconAtom(name = "star", sizeDp = ICON_MD, color = ColorTokenRef("brand.primary")),
                        badge = badge,
                        cta = cta,
                        dismissible = true,
                        layout = ui.cardLayout,
                    ),
                ),
            motion = MotionSpec(enter = MotionTransition.FADE_IN, durationToken = MotionDurationToken.FAST),
        )
    }

    private fun bitesOrderSteps(): List<OrderTrackerStep> =
        listOf(
            OrderTrackerStep(
                id = "step_confirmed",
                label = "Order Confirmed",
                caption = "19:14",
                icon = IconAtom(name = "check", sizeDp = ICON_SM, color = ColorTokenRef("status.success")),
                completed = true,
            ),
            OrderTrackerStep(
                id = "step_preparing",
                label = "Kitchen Preparing",
                caption = "19:21",
                icon = IconAtom(name = "star", sizeDp = ICON_SM, color = ColorTokenRef("status.success")),
                completed = true,
            ),
            OrderTrackerStep(
                id = "step_en_route",
                label = "Rider En Route",
                caption = "1.2 km",
                icon = IconAtom(name = "notifications", sizeDp = ICON_SM, color = ColorTokenRef("icon.on_brand")),
                active = true,
            ),
            OrderTrackerStep(
                id = "step_delivered",
                label = "Delivered",
                caption = "ETA 11m",
                icon = IconAtom(name = "home", sizeDp = ICON_SM, color = ColorTokenRef("text.tertiary")),
            ),
        )

    private fun marqueeEnvelope(
        snippetId: String,
        stripId: String,
        items: List<MarqueeItem>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data =
                ui.encode(
                    MarqueeStripSnippetData(
                        stripId = stripId,
                        items = items,
                        velocityDpPerSec = MARQUEE_VELOCITY_DP,
                        separator = "•",
                        layout = marqueeLayout,
                    ),
                ),
            motion =
                MotionSpec(
                    enter = MotionTransition.FADE_IN,
                    durationToken = MotionDurationToken.FAST,
                    loopEffect = MotionLoopEffect.MARQUEE,
                ),
        )
}
