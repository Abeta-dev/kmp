package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.DividerSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("MagicNumber")
internal fun SampleScreens.buildCatalogSamplePage(): PageResponse {
    val inspectApi = buildCatalogInspectApiAction()
    val saveBookmark = buildCatalogSaveBookmarkAction()
    val deleteBookmark = buildCatalogDeleteBookmarkAction()
    val patchSnippet = buildCatalogPatchSnippetAction()

    return PageResponse(
        pageId = "catalog",
        title = "Component Catalog",
        toolbar =
            ToolbarSpec(
                title = text("Component Catalog", "title.large"),
                navigationIcon =
                    IconAtom(
                        name = "arrow_back",
                        sizeDp = 22,
                        color = ColorTokenRef(token = "text.primary"),
                        contentDescription = "Navigate back",
                        onClick = backAction("back_from_catalog"),
                    ),
                bgColor = ColorTokenRef(token = "surface.primary"),
            ),
        snippets =
            listOf(
                buildCatalogHeaderSnippet(),
                buildCatalogMetricSnippet(),
                buildCatalogAtomsCardSnippet(inspectApi, saveBookmark),
                buildCatalogEmptyPreviewSnippet(patchSnippet),
                SnippetEnvelope(
                    snippetId = "catalog_div_1",
                    snippetType = SnippetTypes.DIVIDER,
                    data =
                        encode(
                            DividerSnippetData(
                                divider =
                                    DividerAtom(
                                        thicknessDp = 1f,
                                        color = ColorTokenRef(token = "divider.default"),
                                    ),
                            ),
                        ),
                ),
                buildCatalogResetRowSnippet(deleteBookmark),
            ),
    )
}

private fun buildCatalogInspectApiAction(): ActionRef =
    ActionRef(
        id = "call_catalog_tokens",
        type = ActionTypes.CALL_API,
        payload =
            buildJsonObject {
                put("endpoint", "/api/v1/catalog/tokens")
                put("method", "GET")
                put(
                    "on_success",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "toast_catalog_ok",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "Token catalog loaded")
                                    put("variant", "success")
                                },
                        ),
                    ),
                )
            },
    )

private fun buildCatalogSaveBookmarkAction(): ActionRef =
    ActionRef(
        id = "save_catalog_bookmark",
        type = ActionTypes.SAVE_STATE,
        payload =
            buildJsonObject {
                put("key", "bookmarked_catalog")
                put("value", true)
                put("scope", "disk")
            },
        secondaryActions =
            listOf(
                ActionRef(
                    id = "toast_bookmarked",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Catalog bookmarked to session state")
                            put("variant", "info")
                        },
                ),
            ),
    )

private fun buildCatalogDeleteBookmarkAction(): ActionRef =
    ActionRef(
        id = "delete_catalog_bookmark",
        type = ActionTypes.DELETE_STATE,
        payload =
            buildJsonObject {
                put("key", "bookmarked_catalog")
                put("scope", "disk")
            },
        secondaryActions =
            listOf(
                ActionRef(
                    id = "toast_bookmark_cleared",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Cached bookmark cleared")
                            put("variant", "info")
                        },
                ),
            ),
    )

private fun SampleScreens.buildCatalogPatchSnippetAction(): ActionRef {
    val appendedPreview =
        SnippetEnvelope(
            snippetId = "catalog_dynamic_banner",
            snippetType = SnippetTypes.BANNER,
            data =
                encode(
                    BannerSnippetData(
                        title = text("Dynamic Snippet Inserted", "title.small"),
                        body =
                            text(
                                "Added in-place via update_snippets without reloading the screen.",
                                "body.small",
                                "text.secondary",
                            ),
                        layout = cardLayout.copy(bgColor = ColorTokenRef(token = "brand.container")),
                    ),
                ),
        )
    return ActionRef(
        id = "patch_catalog_preview",
        type = ActionTypes.UPDATE_SNIPPETS,
        payload =
            buildJsonObject {
                put("operation", "append")
                put("target_snippet_id", "catalog_empty_preview")
                put(
                    "snippets",
                    buildJsonArray {
                        add(SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), appendedPreview))
                    },
                )
            },
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildCatalogHeaderSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "catalog_header",
        snippetType = SnippetTypes.HEADER,
        data =
            encode(
                HeaderSnippetData(
                    title = text("Token & Snippet Showcase", "headline.small"),
                    subtitle =
                        text(
                            "Live contracts served from sdui-contracts",
                            "body.medium",
                            "text.secondary",
                        ),
                    trailingTag =
                        TagAtom(
                            label = "23 SNIPPETS",
                            bgColor = ColorTokenRef(token = "brand.container"),
                        ),
                    layout =
                        LayoutConfig(
                            padding = SpacingValues(horizontal = 16, vertical = 12),
                            fillMaxWidth = true,
                        ),
                ),
            ),
    )

