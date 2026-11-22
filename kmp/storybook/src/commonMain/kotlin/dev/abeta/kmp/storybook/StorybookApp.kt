package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.LocalImageRenderer
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.media.KtorImageBytesFetcher
import dev.abeta.kmp.layout.media.KtorImageRenderer
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.tokens.DesignTheme
import dev.abeta.kmp.tokens.ProvideAdaptiveLayoutMetrics
import dev.abeta.kmp.tokens.ThemePreset
import dev.abeta.kmp.tokens.ThemeTokens
import dev.abeta.kmp.tokens.WindowWidthClass
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SIDEBAR_WIDTH_DP = 250
private const val ACTION_LOG_LIMIT = 8
private const val ACTION_CONSOLE_PREVIEW_COUNT = 3
private const val CONSOLE_MAX_HEIGHT_DP = 130
private const val MIN_FONT_SCALE = 0.85f
private const val DEFAULT_FONT_SCALE = 1.0f
private const val MAX_FONT_SCALE = 1.5f
private const val FONT_SCALE_STEP = 0.15f
private const val FONT_SCALE_EPSILON = 0.01f
private const val PERCENT_MULTIPLIER = 100
private const val DEFAULT_IMAGE_BASE_URL = "http://localhost:8080"

/**
 * Multiplatform root composable rendering the interactive Abeta KMP Storybook.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun StorybookApp(
    initialStoryId: String = StoryCatalog.STORY_INTRODUCTION,
    initialPreset: ThemePreset = ThemePreset.EMERALD,
    initialDarkTheme: Boolean = false,
) {
    var selectedStoryId by remember { mutableStateOf(initialStoryId) }
    var preset by remember { mutableStateOf(initialPreset) }
    var darkTheme by remember { mutableStateOf(initialDarkTheme) }
    var reducedMotion by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(DEFAULT_FONT_SCALE) }
    val actionLogs = remember { mutableStateListOf<String>() }

    val imageHttpClient =
        remember {
            HttpClientFactory.create(
                config =
                    NetworkConfig(
                        baseUrl = DEFAULT_IMAGE_BASE_URL,
                        retry = RetryPolicy(maxRetries = 0),
                    ),
            )
        }
    DisposableEffect(imageHttpClient) { onDispose { imageHttpClient.close() } }
    val imageRenderer = remember(imageHttpClient) { KtorImageRenderer(KtorImageBytesFetcher(imageHttpClient)) }

    val recordAction: (ActionRef) -> Unit = { action ->
        actionLogs.add(0, "[${action.type}] id=${action.id} payload=${action.payload}")
        if (actionLogs.size > ACTION_LOG_LIMIT) actionLogs.removeAt(actionLogs.lastIndex)
    }

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = currentDensity.density, fontScale = fontScale),
        LocalImageRenderer provides imageRenderer,
    ) {
        DesignTheme(preset = preset, darkTheme = darkTheme, reducedMotion = reducedMotion) {
            StorybookScaffold(
                selectedStoryId = selectedStoryId,
                preset = preset,
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
                fontScale = fontScale,
                actionLogs = actionLogs,
                onSelectStory = { selectedStoryId = it },
                onPresetChange = { preset = it },
                onDarkThemeChange = { darkTheme = it },
                onReducedMotionChange = { reducedMotion = it },
                onFontScaleChange = { fontScale = it.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) },
                onClearLogs = { actionLogs.clear() },
                onAction = recordAction,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun StorybookScaffold(
    selectedStoryId: String,
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
    fontScale: Float,
    actionLogs: List<String>,
    onSelectStory: (String) -> Unit,
    onPresetChange: (ThemePreset) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onClearLogs: () -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = ThemeTokens.colors.background.primary) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            ProvideAdaptiveLayoutMetrics(maxWidth = maxWidth, maxHeight = maxHeight) {
                val isCompact = ThemeTokens.adaptiveMetrics.widthClass == WindowWidthClass.COMPACT
                if (isCompact) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CompactStoryStrip(selectedStoryId = selectedStoryId, onSelectStory = onSelectStory)
                        UiDivider(DividerAtom())
                        StoryStagePane(
                            selectedStoryId = selectedStoryId,
                            preset = preset,
                            darkTheme = darkTheme,
                            reducedMotion = reducedMotion,
                            fontScale = fontScale,
                            actionLogs = actionLogs,
                            onPresetChange = onPresetChange,
                            onDarkThemeChange = onDarkThemeChange,
                            onReducedMotionChange = onReducedMotionChange,
                            onFontScaleChange = onFontScaleChange,
                            onClearLogs = onClearLogs,
                            onAction = onAction,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        StorySidebar(selectedStoryId = selectedStoryId, onSelectStory = onSelectStory)
                        UiDivider(DividerAtom(), modifier = Modifier.fillMaxHeight().width(1.dp))
                        StoryStagePane(
                            selectedStoryId = selectedStoryId,
                            preset = preset,
                            darkTheme = darkTheme,
                            reducedMotion = reducedMotion,
                            fontScale = fontScale,
                            actionLogs = actionLogs,
                            onPresetChange = onPresetChange,
                            onDarkThemeChange = onDarkThemeChange,
                            onReducedMotionChange = onReducedMotionChange,
                            onFontScaleChange = onFontScaleChange,
                            onClearLogs = onClearLogs,
                            onAction = onAction,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CompactStoryStrip(
    selectedStoryId: String,
    onSelectStory: (String) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ThemeTokens.colors.surface.primary)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.space12, vertical = spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        StoryCatalog.entries.forEach { entry ->
            FilterChip(
                selected = entry.id == selectedStoryId,
                onClick = { onSelectStory(entry.id) },
                label = { Text(entry.title) },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun StoryStagePane(
    selectedStoryId: String,
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
    fontScale: Float,
    actionLogs: List<String>,
    onPresetChange: (ThemePreset) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onClearLogs: () -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StoryTopBar(
            entry = StoryCatalog.findById(selectedStoryId),
            preset = preset,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            fontScale = fontScale,
            onPresetChange = onPresetChange,
            onDarkThemeChange = onDarkThemeChange,
            onReducedMotionChange = onReducedMotionChange,
            onFontScaleChange = onFontScaleChange,
        )
        UiDivider(DividerAtom())
        val stageScrollState = rememberScrollState()
        LaunchedEffect(selectedStoryId) { stageScrollState.scrollTo(0) }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(stageScrollState)
                    .padding(ThemeTokens.spacing.space24),
        ) {
            StoryStage(selectedStoryId, preset, darkTheme, reducedMotion, onAction)
        }
        ActionConsoleBar(logs = actionLogs, onClear = onClearLogs)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StorySidebar(
    selectedStoryId: String,
    onSelectStory: (String) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    Column(
        modifier =
            Modifier
                .width(SIDEBAR_WIDTH_DP.dp)
                .fillMaxHeight()
                .background(colors.surface.primary)
                .verticalScroll(rememberScrollState())
                .padding(spacing.space16),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
            UiText(TextAtom(text = "abeta-dev/kmp", typography = TypographyTokenRef("title.medium")))
            UiTag(TagAtom(label = "Storybook", variant = TagVariant.BRAND))
        }
        UiDivider(DividerAtom())
        StoryCategory.entries.forEach { category ->
            Text(
                text = category.title.uppercase(),
                style = ThemeTokens.typography.overline,
                color = colors.text.tertiary,
            )
            StoryCatalog.entries
                .filter { it.category == category }
                .forEach { story ->
                    val isSelected = story.id == selectedStoryId
                    val itemShape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(if (isSelected) colors.brand.container else colors.surface.primary)
                                .clickable { onSelectStory(story.id) }
                                .padding(horizontal = spacing.space12, vertical = spacing.space8),
                    ) {
                        Text(
                            text = story.title,
                            style = ThemeTokens.typography.labelLarge,
                            color = if (isSelected) colors.brand.onContainer else colors.text.primary,
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun StoryTopBar(
    entry: StoryEntry,
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
    fontScale: Float,
    onPresetChange: (ThemePreset) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface.primary)
                .padding(horizontal = spacing.space24, vertical = spacing.space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = spacing.space12)) {
            Text(text = entry.title, style = ThemeTokens.typography.titleLarge, color = colors.text.primary)
            Text(text = entry.subtitle, style = ThemeTokens.typography.bodySmall, color = colors.text.secondary)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            ThemePreset.entries.forEach { item ->
                FilterChip(
                    selected = preset == item,
                    onClick = { onPresetChange(item) },
                    label = { Text(item.displayName) },
                )
            }
            FontScaleChips(fontScale = fontScale, onFontScaleChange = onFontScaleChange)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Text(text = "Dark", style = ThemeTokens.typography.labelSmall, color = colors.text.secondary)
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                Text(text = "Reduced Motion", style = ThemeTokens.typography.labelSmall, color = colors.text.secondary)
                Switch(checked = reducedMotion, onCheckedChange = onReducedMotionChange)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FontScaleChips(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
) {
    val isDefault = abs(fontScale - DEFAULT_FONT_SCALE) < FONT_SCALE_EPSILON
    val scaleLabel = if (isDefault) "1.0x" else "${(fontScale * PERCENT_MULTIPLIER).roundToInt()}%"
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space4)) {
        FilterChip(
            selected = false,
            enabled = fontScale > MIN_FONT_SCALE + FONT_SCALE_EPSILON,
            onClick = { onFontScaleChange(fontScale - FONT_SCALE_STEP) },
            label = { Text("A-") },
        )
        FilterChip(
            selected = isDefault,
            onClick = { onFontScaleChange(DEFAULT_FONT_SCALE) },
            label = { Text(scaleLabel) },
        )
        FilterChip(
            selected = false,
            enabled = fontScale < MAX_FONT_SCALE - FONT_SCALE_EPSILON,
            onClick = { onFontScaleChange(fontScale + FONT_SCALE_STEP) },
            label = { Text("A+") },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StoryStage(
    storyId: String,
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
    onAction: (ActionRef) -> Unit,
) {
    when (storyId) {
        StoryCatalog.STORY_INTRODUCTION -> IntroductionStory()
        StoryCatalog.STORY_DESIGN_TOKENS -> DesignTokensStory(preset, darkTheme, reducedMotion)
        StoryCatalog.STORY_BUTTON -> ButtonStory(onAction)
        StoryCatalog.STORY_TEXT_TAG -> TypographyAndTagStory(onAction)
        StoryCatalog.STORY_TEXT_FIELD -> TextFieldStory(onAction)
        StoryCatalog.STORY_MEDIA_FEEDBACK -> MediaFeedbackStory(onAction)
        StoryCatalog.STORY_CONTROLS_DATA -> ControlsAndDataAtomsStory(onAction)
        else -> PlaygroundAndSnippetStage(storyId = storyId, onAction = onAction)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PlaygroundAndSnippetStage(
    storyId: String,
    onAction: (ActionRef) -> Unit,
) {
    when (storyId) {
        StoryCatalog.STORY_SNIPPETS -> SnippetsStory(onAction)
        StoryCatalog.STORY_PLAYGROUND -> PlaygroundStory(onAction)
        StoryCatalog.STORY_MOTION_STORE -> MotionAndStoreStory(onAction)
        StoryCatalog.STORY_LIVE_BFF -> LiveBffStory(onAction)
        StoryCatalog.STORY_RESPONSIVE_BRIDGES -> ResponsiveAndBridgesStory(onAction)
        StoryCatalog.STORY_DISCOVERY_SEARCH -> DiscoveryAndSearchStory(onAction)
        else -> LabAndJourneyStage(storyId = storyId, onAction = onAction)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun LabAndJourneyStage(
    storyId: String,
    onAction: (ActionRef) -> Unit,
) {
    when (storyId) {
        StoryCatalog.STORY_ANIMATION_NUDGES -> AnimationNudgesStory(onAction)
        StoryCatalog.STORY_SNIPPET_CRAFTER -> SnippetCrafterStory(onAction)
        StoryCatalog.STORY_E2E_JOURNEYS -> EndToEndJourneysStory(onAction)
        else -> IntroductionStory()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ActionConsoleBar(
    logs: List<String>,
    onClear: () -> Unit,
) {
    val colors = ThemeTokens.colors
    UiDivider(DividerAtom())
    UiSurfaceCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = CONSOLE_MAX_HEIGHT_DP.dp)
                .background(colors.surface.secondary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(
                TextAtom(
                    text = "ActionRef Event Console (${logs.size})",
                    typography = TypographyTokenRef("label.large"),
                    color = ColorTokenRef("text.primary"),
                ),
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
        if (logs.isEmpty()) {
            Text(
                text = "Interact with any atom, snippet, or BFF explorer story to inspect emitted ActionRefs.",
                style = ThemeTokens.typography.caption,
                color = colors.text.tertiary,
            )
        } else {
            logs.take(ACTION_CONSOLE_PREVIEW_COUNT).forEach { entry ->
                Text(
                    text = entry,
                    style = ThemeTokens.typography.caption.copy(fontFamily = ThemeTokens.typography.fonts.mono),
                    color = colors.text.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}
