package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.ThemeTokens

private const val TOTAL_SNIPPET_COUNT = 17

/**
 * Interactive gallery rendering all 17 built-in SDUI [SnippetTypes] through [BuiltInSnippetModule].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun SnippetsStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    val allItems = remember { SnippetsSamples.allItems() }
    val formValues = remember { mutableStateMapOf<String, String>() }
    var selectedType by remember { mutableStateOf<String?>(null) }

    val visibleItems =
        remember(selectedType, allItems) {
            if (selectedType == null) {
                allItems
            } else {
                allItems.filter { it.envelope.snippetType == selectedType }
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        SnippetGalleryHeaderCard(
            registeredCount = registry.registeredTypes().size,
            allItems = allItems,
            selectedType = selectedType,
            onSelectType = { selectedType = it },
        )

        visibleItems.forEach { item ->
            SnippetPreviewSection(
                item = item,
                registry = registry,
                formValues = formValues,
                onFormValueChange = { fieldId, updated -> formValues[fieldId] = updated },
                onAction = onAction,
            )
        }

        if (selectedType != null && visibleItems.size == 1) {
            val selectedEnvelope = visibleItems.first().envelope
            JsonContractPreviewCard(
                title = "SnippetEnvelope Wire JSON (${selectedEnvelope.snippetType})",
                jsonText = SduiJson.encodeToString(SnippetEnvelope.serializer(), selectedEnvelope),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SnippetGalleryHeaderCard(
    registeredCount: Int,
    allItems: List<SnippetSampleItem>,
    selectedType: String?,
    onSelectType: (String?) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "SDUI Snippet Registry Gallery", typography = TypographyTokenRef("title.large")))
            UiTag(
                TagAtom(
                    label = "$registeredCount / $TOTAL_SNIPPET_COUNT Registered",
                    variant = TagVariant.BRAND,
                ),
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { onSelectType(null) },
                label = { Text("All ($TOTAL_SNIPPET_COUNT)") },
            )
            allItems.forEach { item ->
                val type = item.envelope.snippetType
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onSelectType(type) },
                    label = { Text(item.label) },
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SnippetPreviewSection(
    item: SnippetSampleItem,
    registry: SnippetRegistry,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = item.label, typography = TypographyTokenRef("title.medium")))
            UiTag(TagAtom(label = item.envelope.snippetType, variant = TagVariant.NEUTRAL))
        }
        registry.Render(
            envelope = item.envelope,
            formValues = formValues,
            onFormValueChange = onFormValueChange,
            onAction = onAction,
        )
        if (item.envelope.snippetType == SnippetTypes.SPACER) {
            Text(
                text = "Spacer rendered above (24dp vertical height)",
                style = ThemeTokens.typography.caption,
                color = colors.text.tertiary,
            )
        }
    }
}
