@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val QUERY_PLACEHOLDER = "{{query}}"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val HEX_HIGH_SHIFT = 4
private const val HEX_NIBBLE_MASK = 0x0F
private const val BYTE_MASK = 0xFF
private const val SEARCH_ICON_SIZE_DP = 18
private const val CLEAR_BUTTON_SIZE_DP = 32
private const val SUBMIT_PILL_MIN_HEIGHT_DP = 36
private const val CHIP_MIN_HEIGHT_DP = 36
private const val CHIP_ICON_SIZE_DP = 16
private const val SELECTED_BADGE_BG_ALPHA = 0.22f
private const val CLEAR_GLYPH = "×"
private val URL_ENCODED_PAYLOAD_KEYS = setOf("route", "endpoint")

internal fun resolveQueryPlaceholders(
    action: ActionRef,
    query: String,
): ActionRef {
    val trimmed = query.trim()
    val encoded = encodeUrlQueryComponent(trimmed)
    return action.copy(
        payload = resolveObjectPlaceholders(action.payload, trimmed, encoded),
        secondaryActions = action.secondaryActions.map { resolveQueryPlaceholders(it, query) },
    )
}

private fun resolveObjectPlaceholders(
    obj: JsonObject,
    rawQuery: String,
    encodedQuery: String,
): JsonObject =
    JsonObject(
        obj.mapValues { (key, value) ->
            resolveElementPlaceholders(value, rawQuery, encodedQuery, key in URL_ENCODED_PAYLOAD_KEYS)
        },
    )

private fun resolveElementPlaceholders(
    element: JsonElement,
    rawQuery: String,
    encodedQuery: String,
    useUrlEncoding: Boolean,
): JsonElement =
    when (element) {
        is JsonPrimitive -> {
            if (element.isString && element.content.contains(QUERY_PLACEHOLDER)) {
                val replacement = if (useUrlEncoding) encodedQuery else rawQuery
                JsonPrimitive(element.content.replace(QUERY_PLACEHOLDER, replacement))
            } else {
                element
            }
        }

        is JsonObject -> {
            resolveObjectPlaceholders(element, rawQuery, encodedQuery)
        }

        is JsonArray -> {
            JsonArray(element.map { resolveElementPlaceholders(it, rawQuery, encodedQuery, useUrlEncoding) })
        }
    }

private fun Char.isUrlUnreserved(): Boolean =
    when (this) {
        in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> true
        else -> false
    }

private fun encodeUrlQueryComponent(value: String): String {
    if (value.isEmpty()) return ""
    val bytes = value.encodeToByteArray()
    val builder = StringBuilder(bytes.size)
    for (byte in bytes) {
        val code = byte.toInt() and BYTE_MASK
        val ch = code.toChar()
        if (ch.isUrlUnreserved()) {
            builder.append(ch)
        } else {
            builder.append('%')
            builder.append(HEX_DIGITS[(code shr HEX_HIGH_SHIFT) and HEX_NIBBLE_MASK])
            builder.append(HEX_DIGITS[code and HEX_NIBBLE_MASK])
        }
    }
    return builder.toString()
}

