package dev.abeta.kmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.tokens.ThemeTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun SampleTopBar(
    availableScreens: List<String>,
    activeScreen: String,
    pendingOfflineCount: Int,
    darkTheme: Boolean,
    onSelectScreen: (String) -> Unit,
    onFlushOffline: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface.secondary)
                .padding(horizontal = spacing.space16, vertical = spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Text(text = "Screens:", style = typography.labelLarge, color = colors.text.secondary)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            availableScreens.forEach { screenId ->
                FilterChip(
                    selected = activeScreen == screenId,
                    onClick = { onSelectScreen(screenId) },
                    label = { Text(text = screenId, style = typography.labelSmall) },
                )
            }
        }
        OutlinedButton(
            onClick = onFlushOffline,
            enabled = pendingOfflineCount > 0,
        ) {
            Text(
                text = if (pendingOfflineCount > 0) "Sync ($pendingOfflineCount)" else "Queue (0)",
                style = typography.labelSmall,
            )
        }
        OutlinedButton(onClick = onToggleTheme) {
            Text(
                text = if (darkTheme) "Light Theme" else "Dark Theme",
                style = typography.labelSmall,
            )
        }
    }
}
