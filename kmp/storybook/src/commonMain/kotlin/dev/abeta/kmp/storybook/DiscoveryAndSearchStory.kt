package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiCelebrationOverlay
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_SWIPE_LOGS = 6

/**
 * Interactive Storybook playground for `search_bar`, `chip_group`, and `swipe_deck` rendered
 * through [BuiltInSnippetModule], with live query feedback, filter state, swipe logs, and match overlay.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun DiscoveryAndSearchStory(onAction: (ActionRef) -> Unit) {
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    val searchEnvelope = remember { DiscoveryStoryFixtures.buildSearchEnvelope() }
    val categoryEnvelope = remember { DiscoveryStoryFixtures.buildCategoryChipsEnvelope() }
    val filterEnvelope = remember { DiscoveryStoryFixtures.buildFilterChipsEnvelope() }
    val swipeEnvelope = remember { DiscoveryStoryFixtures.buildSwipeDeckEnvelope() }

    val formValues = remember { mutableStateMapOf<String, String>() }
    var submittedQuery by remember { mutableStateOf("") }
    val swipeLogs = remember { mutableStateListOf<String>() }
    var matchedProfile by remember { mutableStateOf<String?>(null) }

    val handleSearchAction: (ActionRef) -> Unit = { action ->
        onAction(action)
        when (action.id) {
            "discovery_search_submit" -> submittedQuery = action.payloadString("query")
            "discovery_search_clear" -> submittedQuery = ""
        }
    }

    val handleSwipeAction: (ActionRef) -> Unit = { action ->
        onAction(action)
        swipeLogs.add(0, formatSwipeLogEntry(action))
        if (swipeLogs.size > MAX_SWIPE_LOGS) swipeLogs.removeAt(swipeLogs.lastIndex)
        val decision = action.payloadString("decision")
        if (decision == "like" || decision == "super_like") {
            matchedProfile = action.payloadString("profile_name").ifEmpty { "New Connection" }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space16)) {
        DiscoverySearchSection(
            registry = registry,
            envelope = searchEnvelope,
            formValues = formValues,
            submittedQuery = submittedQuery,
            onFormValueChange = { k, v -> formValues[k] = v },
            onAction = handleSearchAction,
        )
        DiscoveryChipsSection(
            registry = registry,
            categoryEnvelope = categoryEnvelope,
            filterEnvelope = filterEnvelope,
            formValues = formValues,
            onFormValueChange = { k, v -> formValues[k] = v },
            onAction = onAction,
        )
        DiscoverySwipeSection(
            registry = registry,
            swipeEnvelope = swipeEnvelope,
            formValues = formValues,
            swipeLogs = swipeLogs,
            matchedProfile = matchedProfile,
            onFormValueChange = { k, v -> formValues[k] = v },
            onAction = handleSwipeAction,
            onClearLogs = { swipeLogs.clear() },
            onDismissMatch = { matchedProfile = null },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun DiscoverySearchSection(
    registry: SnippetRegistry,
    envelope: SnippetEnvelope,
    formValues: Map<String, String>,
    submittedQuery: String,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val liveQuery = formValues[DiscoveryStoryFixtures.SEARCH_ID].orEmpty()
    val (badgeLabel, badgeVariant) =
        when {
            submittedQuery.isNotBlank() && liveQuery == submittedQuery -> {
                "Submitted: \"$submittedQuery\"" to TagVariant.SUCCESS
            }

            liveQuery.isNotBlank() -> {
                "Live Query: \"$liveQuery\"" to TagVariant.INFO
            }

            else -> {
                "Query: (empty)" to TagVariant.NEUTRAL
            }
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "Search Bar & Live Query Binding", typography = TypographyTokenRef("title.medium")))
            UiTag(TagAtom(label = badgeLabel, variant = badgeVariant))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = ThemeTokens.spacing.space8)) {
            registry.Render(envelope, formValues, onFormValueChange, onAction)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun DiscoveryChipsSection(
    registry: SnippetRegistry,
    categoryEnvelope: SnippetEnvelope,
    filterEnvelope: SnippetEnvelope,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val activeCategory = formValues["chip_${DiscoveryStoryFixtures.CATEGORY_GROUP_ID}"]?.ifBlank { "spark" } ?: "spark"
    val activeFilters = formValues["chip_${DiscoveryStoryFixtures.FILTER_GROUP_ID}"] ?: "nearby,verified"

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Category & Filter Chip Groups",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiTag(TagAtom(label = "Single + Multi-Select", variant = TagVariant.BRAND))
            }
            registry.Render(categoryEnvelope, formValues, onFormValueChange, onAction)
            registry.Render(filterEnvelope, formValues, onFormValueChange, onAction)
            Text(
                text = "Active vertical: $activeCategory · Filters: ${activeFilters.ifBlank { "none" }}",
                style = ThemeTokens.typography.caption,
                color = ThemeTokens.colors.text.secondary,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun DiscoverySwipeSection(
    registry: SnippetRegistry,
    swipeEnvelope: SnippetEnvelope,
    formValues: Map<String, String>,
    swipeLogs: List<String>,
    matchedProfile: String?,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    onClearLogs: () -> Unit,
    onDismissMatch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space12)) {
            UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UiText(
                            TextAtom(
                                text = "Abeta Spark · Swipe Discovery Deck",
                                typography = TypographyTokenRef("title.medium"),
                            ),
                        )
                        UiTag(TagAtom(label = "3 Profile Cards", variant = TagVariant.BRAND))
                    }
                    registry.Render(swipeEnvelope, formValues, onFormValueChange, onAction)
                }
            }
            SwipeActionLogCard(swipeLogs = swipeLogs, onClearLogs = onClearLogs)
        }
        UiCelebrationOverlay(
            visible = matchedProfile != null,
            title = "It's a Match!",
            subtitle = matchedProfile?.let { "You and $it connected on Abeta Spark!" },
            onDismiss = onDismissMatch,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SwipeActionLogCard(
    swipeLogs: List<String>,
    onClearLogs: () -> Unit,
) {
    val colors = ThemeTokens.colors
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "Live Swipe Action Log (${swipeLogs.size})",
                    typography = TypographyTokenRef("label.large"),
                ),
            )
            if (swipeLogs.isNotEmpty()) TextButton(onClick = onClearLogs) { Text("Clear") }
        }
        if (swipeLogs.isEmpty()) {
            Text(
                text = "Drag a card or tap Like, Super Like, Nope, or Undo to inspect live swipe events.",
                style = ThemeTokens.typography.bodySmall,
                color = colors.text.secondary,
            )
        } else {
            swipeLogs.forEach { entry ->
                Text(
                    text = entry,
                    style = ThemeTokens.typography.caption.copy(fontFamily = ThemeTokens.typography.fonts.mono),
                    color = colors.text.primary,
                )
            }
        }
    }
}

private fun formatSwipeLogEntry(action: ActionRef): String {
    val decision = action.payloadString("decision")
    val profile = action.payloadString("profile_name")
    return when (decision) {
        "like" -> "LIKE · $profile"
        "super_like" -> "SUPER LIKE · $profile"
        "nope" -> "NOPE · $profile"
        "undo" -> "UNDO · Restored previous profile"
        else -> "${action.type} · ${action.id}"
    }
}

private fun ActionRef.payloadString(key: String): String = (payload[key] as? JsonPrimitive)?.content.orEmpty()
