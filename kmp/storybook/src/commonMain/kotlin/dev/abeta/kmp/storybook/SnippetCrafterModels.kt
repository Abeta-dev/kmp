package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.TagVariant

private const val FLASH_COUNTDOWN_SEC = 185
private const val LOYALTY_COUNTDOWN_SEC = 720
private const val RIDER_COUNTDOWN_SEC = 340
private const val B2B_COUNTDOWN_SEC = 540
private const val FLASH_PROGRESS = 0.78f
private const val LOYALTY_PROGRESS = 0.75f
private const val RIDER_PROGRESS = 0.66f
private const val B2B_PROGRESS = 0.85f

internal enum class CrafterPreset(
    val label: String,
    val recipeName: String,
    val title: String,
    val subtitle: String,
    val badgeLabel: String,
    val badgeVariant: TagVariant,
    val countdownLabel: String,
    val countdownSeconds: Int,
    val progressLabel: String,
    val progressFraction: Float,
    val ctaLabel: String,
) {
    FLASH_COMBO(
        label = "Flash Combo Deal",
        recipeName = "flash_combo_deal_v1",
        title = "Avocado Toast + Cold Brew Combo",
        subtitle = "Save 28% · Dispatched in 8 minutes from Abeta Fresh",
        badgeLabel = "FLASH COMBO",
        badgeVariant = TagVariant.BRAND,
        countdownLabel = "Ends",
        countdownSeconds = FLASH_COUNTDOWN_SEC,
        progressLabel = "78% of flash batch claimed",
        progressFraction = FLASH_PROGRESS,
        ctaLabel = "Add Combo",
    ),
    LOYALTY_STAMP(
        label = "Loyalty Stamp Card",
        recipeName = "loyalty_stamp_card_v1",
        title = "Abeta Rewards · 6 of 8 Stamps",
        subtitle = "2 more express orders unlock a free weekend feast",
        badgeLabel = "GOLD TIER",
        badgeVariant = TagVariant.SUCCESS,
        countdownLabel = "Reset",
        countdownSeconds = LOYALTY_COUNTDOWN_SEC,
        progressLabel = "75% toward free delivery pass",
        progressFraction = LOYALTY_PROGRESS,
        ctaLabel = "Claim Reward",
    ),
    LIVE_RIDER_ETA(
        label = "Live Rider ETA Tile",
        recipeName = "live_rider_eta_tile_v1",
        title = "Rider Kabir · 550m Away",
        subtitle = "EV Scooter · Insulated express bag · Order #AB-7410",
        badgeLabel = "ON THE WAY",
        badgeVariant = TagVariant.INFO,
        countdownLabel = "ETA",
        countdownSeconds = RIDER_COUNTDOWN_SEC,
        progressLabel = "66% route completed",
        progressFraction = RIDER_PROGRESS,
        ctaLabel = "Call Rider",
    ),
    B2B_BULK_QUOTE(
        label = "B2B Bulk Quote Tile",
        recipeName = "b2b_bulk_quote_tile_v1",
        title = "GST-Verified Carton Wholesale Tier",
        subtitle = "MOQ 10 cartons · Locked factory rate on Abeta Trade",
        badgeLabel = "GST VERIFIED",
        badgeVariant = TagVariant.WARNING,
        countdownLabel = "Rate Lock",
        countdownSeconds = B2B_COUNTDOWN_SEC,
        progressLabel = "85% to extra 6% volume rebate",
        progressFraction = B2B_PROGRESS,
        ctaLabel = "Request Quote",
    ),
}

internal enum class CrafterRootContainer(
    val label: String,
    val kind: ComposedNodeKind,
) {
    COLUMN("Column", ComposedNodeKind.COLUMN),
    ROW("Row", ComposedNodeKind.ROW),
}

internal data class CrafterConfig(
    val preset: CrafterPreset = CrafterPreset.FLASH_COMBO,
    val rootContainer: CrafterRootContainer = CrafterRootContainer.COLUMN,
    val enterTransition: MotionTransition = MotionTransition.SPRING_BOUNCE,
    val loopEffect: MotionLoopEffect = MotionLoopEffect.NONE,
    val includeBadgeTag: Boolean = true,
    val includeCountdown: Boolean = true,
    val includeRollingCounter: Boolean = true,
    val includeProgressBar: Boolean = true,
    val includeInlineStepper: Boolean = true,
    val includeCtaButton: Boolean = true,
)
