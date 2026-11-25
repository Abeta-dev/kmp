package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.PaginationSpec
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val sampleAuthorAvatar =
    AvatarAtom(
        id = "avatar_card_author",
        url = "https://cdn.abeta.dev/avatars/sr.png",
        initials = "SR",
        sizeDp = 36,
        bgColor = ColorTokenRef(token = "status.success_container"),
        textColor = ColorTokenRef(token = "status.success"),
        contentDescription = "Sam Rivera",
    )

private val savePrefAction =
    ActionRef(
        id = "save_container_pref",
        type = ActionTypes.SAVE_STATE,
        payload =
            buildJsonObject {
                put("key", "compact_containers")
                put("value", true)
                put("scope", "session")
            },
    )

private val submitControlsAction =
    ActionRef(
        id = "submit_container_controls",
        type = ActionTypes.CALL_API,
        payload =
            buildJsonObject {
                put("endpoint", "/api/v1/actions/execute")
                put("method", "POST")
                put("body", buildJsonObject { put("form_id", "container_preferences") })
                put(
                    "on_success",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "toast_controls_saved",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "Container preferences updated")
                                    put("duration_ms", 2000)
                                    put("variant", "success")
                                },
                        ),
                    ),
                )
            },
    )

private val loadMoreContainersAction =
    ActionRef(
        id = "load_more_containers",
        type = ActionTypes.CALL_API,
        payload =
            buildJsonObject {
                put("endpoint", "/api/v1/screens/containers?cursor=cursor_page_2")
                put("method", "GET")
                put(
                    "on_success",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "toast_containers_loaded",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "Loaded next page of containers")
                                    put("variant", "info")
                                },
                        ),
                    ),
                )
            },
    )

/**
 * Builds the `"containers"` SDUI sample screen exercising container snippets, rich form atoms,
 * bottom-sheet overlays, pagination, and pull-to-refresh.
 */
