@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Responsive grid column counts keyed by horizontal window width class.
 */
@Serializable
data class ResponsiveColumnsSpec(
    val compact: Int? = null,
    val medium: Int? = null,
    val expanded: Int? = null,
)

/**
 * Responsive integer sizing values keyed by horizontal window width class (e.g. item width in dp).
 */
@Serializable
data class ResponsiveIntSpec(
    val compact: Int? = null,
    val medium: Int? = null,
    val expanded: Int? = null,
)

/**
 * Server-driven responsive viewport configuration attached to [PageResponse].
 */
@Serializable
data class AdaptivePageSpec(
    @SerialName("layout_mode")
    val layoutMode: ScreenLayoutMode = ScreenLayoutMode.CENTERED,
    @SerialName("max_width_dp")
    val maxWidthDp: Int? = null,
    @SerialName("split_pane_enabled")
    val splitPaneEnabled: Boolean = false,
    @SerialName("secondary_snippet_ids")
    val secondarySnippetIds: List<String> = emptyList(),
)