internal class SearchBarSnippetRenderer(
    json: Json,
) : SnippetRenderer<SearchBarSnippetData> {
    override val snippetType: String = SnippetTypes.SEARCH_BAR
    private val decoder = kotlinxSnippetDecoder<SearchBarSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<SearchBarSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: SearchBarSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val sizing = ThemeTokens.sizing
        val haptic = LocalUiHapticFeedback.current
        val currentQuery = formValues[model.searchId] ?: model.query
        val triggerSubmit: () -> Unit = {
            haptic(UiHapticKind.CLICK)
            model.onSubmit?.let { onAction(resolveQueryPlaceholders(it, currentQuery)) }
        }
        val triggerClear: () -> Unit = {
            haptic(UiHapticKind.CLICK)
            onFormValueChange(model.searchId, "")
            val clearAction = model.onClear
            if (clearAction != null) {
                onAction(clearAction)
            } else if (model.query.isNotEmpty()) {
                model.onSubmit?.let { onAction(resolveQueryPlaceholders(it, "")) }
            }
        }

        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("search_bar_${model.searchId}"),
            layout = model.layout,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = sizing.inputMinHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                val leading =
                    model.leadingIcon ?: IconAtom(
                        name = "search",
                        sizeDp = SEARCH_ICON_SIZE_DP,
                        color = ColorTokenRef(token = "icon.tertiary"),
                    )
                UiIcon(atom = leading, onAction = onAction)

                SearchTextField(
                    searchId = model.searchId,
                    query = currentQuery,
                    placeholder = model.placeholder,
                    onQueryChange = { onFormValueChange(model.searchId, it) },
                    onSearch = triggerSubmit,
                    modifier = Modifier.weight(1f),
                )

                if (model.clearable && currentQuery.isNotEmpty()) {
                    SearchClearButton(model.searchId, triggerClear)
                }

                model.trailingIcon?.let { trailing ->
                    val rawAction = model.onTrailingClick ?: trailing.onClick
                    val action = rawAction?.let { resolveQueryPlaceholders(it, currentQuery) }
                    UiIcon(atom = trailing.copy(onClick = action), onAction = onAction)
                }

                model.submitLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    SearchSubmitPill(model.searchId, label, triggerSubmit)
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun SearchTextField(
        searchId: String,
        query: String,
        placeholder: String,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val colors = ThemeTokens.colors
        val typography = ThemeTokens.typography
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier.testTag("search_input_$searchId"),
            singleLine = true,
            textStyle = typography.bodyLarge.copy(color = colors.input.foreground),
            cursorBrush = SolidColor(colors.brand.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = typography.bodyLarge,
                            color = colors.input.placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun SearchClearButton(
        searchId: String,
        onClick: () -> Unit,
    ) {
        val colors = ThemeTokens.colors
        Box(
            modifier =
                Modifier
                    .defaultMinSize(minWidth = CLEAR_BUTTON_SIZE_DP.dp, minHeight = CLEAR_BUTTON_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(colors.surface.secondary, CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = "Clear search" }
                    .testTag("search_clear_$searchId"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = CLEAR_GLYPH,
                style = ThemeTokens.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text.secondary,
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun SearchSubmitPill(
        searchId: String,
        label: String,
        onClick: () -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val shape = RoundedCornerShape(ThemeTokens.cornerRadius.full)
        Box(
            modifier =
                Modifier
                    .heightIn(min = SUBMIT_PILL_MIN_HEIGHT_DP.dp)
                    .clip(shape)
                    .background(colors.brand.primary, shape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = spacing.space12, vertical = spacing.space8)
                    .testTag("search_submit_$searchId"),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = ThemeTokens.typography.labelLarge, color = colors.brand.onPrimary, maxLines = 1)
        }
    }
}

internal class ChipGroupSnippetRenderer(
    json: Json,
) : SnippetRenderer<ChipGroupSnippetData> {
    override val snippetType: String = SnippetTypes.CHIP_GROUP
    private val decoder = kotlinxSnippetDecoder<ChipGroupSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<ChipGroupSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: ChipGroupSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val haptic = LocalUiHapticFeedback.current
        val serverSelectedIds =
            remember(model.chips) {
                model.chips
                    .filter { it.selected }
                    .map { it.id }
                    .toSet()
            }
        val serverSelectionSignature =
            remember(envelope.snippetId, model.chips) { model.chips.joinToString(",") { "${it.id}:${it.selected}" } }
        var localSelectedIds by remember(serverSelectionSignature) { mutableStateOf<Set<String>?>(null) }
        val effectiveSelectedIds = localSelectedIds ?: serverSelectedIds
        val formKey = "chip_${model.groupId}"

        Column(
            modifier = Modifier.fillMaxWidth().applyLayoutConfig(model.layout).testTag("chip_group_${model.groupId}"),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            model.title?.let { title -> UiText(atom = title, onAction = onAction) }
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                items(items = model.chips, key = { chip -> chip.id }) { chip ->
                    val isSelected = chip.id in effectiveSelectedIds
                    FilterChipPill(
                        chip = chip,
                        isSelected = isSelected,
                        onClick = {
                            val nextSelection =
                                if (model.multiSelect) {
                                    if (isSelected) effectiveSelectedIds - chip.id else effectiveSelectedIds + chip.id
                                } else {
                                    setOf(chip.id)
                                }
                            localSelectedIds = nextSelection
                            onFormValueChange(formKey, nextSelection.sorted().joinToString(","))
                            haptic(UiHapticKind.TICK)
                            chip.onClick?.let(onAction)
                        },
                        onAction = onAction,
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun FilterChipPill(
        chip: FilterChipItem,
        isSelected: Boolean,
        onClick: () -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography
        val shape = RoundedCornerShape(ThemeTokens.cornerRadius.full)
        val containerColor = if (isSelected) colors.brand.primary else colors.surface.elevated
        val contentColor = if (isSelected) colors.brand.onPrimary else colors.text.primary
        val borderColor = if (isSelected) colors.brand.primary else colors.border.default

        Row(
            modifier =
                Modifier
                    .heightIn(min = CHIP_MIN_HEIGHT_DP.dp)
                    .clip(shape)
                    .background(containerColor, shape)
                    .border(ThemeTokens.strokeWidth.thin, borderColor, shape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { selected = isSelected }
                    .padding(horizontal = spacing.space12, vertical = spacing.space8)
                    .testTag("chip_${chip.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            chip.icon?.let { icon ->
                val iconColor = icon.color ?: ColorTokenRef(if (isSelected) "icon.on_brand" else "icon.secondary")
                UiIcon(
                    atom = icon.copy(sizeDp = icon.sizeDp ?: CHIP_ICON_SIZE_DP, color = iconColor, onClick = null),
                    onAction = onAction,
                )
            }
            Text(text = chip.label, style = typography.labelLarge, color = contentColor, maxLines = 1)
            chip.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                val badgeBg =
                    if (isSelected) {
                        colors.brand.onPrimary.copy(alpha = SELECTED_BADGE_BG_ALPHA)
                    } else {
                        colors.tag.neutralBackground
                    }
                val badgeFg = if (isSelected) colors.brand.onPrimary else colors.tag.neutralForeground
                Box(
                    modifier =
                        Modifier
                            .clip(
                                shape,
                            ).background(badgeBg, shape)
                            .padding(horizontal = spacing.space8, vertical = spacing.space2),
                ) {
                    Text(text = badge, style = typography.labelSmall, color = badgeFg, maxLines = 1)
                }
            }
        }
    }
}
