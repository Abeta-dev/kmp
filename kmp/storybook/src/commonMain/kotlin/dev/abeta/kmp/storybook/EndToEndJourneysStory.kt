package dev.abeta.kmp.storybook

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiAvatar
import dev.abeta.kmp.atoms.atoms.UiCelebrationOverlay
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.JsonPrimitive

private const val FRESH_VIEWPORT_HEIGHT_DP = 560
private const val MATCH_AVATAR_SIZE_DP = 36

private enum class JourneyTab(
    val label: String,
    val badge: String,
) {
    FRESH_EXPRESS("Abeta Fresh Express", "Browse -> Cart -> Tracker"),
    SPARK_MATCH("Abeta Spark Match", "Filter -> Swipe -> Inbox"),
    TRADE_B2B_RFQ("Abeta Trade B2B RFQ", "GST -> MOQ -> Validated RFQ"),
}

/**
 * Story 16: Tabbed End-to-End Super-App Journey Player rendering multi-step flows for
 * Abeta Fresh, Abeta Spark, and Abeta Trade via real [SnippetRegistry] and [UiCelebrationOverlay].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun EndToEndJourneysStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    var activeTab by remember { mutableStateOf(JourneyTab.FRESH_EXPRESS) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        JourneySelectorCard(
            activeTab = activeTab,
            onSelectTab = { activeTab = it },
        )
        when (activeTab) {
            JourneyTab.FRESH_EXPRESS -> FreshExpressJourneyFlow(registry = registry, onAction = onAction)
            JourneyTab.SPARK_MATCH -> SparkMatchJourneyFlow(registry = registry, onAction = onAction)
            JourneyTab.TRADE_B2B_RFQ -> TradeRfqJourneyFlow(registry = registry, onAction = onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun JourneySelectorCard(
    activeTab: JourneyTab,
    onSelectTab: (JourneyTab) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Super-App End-to-End Journeys",
                        typography = TypographyTokenRef("title.large"),
                    ),
                )
                UiTag(TagAtom(label = activeTab.badge, variant = TagVariant.BRAND))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                JourneyTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { onSelectTab(tab) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FreshExpressJourneyFlow(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val browsePage = remember { EndToEndJourneyFixtures.buildFreshBrowsePage() }
    val formValues =
        remember {
            mutableStateMapOf(
                EndToEndJourneyFixtures.FRESH_BERRIES_FIELD to "1",
                EndToEndJourneyFixtures.FRESH_LOAF_FIELD to "1",
            )
        }
    var orderPlaced by remember { mutableStateOf(false) }
    var trackerStage by remember { mutableIntStateOf(0) }
    var celebrationVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!orderPlaced) {
            FreshBrowseStageCard(
                registry = registry,
                browsePage = browsePage,
                formValues = formValues.toMap(),
                onFormChange = { fieldId, value -> formValues[fieldId] = value },
                onAction = { action ->
                    onAction(action)
                    if (action.id == EndToEndJourneyFixtures.FRESH_PLACE_ORDER_ACTION) {
                        orderPlaced = true
                        trackerStage = 0
                        celebrationVisible = true
                    }
                },
            )
        } else {
            FreshOrderTrackingCard(
                registry = registry,
                trackerStage = trackerStage,
                onAdvanceStep = {
                    trackerStage = (trackerStage + 1).coerceAtMost(EndToEndJourneyFixtures.TRACKER_STAGE_COUNT - 1)
                },
                onNewOrder = {
                    orderPlaced = false
                    trackerStage = 0
                    celebrationVisible = false
                    formValues[EndToEndJourneyFixtures.FRESH_BERRIES_FIELD] = "1"
                    formValues[EndToEndJourneyFixtures.FRESH_LOAF_FIELD] = "1"
                },
                onAction = onAction,
            )
        }

        UiCelebrationOverlay(
            visible = celebrationVisible,
            title = "Express Order Placed!",
            subtitle = "Abeta Fresh rider assigned · Arriving in 8 mins",
            onDismiss = { celebrationVisible = false },
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FreshBrowseStageCard(
    registry: SnippetRegistry,
    browsePage: PageResponse,
    formValues: Map<String, String>,
    onFormChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    val frameShape = RoundedCornerShape(ThemeTokens.cornerRadius.md)

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Step 1 · Browse & Unlock Free Delivery",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                UiTag(TagAtom(label = "8-Min Express", variant = TagVariant.SUCCESS))
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(FRESH_VIEWPORT_HEIGHT_DP.dp)
                        .clip(frameShape)
                        .border(ThemeTokens.strokeWidth.thin, colors.border.default, frameShape),
            ) {
                SduiScreen(
                    state = LayoutUiState.Ready(page = browsePage, formValues = formValues),
                    snippetRegistry = registry,
                    onAction = onAction,
                    onFormValueChange = onFormChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FreshOrderTrackingCard(
    registry: SnippetRegistry,
    trackerStage: Int,
    onAdvanceStep: () -> Unit,
    onNewOrder: () -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val trackerEnvelope = remember(trackerStage) { EndToEndJourneyFixtures.buildFreshTrackerEnvelope(trackerStage) }
    val isDelivered = trackerStage >= EndToEndJourneyFixtures.TRACKER_STAGE_COUNT - 1

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Step 2 · Live 4-Stage Rider Tracker",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    Button(onClick = onAdvanceStep, enabled = !isDelivered) { Text("Advance Step") }
                    OutlinedButton(onClick = onNewOrder) { Text("New Order") }
                }
            }
            registry.Render(trackerEnvelope, emptyMap(), { _, _ -> }, onAction)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SparkMatchJourneyFlow(
    registry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val chipsEnvelope = remember { EndToEndJourneyFixtures.buildSparkFilterChipsEnvelope() }
    val deckEnvelope = remember { EndToEndJourneyFixtures.buildSparkSwipeDeckEnvelope() }
    val formValues = remember { mutableStateMapOf<String, String>() }
    val matches = remember { mutableStateListOf<SparkMatchRecord>() }
    var matchedName by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
            UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                    UiText(
                        TextAtom(
                            text = "Abeta Spark · Filter & Swipe to Match",
                            typography = TypographyTokenRef("title.medium"),
                        ),
                    )
                    registry.Render(chipsEnvelope, formValues.toMap(), { k, v -> formValues[k] = v }, onAction)
                    registry.Render(
                        envelope = deckEnvelope,
                        formValues = formValues.toMap(),
                        onFormValueChange = { k, v -> formValues[k] = v },
                        onAction = { action ->
                            onAction(action)
                            val decision = action.payloadStringOrEmpty("decision")
                            if (decision == "like" || decision == "super_like") {
                                val name = action.payloadStringOrEmpty("profile_name").ifEmpty { "New Spark" }
                                val role = action.payloadStringOrEmpty("profile_role").ifEmpty { "Abeta Spark Member" }
                                if (matches.none { it.name == name }) {
                                    val rec = SparkMatchRecord(name, role, superLike = decision == "super_like")
                                    matches.add(0, rec)
                                }
                                matchedName = name
                            }
                        },
                    )
                }
            }
            SparkMatchesInboxCard(matches = matches, onClear = { matches.clear() })
        }

        UiCelebrationOverlay(
            visible = matchedName != null,
            title = "It's a Match!",
            subtitle = matchedName?.let { "You and $it connected — saved to Matches Inbox!" },
            onDismiss = { matchedName = null },
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SparkMatchesInboxCard(
    matches: List<SparkMatchRecord>,
    onClear: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiText(
                    TextAtom(
                        text = "Matches Inbox (${matches.size})",
                        typography = TypographyTokenRef("title.medium"),
                    ),
                )
                if (matches.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear Inbox") }
            }
            if (matches.isEmpty()) {
                Text(
                    text = "Tap Like or Super Like on any card above to unlock a match and populate your inbox.",
                    style = ThemeTokens.typography.bodySmall,
                    color = colors.text.secondary,
                )
            } else {
                matches.forEach { match ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UiAvatar(AvatarAtom(contentDescription = match.name, sizeDp = MATCH_AVATAR_SIZE_DP))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = match.name,
                                style = ThemeTokens.typography.titleSmall,
                                color = colors.text.primary,
                            )
                            Text(
                                text = match.role,
                                style = ThemeTokens.typography.caption,
                                color = colors.text.secondary,
                            )
                        }
                        UiTag(
                            TagAtom(
                                label = if (match.superLike) "SUPER SPARK" else "MATCHED",
                                variant = if (match.superLike) TagVariant.BRAND else TagVariant.SUCCESS,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun ActionRef.payloadStringOrEmpty(key: String): String = (payload[key] as? JsonPrimitive)?.content.orEmpty()
