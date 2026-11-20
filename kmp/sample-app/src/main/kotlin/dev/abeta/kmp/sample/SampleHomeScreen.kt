package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.DividerSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.SpacerSnippetData
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private data class HomeScreenActions(
    val navCatalog: ActionRef,
    val navProfile: ActionRef,
    val navForm: ActionRef,
    val refresh: ActionRef,
    val infoDialog: ActionRef,
    val copyApi: ActionRef,
    val refreshBatch: ActionRef,
)

@Suppress("MagicNumber")
internal fun SampleScreens.buildHomeSamplePage(): PageResponse {
    val actions = buildHomeActions()
    return PageResponse(
        pageId = "home",
        title = "Abeta Workspace",
        toolbar = buildHomeToolbar(actions.refresh, actions.infoDialog),
        snippets =
            listOf(
                buildHomeHeaderSnippet(actions.navCatalog),
                buildHomeBannerSnippet(actions.navForm),
                buildHomeUptimeMetricSnippet(),
                SnippetEnvelope(
                    snippetId = "home_div_1",
                    snippetType = SnippetTypes.DIVIDER,
                    data =
                        encode(
                            DividerSnippetData(
                                divider =
                                    DividerAtom(
                                        thicknessDp = 1f,
                                        color = ColorTokenRef(token = "divider.default"),
                                        insetStartDp = 16,
                                    ),
                            ),
                        ),
                ),
                buildHomeFeaturedCardSnippet(actions.navCatalog, actions.copyApi),
                SnippetEnvelope(
                    snippetId = "home_spacer_1",
                    snippetType = SnippetTypes.SPACER,
                    data = encode(SpacerSnippetData(spacer = SpacerAtom(heightDp = 12))),
                ),
            ) + buildHomeNavigationRows(actions.navProfile, actions.navForm),
        bottomBar = buildHomeBottomBar(actions.refreshBatch),
        pageActions =
            listOf(
                ActionRef(
                    id = "track_home_view",
                    type = ActionTypes.TRACK_EVENT,
                    payload =
                        buildJsonObject {
                            put("event_name", "screen_viewed")
                            put("properties", buildJsonObject { put("screen_id", "home") })
                        },
                ),
            ),
        tracking =
            TrackingContract(
                eventName = "page_loaded",
                properties = mapOf("page_id" to "home"),
            ),
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeActions(): HomeScreenActions {
    val refreshAct =
        ActionRef(
            id = "refresh_home",
            type = ActionTypes.REFRESH_PAGE,
            payload =
                buildJsonObject {
                    put("page_id", "home")
                    put("show_loader", true)
                },
        )
    val copyApi =
        ActionRef(
            id = "copy_catalog_url",
            type = ActionTypes.COPY_TO_CLIPBOARD,
            payload =
                buildJsonObject {
                    put("text", "/api/v1/catalog/tokens")
                    put("label", "Catalog Endpoint")
                },
            secondaryActions =
                listOf(
                    ActionRef(
                        id = "toast_copied",
                        type = ActionTypes.SHOW_TOAST,
                        payload =
                            buildJsonObject {
                                put("message", "Endpoint copied to clipboard")
                                put("duration_ms", 2000)
                                put("variant", "success")
                            },
                    ),
                ),
        )
    return HomeScreenActions(
        navCatalog = navAction("nav_catalog", "/screens/catalog"),
        navProfile = navAction("nav_profile", "/screens/profile"),
        navForm = navAction("nav_form_demo", "/screens/form-demo"),
        refresh = refreshAct,
        infoDialog = buildHomeInfoDialogAction(),
        copyApi = copyApi,
        refreshBatch = buildHomeRefreshBatchAction(refreshAct),
    )
}

private fun buildHomeInfoDialogAction(): ActionRef =
    ActionRef(
        id = "open_home_info",
        type = ActionTypes.OPEN_DIALOG,
        payload =
            buildJsonObject {
                put("dialog_id", "home_info_dialog")
                put("title", "Abeta SDUI Engine")
                put("message", "Screens are streamed via atomic contracts and rendered natively on KMP.")
                put("dismissible", true)
                put(
                    "positive_action",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "dismiss_info",
                            type = ActionTypes.DISMISS_OVERLAY,
                            payload = buildJsonObject { put("overlay_id", "home_info_dialog") },
                        ),
                    ),
                )
            },
    )

