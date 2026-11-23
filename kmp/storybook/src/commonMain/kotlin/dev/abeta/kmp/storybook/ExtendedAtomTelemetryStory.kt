package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiAlertBanner
import dev.abeta.kmp.atoms.atoms.UiBadge
import dev.abeta.kmp.atoms.atoms.UiCountdown
import dev.abeta.kmp.atoms.atoms.UiRollingCounter
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiStatTile
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlertBannerAtom
import dev.abeta.kmp.contracts.BadgeAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val SPACER_SM_DP = 8
private const val SPACER_MD_DP = 12
private const val BADGE_SMALL_COUNT = 3
private const val BADGE_OVERFLOW_COUNT = 142
private const val BADGE_MAX_COUNT = 99
private const val COUNTDOWN_URGENT_SEC = 12
private const val COUNTDOWN_NORMAL_SEC = 95
private const val COUNTDOWN_URGENT_THRESHOLD_SEC = 30
private const val COUNTER_INITIAL_CENTS = 1240
private const val COUNTER_SMALL_STEP_CENTS = 150
private const val COUNTER_LARGE_STEP_CENTS = 1240

/**
 * Interactive story showcasing [UiBadge], [UiAlertBanner], [UiStatTile], [UiCountdown],
 * and [UiRollingCounter] with live `SduiJson` wire inspection.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ExtendedAtomTelemetryStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var countdownSec by remember { mutableIntStateOf(COUNTDOWN_NORMAL_SEC) }
    var countdownRun by remember { mutableIntStateOf(0) }
    val countdownAtom =
        remember(countdownSec, countdownRun) {
            CountdownAtom(
                countdownId = "story_telemetry_cd_$countdownRun",
                remainingSeconds = countdownSec,
                label = "Flash drop ends",
                urgentThresholdSeconds = COUNTDOWN_URGENT_THRESHOLD_SEC,
                onExpire =
                    ActionRef(
                        id = "story_cd_expire",
                        type = ActionTypes.SHOW_TOAST,
                        payload = buildJsonObject { put("message", "Flash drop countdown expired!") },
                    ),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        BadgeSpecimenCard()
        AlertBannerSpecimenCard(onAction = onAction)
        StatTileSpecimenCard(onAction = onAction)
        CountdownSpecimenCard(
            countdownAtom = countdownAtom,
            onSelectSeconds = { seconds ->
                countdownSec = seconds
                countdownRun += 1
            },
            onAction = onAction,
        )
        RollingCounterSpecimenCard()
        JsonContractPreviewCard(
            title = "CountdownAtom Wire JSON",
            jsonText = SduiJson.encodeToString(CountdownAtom.serializer(), countdownAtom),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun BadgeSpecimenCard() {
    val spacing = ThemeTokens.spacing
    var liveCount by remember { mutableIntStateOf(BADGE_SMALL_COUNT) }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "UiBadge · Counts, Overflow & Live Dot",
                    typography = TypographyTokenRef("title.large"),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                OutlinedButton(onClick = { liveCount += 1 }) { Text("+1") }
                OutlinedButton(onClick = { liveCount = BADGE_SMALL_COUNT }) { Text("Reset") }
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space24),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            BadgeSpecimenItem(
                atom = BadgeAtom(id = "story_badge_count", count = liveCount, variant = TagVariant.BRAND),
                caption = "Count ($liveCount)",
            )
            BadgeSpecimenItem(
                atom =
                    BadgeAtom(
                        id = "story_badge_overflow",
                        count = BADGE_OVERFLOW_COUNT,
                        maxCount = BADGE_MAX_COUNT,
                        variant = TagVariant.ERROR,
                    ),
                caption = "Overflow ($BADGE_OVERFLOW_COUNT -> 99+)",
            )
            BadgeSpecimenItem(
                atom =
                    BadgeAtom(
                        id = "story_badge_pulse_dot",
                        dotOnly = true,
                        label = "Live hub pulse",
                        variant = TagVariant.SUCCESS,
                        motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                    ),
                caption = "Live Dot + Pulse",
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun BadgeSpecimenItem(
    atom: BadgeAtom,
    caption: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space8),
    ) {
        UiBadge(atom = atom)
        Text(text = caption, style = ThemeTokens.typography.bodySmall, color = ThemeTokens.colors.text.secondary)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AlertBannerSpecimenCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var dismissedIds by remember { mutableStateOf(emptySet<String>()) }
    val banners = remember { buildDemoAlertBanners() }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "UiAlertBanner · 4 Semantic Variants",
                    typography = TypographyTokenRef("title.large"),
                ),
            )
            if (dismissedIds.isNotEmpty()) {
                OutlinedButton(onClick = { dismissedIds = emptySet() }) {
                    Text("Restore Banners")
                }
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            banners.filterNot { it.id in dismissedIds }.forEach { banner ->
                UiAlertBanner(
                    atom = banner,
                    onDismiss = { banner.id?.let { dismissedIds = dismissedIds + it } },
                    onAction = onAction,
                )
            }
        }
    }
}

private fun buildDemoAlertBanners(): List<AlertBannerAtom> =
    listOf(
        AlertBannerAtom(
            id = "banner_info",
            title = "Info · Express Slot Reserved",
            message = "Your 8-min cold-chain slot is held for the next 5 minutes.",
            variant = TagVariant.INFO,
            actionLabel = "View Details",
            onAction = ActionRef(id = "banner_info_cta", type = ActionTypes.SHOW_TOAST),
            dismissible = true,
        ),
        AlertBannerAtom(
            id = "banner_success",
            title = "Success · Instant Settlement Verified",
            message = "Receipt #PAY-3081 cleared with \$0.00 fee and 5% cashback.",
            variant = TagVariant.SUCCESS,
            actionLabel = "Open Receipt",
            onAction = ActionRef(id = "banner_success_cta", type = ActionTypes.NAVIGATE),
            dismissible = true,
        ),
        AlertBannerAtom(
            id = "banner_warning",
            title = "Warning · High Demand Hub",
            message = "Metro Fulfillment Hub is running 4 mins behind standard ETA.",
            variant = TagVariant.WARNING,
            actionLabel = "Switch Hub",
            onAction = ActionRef(id = "banner_warning_cta", type = ActionTypes.SHOW_TOAST),
            dismissible = true,
        ),
        AlertBannerAtom(
            id = "banner_error",
            title = "Error · Card Authorization Declined",
            message = "Please re-enter your 4-digit PIN or choose another funding source.",
            variant = TagVariant.ERROR,
            actionLabel = "Retry Payment",
            onAction = ActionRef(id = "banner_error_cta", type = ActionTypes.SHOW_TOAST),
            dismissible = true,
        ),
    )

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StatTileSpecimenCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val positiveTile =
        remember {
            StatTileAtom(
                id = "story_stat_gmv",
                label = "Monthly GMV",
                value = "\$48,920",
                deltaLabel = "+18.4%",
                deltaPositive = true,
                caption = "vs previous 30-day period",
                icon = IconAtom(name = "star", color = ColorTokenRef("status.success")),
                onClick = ActionRef(id = "stat_gmv_tap", type = ActionTypes.TRACK_EVENT),
            )
        }
    val negativeTile =
        remember {
            StatTileAtom(
                id = "story_stat_dropoff",
                label = "Cart Drop-Off",
                value = "4.2%",
                deltaLabel = "-2.1%",
                deltaPositive = false,
                caption = "Faster checkout conversion",
                icon = IconAtom(name = "warning", color = ColorTokenRef("status.error")),
                onClick = ActionRef(id = "stat_dropoff_tap", type = ActionTypes.TRACK_EVENT),
            )
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiStatTile · Positive & Negative KPIs", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            UiStatTile(atom = positiveTile, modifier = Modifier.weight(1f), onAction = onAction)
            UiStatTile(atom = negativeTile, modifier = Modifier.weight(1f), onAction = onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CountdownSpecimenCard(
    countdownAtom: CountdownAtom,
    onSelectSeconds: (Int) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiCountdown · Live Urgency Timer", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            OutlinedButton(onClick = { onSelectSeconds(COUNTDOWN_URGENT_SEC) }) {
                Text("12s Urgent")
            }
            OutlinedButton(onClick = { onSelectSeconds(COUNTDOWN_NORMAL_SEC) }) {
                Text("95s Normal")
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiCountdown(atom = countdownAtom, onAction = onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RollingCounterSpecimenCard() {
    val spacing = ThemeTokens.spacing
    var cents by remember { mutableIntStateOf(COUNTER_INITIAL_CENTS) }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiRollingCounter · Tabular Odometer", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiRollingCounter(
            value = SuperAppDemoBuilder.formatUsd(cents),
            style = ThemeTokens.typography.headlineMedium,
            color = ThemeTokens.colors.brand.primary,
        )
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            OutlinedButton(onClick = { cents += COUNTER_SMALL_STEP_CENTS }) {
                Text("+\$1.50")
            }
            OutlinedButton(onClick = { cents += COUNTER_LARGE_STEP_CENTS }) {
                Text("+\$12.40")
            }
            OutlinedButton(onClick = { cents = COUNTER_INITIAL_CENTS }) {
                Text("Reset")
            }
        }
    }
}
