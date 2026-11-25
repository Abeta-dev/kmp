package dev.abeta.kmp.layout.snippet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.ResponsiveIntSpec
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import dev.abeta.kmp.tokens.WindowWidthClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_CAROUSEL_ITEM_WIDTH_DP = 260
private const val MIN_CAROUSEL_ITEM_WIDTH_DP = 64
private const val DEFAULT_CAROUSEL_SPACING_DP = 12
private const val DEFAULT_GRID_SPACING_DP = 8
private const val SCROLLABLE_TABS_MIN_COUNT = 3
private const val ACCORDION_CHEVRON_SIZE_DP = 20
private const val ACCORDION_EXPANDED_DEGREES = 90f

internal fun resolveResponsiveColumns(
    columns: Int,
    columnsByWidth: ResponsiveColumnsSpec?,
    widthClass: WindowWidthClass,
): Int {
    val selected =
        when (widthClass) {
            WindowWidthClass.COMPACT -> columnsByWidth?.compact
            WindowWidthClass.MEDIUM -> columnsByWidth?.medium ?: columnsByWidth?.compact
            WindowWidthClass.EXPANDED -> columnsByWidth?.expanded ?: columnsByWidth?.medium ?: columnsByWidth?.compact
        }
    return (selected ?: columns).coerceAtLeast(1)
}

internal fun resolveResponsiveItemWidthDp(
    itemWidthDp: Int?,
    itemWidthByWidth: ResponsiveIntSpec?,
    widthClass: WindowWidthClass,
): Int {
    val selected =
        when (widthClass) {
            WindowWidthClass.COMPACT -> {
                itemWidthByWidth?.compact
            }

            WindowWidthClass.MEDIUM -> {
                itemWidthByWidth?.medium ?: itemWidthByWidth?.compact
            }

            WindowWidthClass.EXPANDED -> {
                itemWidthByWidth?.expanded ?: itemWidthByWidth?.medium ?: itemWidthByWidth?.compact
            }
        }
    return (selected ?: itemWidthDp ?: DEFAULT_CAROUSEL_ITEM_WIDTH_DP)
        .coerceAtLeast(MIN_CAROUSEL_ITEM_WIDTH_DP)
}