private fun buildHomeRefreshBatchAction(refreshAct: ActionRef): ActionRef =
    ActionRef(
        id = "batch_refresh_home",
        type = ActionTypes.BATCH,
        payload =
            buildJsonObject {
                put("stop_on_error", true)
                put(
                    "actions",
                    buildJsonArray {
                        add(
                            SduiJson.encodeToJsonElement(
                                ActionRef.serializer(),
                                ActionRef(
                                    id = "track_home_refresh",
                                    type = ActionTypes.TRACK_EVENT,
                                    payload =
                                        buildJsonObject {
                                            put("event_name", "home_refresh_clicked")
                                            put("properties", buildJsonObject { put("source", "bottom_bar") })
                                        },
                                ),
                            ),
                        )
                        add(SduiJson.encodeToJsonElement(ActionRef.serializer(), refreshAct))
                    },
                )
            },
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeToolbar(
    refreshAct: ActionRef,
    infoDialog: ActionRef,
): ToolbarSpec =
    ToolbarSpec(
        title = text("Abeta Workspace", "title.large"),
        subtitle = text("SDUI Control Center", "body.small", "text.secondary"),
        trailingActions =
            listOf(
                IconAtom(
                    name = "refresh",
                    sizeDp = 22,
                    color = ColorTokenRef(token = "text.primary"),
                    contentDescription = "Refresh screen",
                    onClick = refreshAct,
                ),
                IconAtom(
                    name = "info",
                    sizeDp = 22,
                    color = ColorTokenRef(token = "text.secondary"),
                    contentDescription = "About SDUI",
                    onClick = infoDialog,
                ),
            ),
        bgColor = ColorTokenRef(token = "surface.primary"),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeHeaderSnippet(navCatalog: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "home_header",
        snippetType = SnippetTypes.HEADER,
        data =
            encode(
                HeaderSnippetData(
                    title = text("Welcome back, Builder", "headline.small"),
                    subtitle =
                        text(
                            "Server-driven UI powered by atomic contracts",
                            "body.medium",
                            "text.secondary",
                        ),
                    leadingIcon =
                        IconAtom(
                            name = "star",
                            sizeDp = 24,
                            color = ColorTokenRef(token = "brand.primary"),
                        ),
                    trailingTag =
                        TagAtom(
                            label = "LIVE",
                            bgColor = ColorTokenRef(token = "status.success_container"),
                        ),
                    trailingAction =
                        ButtonAtom(
                            id = "btn_home_catalog",
                            label = "Catalog",
                            variant = ButtonVariant.OUTLINE,
                            size = ButtonSize.SMALL,
                            onClick = navCatalog,
                        ),
                    layout =
                        LayoutConfig(
                            padding = SpacingValues(horizontal = 16, vertical = 12),
                            fillMaxWidth = true,
                        ),
                ),
            ),
        tracking =
            TrackingContract(
                eventName = "snippet_impression",
                properties = mapOf("snippet_id" to "home_header"),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeBannerSnippet(navForm: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "home_promo_banner",
        snippetType = SnippetTypes.BANNER,
        data =
            encode(
                BannerSnippetData(
                    title = text("Zero-Release UI Updates", "title.medium"),
                    body =
                        text(
                            "Ship layout, theming, and action workflows " +
                                "across Android, iOS, and Desktop.",
                            "body.small",
                            "text.secondary",
                        ),
                    icon =
                        IconAtom(
                            name = "notifications",
                            sizeDp = 24,
                            color = ColorTokenRef(token = "brand.primary"),
                        ),
                    tag = TagAtom(label = "v1.0", bgColor = ColorTokenRef(token = "surface.primary")),
                    cta =
                        ButtonAtom(
                            id = "btn_open_form",
                            label = "Open Form Demo",
                            variant = ButtonVariant.PRIMARY,
                            size = ButtonSize.SMALL,
                            onClick = navForm,
                        ),
                    layout = cardLayout.copy(bgColor = ColorTokenRef(token = "brand.container")),
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeUptimeMetricSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "home_metric_uptime",
        snippetType = SnippetTypes.METRIC_CARD,
        data =
            encode(
                MetricCardSnippetData(
                    label = text("API Availability", "label.medium", "text.secondary"),
                    value = text("99.98%", "headline.medium"),
                    delta =
                        TagAtom(
                            label = "+0.04%",
                            bgColor = ColorTokenRef(token = "status.success_container"),
                        ),
                    icon =
                        IconAtom(
                            name = "check",
                            sizeDp = 20,
                            color = ColorTokenRef(token = "status.success"),
                        ),
                    progress =
                        ProgressAtom(
                            kind = ProgressKind.LINEAR,
                            progress = 0.9998f,
                            label = "30-day SLO target",
                            color = ColorTokenRef(token = "status.success"),
                        ),
                    onClick =
                        ActionRef(
                            id = "toast_uptime",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "All BFF services healthy (99.98% SLA)")
                                    put("duration_ms", 2500)
                                    put("variant", "info")
                                },
                        ),
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeFeaturedCardSnippet(
    navCatalog: ActionRef,
    copyApi: ActionRef,
): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "home_featured_card",
        snippetType = SnippetTypes.CARD,
        data =
            encode(
                CardSnippetData(
                    title = text("Atomic Design System", "title.large"),
                    subtitle = text("Tokens · Atoms · Snippets", "label.medium", "text.brand"),
                    description =
                        text(
                            "Consistent semantic theming and strict action " +
                                "decoupling across every platform.",
                            "body.medium",
                            "text.secondary",
                        ),
                    media =
                        ImageAtom(
                            url = "https://cdn.abeta.dev/sdui/atomic-system-preview.png",
                            contentDescription = "Atomic design architecture diagram",
                            heightDp = 120,
                            scaleMode = ImageScaleMode.CROP,
                            cornerRadius = CornerRadiusSpec(all = 8),
                        ),
                    badge =
                        TagAtom(
                            label = "STABLE",
                            bgColor = ColorTokenRef(token = "status.info_container"),
                        ),
                    primaryButton =
                        ButtonAtom(
                            id = "btn_explore_tokens",
                            label = "Explore Tokens",
                            variant = ButtonVariant.PRIMARY,
                            onClick = navCatalog,
                        ),
                    secondaryButton =
                        ButtonAtom(
                            id = "btn_copy_endpoint",
                            label = "Copy Endpoint",
                            variant = ButtonVariant.SECONDARY,
                            onClick = copyApi,
                        ),
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeNavigationRows(
    navProfile: ActionRef,
    navForm: ActionRef,
): List<SnippetEnvelope> =
    listOf(
        SnippetEnvelope(
            snippetId = "home_row_profile",
            snippetType = SnippetTypes.ACTION_ROW,
            data =
                encode(
                    ActionRowSnippetData(
                        title = text("Account & Preferences", "title.small"),
                        subtitle = text("Manage workspace profile and synced state", "body.small", "text.secondary"),
                        leadingIcon = IconAtom(name = "person", sizeDp = 22, color = ColorTokenRef("brand.primary")),
                        trailingTag = TagAtom(label = "PRO", bgColor = ColorTokenRef("brand.container")),
                        trailingIcon =
                            IconAtom(name = "chevron_right", sizeDp = 20, color = ColorTokenRef("text.tertiary")),
                        onClick = navProfile,
                        layout = cardLayout,
                    ),
                ),
        ),
        SnippetEnvelope(
            snippetId = "home_row_form",
            snippetType = SnippetTypes.ACTION_ROW,
            data =
                encode(
                    ActionRowSnippetData(
                        title = text("Interactive Onboarding Form", "title.small"),
                        subtitle =
                            text("Test declarative client and server validations", "body.small", "text.secondary"),
                        leadingIcon = IconAtom(name = "edit", sizeDp = 22, color = ColorTokenRef("accent.primary")),
                        trailingIcon =
                            IconAtom(name = "chevron_right", sizeDp = 20, color = ColorTokenRef("text.tertiary")),
                        onClick = navForm,
                        layout = cardLayout,
                    ),
                ),
        ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildHomeBottomBar(refreshBatch: ActionRef): BottomBarSpec =
    BottomBarSpec(
        primaryButton =
            ButtonAtom(
                id = "btn_bottom_refresh",
                label = "Refresh Dashboard",
                variant = ButtonVariant.PRIMARY,
                onClick = refreshBatch,
            ),
        helperText = text("Synced via Abeta BFF", "caption", "text.tertiary"),
        layout =
            LayoutConfig(
                padding = SpacingValues(all = 12),
                bgColor = ColorTokenRef(token = "surface.primary"),
                fillMaxWidth = true,
            ),
    )
