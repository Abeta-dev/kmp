@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.watch

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.platform.HapticStyle

/**
 * Glanceable key-value KPI or ETA indicator tailored for Apple Watch complications / Wear OS tiles.
 */
data class WatchMetricGlance(
    val id: String,
    val label: String,
    val value: String,
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
    val onClick: ActionRef? = null,
)

/**
 * Compact actionable card suitable for small wearable screens and Crown / rotary scrolling.
 */
data class WatchActionCard(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val badgeLabel: String? = null,
    val primaryActionLabel: String? = null,
    val primaryAction: ActionRef? = null,
    val secondaryActionLabel: String? = null,
    val secondaryAction: ActionRef? = null,
)

/**
 * Wearable-normalized SDUI snapshot produced by [WatchScreenMapper] from a full [dev.abeta.kmp.contracts.PageResponse].
 */
data class WatchGlancePage(
    val pageId: String,
    val title: String,
    val isOffline: Boolean = false,
    val metrics: List<WatchMetricGlance> = emptyList(),
    val cards: List<WatchActionCard> = emptyList(),
    val lastFeedbackMessage: String? = null,
)

/**
 * Reactive UI state consumed by Apple Watch (`AbetaWatchKit`) and Wear OS companion hosts.
 */
sealed interface WatchUiState {
    /** Initial state before a watch screen load begins. */
    data object Idle : WatchUiState

    /** Loading snapshot from Go BFF or local preview repository. */
    data object Loading : WatchUiState

    /** Active wearable screen ready for glance and tap actions. */
    data class Ready(
        val page: WatchGlancePage,
        val activeToast: String? = null,
        val lastHaptic: HapticStyle? = null,
    ) : WatchUiState

    /** Error state surfaced with the failing [AppError] and requested [screenId]. */
    data class Error(
        val error: AppError,
        val screenId: String? = null,
    ) : WatchUiState
}