internal class CarouselSnippetRenderer(
    json: Json,
    private val registryProvider: () -> SnippetRegistry,
) : SnippetRenderer<CarouselSnippetData> {
    override val snippetType: String = SnippetTypes.CAROUSEL
    private val decoder = kotlinxSnippetDecoder<CarouselSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<CarouselSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: CarouselSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val depth = LocalSnippetDepth.current
        if (depth >= MAX_SNIPPET_NESTING_DEPTH) return

        val spacing = ThemeTokens.spacing
        val registry = LocalSnippetRegistry.current ?: registryProvider()
        val widthClass = ThemeTokens.adaptiveMetrics.widthClass
        val itemWidth =
            resolveResponsiveItemWidthDp(
                itemWidthDp = model.itemWidthDp,
                itemWidthByWidth = model.itemWidthByWidth,
                widthClass = widthClass,
            ).dp
        val itemSpacing = (model.spacingDp ?: DEFAULT_CAROUSEL_SPACING_DP).coerceAtLeast(0).dp
        val itemKeys = remember(model.items) { buildUniqueSnippetKeys(model.items) }

        Column(
            modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            model.title?.let { title ->
                UiText(atom = title, onAction = onAction)
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                itemsIndexed(
                    items = model.items,
                    key = { index, _ -> itemKeys[index] },
                ) { index, child ->
                    Box(modifier = Modifier.width(itemWidth)) {
                        CompositionLocalProvider(
                            LocalMotionStaggerIndex provides index,
                            LocalSnippetDepth provides depth + 1,
                        ) {
                            registry.Render(
                                envelope = child,
                                formValues = formValues,
                                onFormValueChange = onFormValueChange,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal class GridSnippetRenderer(
    json: Json,
    private val registryProvider: () -> SnippetRegistry,
) : SnippetRenderer<GridSnippetData> {
    override val snippetType: String = SnippetTypes.GRID
    private val decoder = kotlinxSnippetDecoder<GridSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<GridSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: GridSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val depth = LocalSnippetDepth.current
        if (depth >= MAX_SNIPPET_NESTING_DEPTH) return

        val registry = LocalSnippetRegistry.current ?: registryProvider()
        val columns =
            resolveResponsiveColumns(
                columns = model.columns,
                columnsByWidth = model.columnsByWidth,
                widthClass = ThemeTokens.adaptiveMetrics.widthClass,
            )
        val gridSpacing = (model.spacingDp ?: DEFAULT_GRID_SPACING_DP).coerceAtLeast(0).dp
        val rows = model.items.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
        ) {
            model.title?.let { title ->
                UiText(atom = title, onAction = onAction)
            }
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                ) {
                    rowItems.forEachIndexed { colIndex, child ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompositionLocalProvider(
                                LocalMotionStaggerIndex provides (rowIndex * columns + colIndex),
                                LocalSnippetDepth provides depth + 1,
                            ) {
                                registry.Render(
                                    envelope = child,
                                    formValues = formValues,
                                    onFormValueChange = onFormValueChange,
                                    onAction = onAction,
                                )
                            }
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal class TabsSnippetRenderer(
    json: Json,
    private val registryProvider: () -> SnippetRegistry,
) : SnippetRenderer<TabsSnippetData> {
    override val snippetType: String = SnippetTypes.TABS
    private val decoder = kotlinxSnippetDecoder<TabsSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<TabsSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: TabsSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val depth = LocalSnippetDepth.current
        if (depth >= MAX_SNIPPET_NESTING_DEPTH || model.tabs.isEmpty()) return

        val spacing = ThemeTokens.spacing
        val motion = ThemeTokens.motion
        val registry = LocalSnippetRegistry.current ?: registryProvider()
        val initialIndex = model.selectedIndex.coerceIn(0, model.tabs.lastIndex)
        var selectedIndex by remember(envelope.snippetId, model.tabs.size, initialIndex) {
            mutableIntStateOf(initialIndex)
        }
        val activeIndex = selectedIndex.coerceIn(0, model.tabs.lastIndex)

        Column(
            modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            TabsHeaderStrip(
                tabs = model.tabs,
                activeIndex = activeIndex,
                onSelectTab = { selectedIndex = it },
                onAction = onAction,
            )

            CompositionLocalProvider(LocalSnippetDepth provides depth + 1) {
                if (motion.reducedMotion) {
                    TabsBodyContent(
                        snippets =
                            model.tabs
                                .getOrNull(activeIndex)
                                ?.snippets
                                .orEmpty(),
                        registry = registry,
                        formValues = formValues,
                        onFormValueChange = onFormValueChange,
                        onAction = onAction,
                    )
                } else {
                    AnimatedContent(
                        targetState = activeIndex,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(motion.fastMs)) togetherWith
                                fadeOut(animationSpec = tween(motion.fastMs))
                        },
                        label = "sdui_tabs_transition",
                    ) { targetIndex ->
                        TabsBodyContent(
                            snippets =
                                model.tabs
                                    .getOrNull(targetIndex)
                                    ?.snippets
                                    .orEmpty(),
                            registry = registry,
                            formValues = formValues,
                            onFormValueChange = onFormValueChange,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun TabsHeaderStrip(
        tabs: List<dev.abeta.kmp.contracts.TabItemSpec>,
        activeIndex: Int,
        onSelectTab: (Int) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val useScrollable =
            ThemeTokens.adaptiveMetrics.widthClass == WindowWidthClass.COMPACT &&
                tabs.size >= SCROLLABLE_TABS_MIN_COUNT
        val tabItems: @Composable () -> Unit = {
            tabs.forEachIndexed { index, tab ->
                TabItemButton(
                    tab = tab,
                    selected = index == activeIndex,
                    onClick = { onSelectTab(index) },
                    onAction = onAction,
                )
            }
        }
        if (useScrollable) {
            SecondaryScrollableTabRow(
                selectedTabIndex = activeIndex,
                containerColor = colors.surface.primary,
                contentColor = colors.brand.primary,
                edgePadding = spacing.space12,
                tabs = tabItems,
            )
        } else {
            SecondaryTabRow(
                selectedTabIndex = activeIndex,
                containerColor = colors.surface.primary,
                contentColor = colors.brand.primary,
                tabs = tabItems,
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun TabItemButton(
        tab: dev.abeta.kmp.contracts.TabItemSpec,
        selected: Boolean,
        onClick: () -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography
        Tab(
            selected = selected,
            onClick = onClick,
            selectedContentColor = colors.brand.primary,
            unselectedContentColor = colors.text.secondary,
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    Text(
                        text = tab.title,
                        style = typography.labelLarge,
                        maxLines = 1,
                    )
                    tab.badge?.let { badge ->
                        UiTag(atom = badge, onAction = onAction)
                    }
                }
            },
        )
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun TabsBodyContent(
        snippets: List<SnippetEnvelope>,
        registry: SnippetRegistry,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            snippets.forEach { child ->
                registry.Render(
                    envelope = child,
                    formValues = formValues,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                )
            }
        }
    }
}

internal class AccordionSnippetRenderer(
    json: Json,
    private val registryProvider: () -> SnippetRegistry,
) : SnippetRenderer<AccordionSnippetData> {
    override val snippetType: String = SnippetTypes.ACCORDION
    private val decoder = kotlinxSnippetDecoder<AccordionSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<AccordionSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: AccordionSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val depth = LocalSnippetDepth.current
        if (depth >= MAX_SNIPPET_NESTING_DEPTH) return

        val spacing = ThemeTokens.spacing
        val reducedMotion = ThemeTokens.motion.reducedMotion
        val registry = LocalSnippetRegistry.current ?: registryProvider()
        var expanded by remember(envelope.snippetId, model.initiallyExpanded) {
            mutableStateOf(model.initiallyExpanded)
        }

        Column(
            modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            AccordionHeaderRow(
                model = model,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onAction = onAction,
            )

            CompositionLocalProvider(LocalSnippetDepth provides depth + 1) {
                if (reducedMotion) {
                    if (expanded) {
                        AccordionSnippetsBody(
                            snippets = model.snippets,
                            registry = registry,
                            formValues = formValues,
                            onFormValueChange = onFormValueChange,
                            onAction = onAction,
                        )
                    }
                } else {
                    AnimatedVisibility(visible = expanded) {
                        AccordionSnippetsBody(
                            snippets = model.snippets,
                            registry = registry,
                            formValues = formValues,
                            onFormValueChange = onFormValueChange,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun AccordionHeaderRow(
        model: AccordionSnippetData,
        expanded: Boolean,
        onToggle: () -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val sizing = ThemeTokens.sizing
        val motion = ThemeTokens.motion
        val chevronDegreesState =
            animateFloatAsState(
                targetValue = if (expanded) ACCORDION_EXPANDED_DEGREES else 0f,
                animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
                label = "accordion_chevron_rotation",
            )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = sizing.minTouchTarget)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .clickable(role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                UiText(atom = model.title, onAction = onAction)
                model.subtitle?.let { subtitle ->
                    UiText(atom = subtitle, onAction = onAction)
                }
            }
            UiIcon(
                atom =
                    IconAtom(
                        name = "chevron_right",
                        sizeDp = ACCORDION_CHEVRON_SIZE_DP,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    ),
                modifier = Modifier.graphicsLayer { rotationZ = chevronDegreesState.value },
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun AccordionSnippetsBody(
        snippets: List<SnippetEnvelope>,
        registry: SnippetRegistry,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            snippets.forEach { child ->
                registry.Render(
                    envelope = child,
                    formValues = formValues,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                )
            }
        }
    }
}