@Suppress("MagicNumber")
internal fun SampleScreens.containers(): PageResponse {
    val bottomSheetAction = createBottomSheetAction()
    return PageResponse(
        pageId = "containers",
        title = "Container Showcase",
        toolbar =
            ToolbarSpec(
                title = text("Container Showcase", "title.large"),
                navigationIcon =
                    IconAtom(
                        name = "arrow_back",
                        sizeDp = 22,
                        color = ColorTokenRef(token = "text.primary"),
                        contentDescription = "Navigate back",
                        onClick = backAction("back_from_containers"),
                    ),
                bgColor = ColorTokenRef(token = "surface.primary"),
            ),
        snippets =
            listOf(
                buildContainersHeader(bottomSheetAction),
                buildContainersCarousel(),
                buildContainersGrid(),
                buildContainersTabs(bottomSheetAction),
                buildContainersAccordion(bottomSheetAction),
                buildContainersSkeletonCard(),
                buildContainersForm(),
            ),
        pagination =
            PaginationSpec(
                nextCursor = "cursor_page_2",
                hasMore = true,
                loadMoreAction = loadMoreContainersAction,
            ),
        pullToRefresh =
            ActionRef(
                id = "pull_refresh_containers",
                type = ActionTypes.REFRESH_PAGE,
                payload =
                    buildJsonObject {
                        put("page_id", "containers")
                        put("show_loader", true)
                    },
            ),
        tracking =
            TrackingContract(
                eventName = "page_loaded",
                properties = mapOf("page_id" to "containers"),
            ),
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersHeader(bottomSheetAction: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "containers_header",
        snippetType = SnippetTypes.HEADER,
        data =
            encode(
                HeaderSnippetData(
                    title = text("SDUI Layout Containers", "headline.small"),
                    subtitle =
                        text(
                            "Carousel, Grid, Tabs, Accordion, and rich form atoms",
                            "body.medium",
                            "text.secondary",
                        ),
                    avatar =
                        AvatarAtom(
                            id = "avatar_header_lead",
                            initials = "AC",
                            sizeDp = 44,
                            bgColor = ColorTokenRef(token = "brand.container"),
                            textColor = ColorTokenRef(token = "text.brand"),
                            contentDescription = "Abeta Core Team",
                        ),
                    trailingTag =
                        TagAtom(
                            label = "V2 WIRE",
                            bgColor = ColorTokenRef(token = "status.info_container"),
                        ),
                    trailingAction =
                        ButtonAtom(
                            id = "btn_open_sheet",
                            label = "Options",
                            variant = ButtonVariant.OUTLINE,
                            size = ButtonSize.SMALL,
                            onClick = bottomSheetAction,
                        ),
                    layout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 12), fillMaxWidth = true),
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersCarousel(): SnippetEnvelope {
    val cardItem =
        SnippetEnvelope(
            snippetId = "car_card_1",
            snippetParentId = "containers_carousel",
            snippetType = SnippetTypes.CARD,
            data =
                encode(
                    CardSnippetData(
                        title = text("Carousel Card One", "title.medium"),
                        subtitle = text("Adaptive layout", "label.medium", "text.brand"),
                        avatar = sampleAuthorAvatar,
                        badge = TagAtom(label = "NEW", bgColor = ColorTokenRef(token = "brand.container")),
                        layout = cardLayout,
                    ),
                ),
        )
    val metricItem =
        SnippetEnvelope(
            snippetId = "car_metric_2",
            snippetParentId = "containers_carousel",
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                encode(
                    MetricCardSnippetData(
                        label = text("Render P95", "label.medium", "text.secondary"),
                        value = text("11.4 ms", "headline.small"),
                        delta = TagAtom(label = "-18%", bgColor = ColorTokenRef(token = "status.success_container")),
                        layout = cardLayout,
                    ),
                ),
        )
    return SnippetEnvelope(
        snippetId = "containers_carousel",
        snippetType = SnippetTypes.CAROUSEL,
        data =
            encode(
                CarouselSnippetData(
                    title = text("Featured Carousel", "title.medium"),
                    items = listOf(cardItem, metricItem),
                    itemWidthDp = 240,
                    spacingDp = 12,
                    layout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 8), fillMaxWidth = true),
                ),
            ),
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersGrid(): SnippetEnvelope {
    val gridItem1 =
        SnippetEnvelope(
            snippetId = "grid_metric_1",
            snippetParentId = "containers_grid",
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                encode(
                    MetricCardSnippetData(
                        label = text("Active Snippets", "label.medium", "text.secondary"),
                        value = text("23", "headline.medium"),
                        layout = cardLayout,
                    ),
                ),
        )
    val gridItem2 =
        SnippetEnvelope(
            snippetId = "grid_metric_2",
            snippetParentId = "containers_grid",
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                encode(
                    MetricCardSnippetData(
                        label = text("Atoms Ready", "label.medium", "text.secondary"),
                        value = text("31", "headline.medium"),
                        layout = cardLayout,
                    ),
                ),
        )
    return SnippetEnvelope(
        snippetId = "containers_grid",
        snippetType = SnippetTypes.GRID,
        data =
            encode(
                GridSnippetData(
                    title = text("Telemetry Grid", "title.medium"),
                    columns = 2,
                    spacingDp = 12,
                    items = listOf(gridItem1, gridItem2),
                    layout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 8), fillMaxWidth = true),
                ),
            ),
    )
}

private val tabSkeletonCard =
    SnippetEnvelope(
        snippetId = "tab_skeleton_card",
        snippetParentId = "containers_tabs",
        snippetType = SnippetTypes.CARD,
        data =
            SampleScreens.encode(
                CardSnippetData(
                    title = SampleScreens.text("Skeleton Loading Preview", "title.medium"),
                    description =
                        SampleScreens.text(
                            "Shimmer placeholders rendered using pure SkeletonAtom contracts.",
                            "body.small",
                            "text.secondary",
                        ),
                    skeletons =
                        listOf(
                            SkeletonAtom(kind = SkeletonKind.CIRCLE, widthDp = 40, heightDp = 40),
                            SkeletonAtom(kind = SkeletonKind.TEXT, widthDp = 180, heightDp = 14),
                            SkeletonAtom(kind = SkeletonKind.RECTANGLE, widthDp = 280, heightDp = 96),
                        ),
                    layout = SampleScreens.cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersTabs(bottomSheetAction: ActionRef): SnippetEnvelope {
    val overviewCard =
        SnippetEnvelope(
            snippetId = "tab_overview_card",
            snippetParentId = "containers_tabs",
            snippetType = SnippetTypes.CARD,
            data =
                encode(
                    CardSnippetData(
                        title = text("Composable Containers", "title.medium"),
                        description =
                            text(
                                "Nest any SnippetEnvelope inside Carousel, Grid, Tabs, or Accordion.",
                                "body.small",
                                "text.secondary",
                            ),
                        avatar = sampleAuthorAvatar,
                        primaryButton =
                            ButtonAtom(
                                id = "btn_tab_sheet",
                                label = "Open Sheet",
                                variant = ButtonVariant.PRIMARY,
                                onClick = bottomSheetAction,
                            ),
                        layout = cardLayout,
                    ),
                ),
        )
    return SnippetEnvelope(
        snippetId = "containers_tabs",
        snippetType = SnippetTypes.TABS,
        data =
            encode(
                TabsSnippetData(
                    tabs =
                        listOf(
                            TabItemSpec(
                                id = "overview",
                                title = "Overview",
                                badge = TagAtom(label = "LIVE", bgColor = ColorTokenRef("status.success_container")),
                                snippets = listOf(overviewCard),
                            ),
                            TabItemSpec(id = "loading", title = "Skeletons", snippets = listOf(tabSkeletonCard)),
                        ),
                    selectedIndex = 0,
                    layout = cardLayout,
                ),
            ),
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersAccordion(bottomSheetAction: ActionRef): SnippetEnvelope {
    val actionRow =
        SnippetEnvelope(
            snippetId = "acc_row_sheet",
            snippetParentId = "containers_accordion",
            snippetType = SnippetTypes.ACTION_ROW,
            data =
                encode(
                    ActionRowSnippetData(
                        title = text("Preview Bottom Sheet", "title.small"),
                        subtitle =
                            text(
                                "Opens an overlay with presentation=bottom_sheet",
                                "body.small",
                                "text.secondary",
                            ),
                        leadingIcon = IconAtom(name = "layers", sizeDp = 22, color = ColorTokenRef("brand.primary")),
                        trailingIcon =
                            IconAtom(name = "chevron_right", sizeDp = 20, color = ColorTokenRef("text.tertiary")),
                        onClick = bottomSheetAction,
                        layout = cardLayout,
                    ),
                ),
        )
    return SnippetEnvelope(
        snippetId = "containers_accordion",
        snippetType = SnippetTypes.ACCORDION,
        data =
            encode(
                AccordionSnippetData(
                    title = text("Overlay & Sheet Triggers", "title.medium"),
                    subtitle = text("Expand to inspect bottom sheet action triggers", "body.small", "text.secondary"),
                    initiallyExpanded = true,
                    snippets = listOf(actionRow),
                    layout = cardLayout,
                ),
            ),
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildContainersSkeletonCard(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "containers_skeleton_card",
        snippetType = SnippetTypes.CARD,
        data =
            encode(
                CardSnippetData(
                    title = text("Async Feed Placeholder", "title.medium"),
                    subtitle = text("Avatar & Skeleton atoms", "label.medium", "text.secondary"),
                    avatar = sampleAuthorAvatar,
                    skeletons =
                        listOf(
                            SkeletonAtom(kind = SkeletonKind.CIRCLE, widthDp = 36, heightDp = 36),
                            SkeletonAtom(kind = SkeletonKind.TEXT, widthDp = 220, heightDp = 16),
                            SkeletonAtom(kind = SkeletonKind.RECTANGLE, widthDp = 300, heightDp = 110),
                        ),
                    layout = cardLayout,
                ),
            ),
    )

private val containersFormControls =
    FormSnippetData(
        formId = "container_preferences",
        title = SampleScreens.text("Interactive Atom Controls", "title.medium"),
        description =
            SampleScreens.text("Switch, Checkbox, and Slider atoms inside form_v1.", "body.small", "text.secondary"),
        fields =
            listOf(
                TextFieldAtom(
                    fieldId = "preset_name",
                    label = "Preset Name",
                    placeholder = "Compact Dashboard",
                    keyboardType = KeyboardKind.TEXT,
                ),
            ),
        switches =
            listOf(
                SwitchAtom(
                    fieldId = "compact_mode",
                    label = "Compact Container Spacing",
                    subtitle = "Reduces inner card padding to 8dp",
                    checked = true,
                    onChange = savePrefAction,
                ),
            ),
        checkboxes =
            listOf(
                CheckboxAtom(
                    fieldId = "confirm_schema",
                    label = "Enable Strict Schema Mode",
                    subtitle = "Reject unrecognized snippet envelopes in preview",
                    checked = false,
                    required = true,
                    onChange = savePrefAction,
                ),
            ),
        sliders =
            listOf(
                SliderAtom(
                    fieldId = "grid_density",
                    label = "Grid Density",
                    value = 2f,
                    minValue = 1f,
                    maxValue = 4f,
                    steps = 3,
                    showValue = true,
                    unitLabel = "cols",
                    onChange = savePrefAction,
                ),
            ),
        submitButton =
            ButtonAtom(
                id = "btn_save_controls",
                label = "Save Preferences",
                variant = ButtonVariant.PRIMARY,
                onClick = submitControlsAction,
            ),
        layout = SampleScreens.cardLayout,
    )

private fun SampleScreens.buildContainersForm(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "containers_controls_form",
        snippetType = SnippetTypes.FORM,
        data = encode(containersFormControls),
    )

private fun createBottomSheetAction(): ActionRef =
    ActionRef(
        id = "open_containers_sheet",
        type = ActionTypes.OPEN_DIALOG,
        payload =
            buildJsonObject {
                put("dialog_id", "container_options_sheet")
                put("title", "Container Layout Options")
                put("message", "Configure responsive columns, spacing, and loading placeholders.")
                put("presentation", "bottom_sheet")
                put("dismissible", true)
                put(
                    "positive_action",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "dismiss_containers_sheet",
                            type = ActionTypes.DISMISS_OVERLAY,
                            payload = buildJsonObject { put("overlay_id", "container_options_sheet") },
                        ),
                    ),
                )
            },
    )
