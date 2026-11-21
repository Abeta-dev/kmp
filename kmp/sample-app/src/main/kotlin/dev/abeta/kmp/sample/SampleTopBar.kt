package dev.abeta.kmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.tokens.ThemeTokens

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun SampleTopBar(
    verticalApps: List<ShowcaseAppSpec>,
    availableScreens: List<String>,
    activeScreen: String,
    mode: SampleLauncherMode,
    pendingOfflineCount: Int,
    darkTheme: Boolean,
    onSelectMode: (SampleLauncherMode) -> Unit,
    onSelectScreen: (String) -> Unit,
    onFlushOffline: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val activeBaseScreen = SampleShowcaseApps.normalizeBaseScreenId(activeScreen)
    val activeApp = SampleShowcaseApps.findAppForScreen(activeScreen)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface.secondary)
                .padding(horizontal = spacing.space16, vertical = spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        SampleTopBarControlsRow(
            showcaseCount = verticalApps.size,
            referenceCount = availableScreens.size,
            activeApp = activeApp,
            mode = mode,
            pendingOfflineCount = pendingOfflineCount,
            darkTheme = darkTheme,
            onSelectMode = onSelectMode,
            onFlushOffline = onFlushOffline,
            onToggleTheme = onToggleTheme,
        )
        SampleTopBarTargetChips(
            mode = mode,
            verticalApps = verticalApps,
            availableScreens = availableScreens,
            activeApp = activeApp,
            activeBaseScreen = activeBaseScreen,
            onSelectScreen = onSelectScreen,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SampleTopBarControlsRow(
    showcaseCount: Int,
    referenceCount: Int,
    activeApp: ShowcaseAppSpec?,
    mode: SampleLauncherMode,
    pendingOfflineCount: Int,
    darkTheme: Boolean,
    onSelectMode: (SampleLauncherMode) -> Unit,
    onFlushOffline: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        FilterChip(
            selected = mode == SampleLauncherMode.SHOWCASE_APPS,
            onClick = { onSelectMode(SampleLauncherMode.SHOWCASE_APPS) },
            label = { Text(text = "Showcase Apps ($showcaseCount)", style = typography.labelSmall) },
        )
        FilterChip(
            selected = mode == SampleLauncherMode.KIT_REFERENCE,
            onClick = { onSelectMode(SampleLauncherMode.KIT_REFERENCE) },
            label = { Text(text = "Kit Reference ($referenceCount)", style = typography.labelSmall) },
        )
        if (activeApp != null && mode == SampleLauncherMode.SHOWCASE_APPS) {
            Text(
                text = "${activeApp.name} · ${activeApp.category}",
                style = typography.labelMedium,
                color = colors.text.secondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onFlushOffline, enabled = pendingOfflineCount > 0) {
            val label = if (pendingOfflineCount > 0) "Sync ($pendingOfflineCount)" else "Queue (0)"
            Text(text = label, style = typography.labelSmall)
        }
        OutlinedButton(onClick = onToggleTheme) {
            Text(text = if (darkTheme) "Light Theme" else "Dark Theme", style = typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SampleTopBarTargetChips(
    mode: SampleLauncherMode,
    verticalApps: List<ShowcaseAppSpec>,
    availableScreens: List<String>,
    activeApp: ShowcaseAppSpec?,
    activeBaseScreen: String,
    onSelectScreen: (String) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (mode == SampleLauncherMode.SHOWCASE_APPS) {
            verticalApps.forEach { app ->
                FilterChip(
                    selected = activeApp?.id == app.id,
                    onClick = { onSelectScreen(app.entryScreen) },
                    label = { Text(text = "${app.name} · ${app.category}", style = typography.labelSmall) },
                )
            }
        } else {
            availableScreens.forEach { screenId ->
                FilterChip(
                    selected = activeBaseScreen == screenId,
                    onClick = { onSelectScreen(screenId) },
                    label = { Text(text = screenId, style = typography.labelSmall) },
                )
            }
        }
    }
}