private fun SampleScreens.buildCatalogMetricSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "catalog_metric_tokens",
        snippetType = SnippetTypes.METRIC_CARD,
        data =
            encode(
                MetricCardSnippetData(
                    label = text("Registered Color Tokens", "label.medium", "text.secondary"),
                    value = text("30", "headline.medium"),
                    delta =
                        TagAtom(
                            label = "100% synced",
                            bgColor = ColorTokenRef(token = "status.success_container"),
                        ),
                    progress =
                        ProgressAtom(
                            kind = ProgressKind.LINEAR,
                            progress = 1.0f,
                            color = ColorTokenRef(token = "brand.primary"),
                        ),
                    layout = cardLayout,
                ),
            ),
    )

private fun SampleScreens.buildCatalogAtomsCardSnippet(
    inspectApi: ActionRef,
    saveBookmark: ActionRef,
): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "catalog_card_atoms",
        snippetType = SnippetTypes.CARD,
        data =
            encode(
                CardSnippetData(
                    title = text("Atomic Primitives", "title.medium"),
                    description =
                        text(
                            "31 stateless UI atoms across Text, Icon, Tag, Button, Inputs, Commerce, " +
                                "Telemetry & MediaPager.",
                            "body.medium",
                            "text.secondary",
                        ),
                    primaryButton =
                        ButtonAtom(
                            id = "btn_inspect_tokens",
                            label = "Inspect Tokens",
                            variant = ButtonVariant.PRIMARY,
                            onClick = inspectApi,
                        ),
                    secondaryButton =
                        ButtonAtom(
                            id = "btn_save_bookmark",
                            label = "Bookmark",
                            variant = ButtonVariant.OUTLINE,
                            onClick = saveBookmark,
                        ),
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildCatalogEmptyPreviewSnippet(patchSnippet: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "catalog_empty_preview",
        snippetType = SnippetTypes.EMPTY_STATE,
        data =
            encode(
                EmptyStateSnippetData(
                    title = text("No Custom Overrides Yet", "title.medium"),
                    message =
                        text(
                            "Patch this section dynamically with update_snippets " +
                                "to preview custom overrides.",
                            "body.small",
                            "text.secondary",
                        ),
                    icon =
                        IconAtom(
                            name = "add",
                            sizeDp = 32,
                            color = ColorTokenRef(token = "text.tertiary"),
                        ),
                    actionButton =
                        ButtonAtom(
                            id = "btn_patch_preview",
                            label = "Patch Preview",
                            variant = ButtonVariant.SECONDARY,
                            onClick = patchSnippet,
                        ),
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildCatalogResetRowSnippet(deleteBookmark: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "catalog_row_clear",
        snippetType = SnippetTypes.ACTION_ROW,
        data =
            encode(
                ActionRowSnippetData(
                    title = text("Reset Cached Preferences", "title.small", "text.error"),
                    subtitle =
                        text(
                            "Clears bookmarked_catalog from local state",
                            "body.small",
                            "text.secondary",
                        ),
                    leadingIcon =
                        IconAtom(
                            name = "delete",
                            sizeDp = 22,
                            color = ColorTokenRef(token = "status.error"),
                        ),
                    onClick = deleteBookmark,
                    layout = cardLayout,
                ),
            ),
    )
