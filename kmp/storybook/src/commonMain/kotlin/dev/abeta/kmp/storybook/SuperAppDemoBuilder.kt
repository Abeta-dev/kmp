package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.AdaptivePageSpec
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.ResponsiveIntSpec
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

internal data class DemoListingSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val details: String,
    val badge: Pair<String, String>,
    val ctaLabel: String,
    val route: String,
    val filterTag: String = "all",
)

internal data class DemoSkuSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val cents: Int,
    val badge: Pair<String, String>,
    val qty: Int = 0,
    val route: String? = null,
    val category: String = "all",
)

/**
 * Pure Kotlin Multiplatform SDUI builder helpers powering the live 7-in-1 Super-App Demos
 * across WasmJS Browser, Desktop JVM, macOS, and iOS.
 */
@Suppress("MagicNumber", "TooManyFunctions", "LongParameterList")
internal object SuperAppDemoBuilder {
    val centeredAdaptive = AdaptivePageSpec(layoutMode = ScreenLayoutMode.CENTERED, maxWidthDp = 1120)
    val horizontalLayout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 8), fillMaxWidth = true)
    val chipRowLayout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 4), fillMaxWidth = true)
    val fullWidthLayout = LayoutConfig(fillMaxWidth = true)
    val cardLayout = boxLayout("surface.primary")
    val bannerLayout = boxLayout("brand.container")
    val searchBarLayout = boxLayout("surface.primary", "border.default", SpacingValues(horizontal = 12, vertical = 6))
    val compactCardLayout =
        LayoutConfig(
            padding = SpacingValues(all = 12),
            bgColor = ColorTokenRef("surface.primary"),
            cornerRadius = CornerRadiusSpec(all = 12),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef("border.subtle")),
            fillMaxWidth = true,
        )
    val marqueeLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 12, vertical = 8),
            margin = SpacingValues(horizontal = 16, vertical = 4),
            bgColor = ColorTokenRef("surface.secondary"),
            cornerRadius = CornerRadiusSpec(all = 12),
            fillMaxWidth = true,
        )

    private fun boxLayout(
        bgToken: String,
        borderToken: String = "border.subtle",
        padding: SpacingValues = SpacingValues(all = 16),
    ): LayoutConfig =
        LayoutConfig(
            padding = padding,
            margin = SpacingValues(horizontal = 16, vertical = 4),
            bgColor = ColorTokenRef(bgToken),
            cornerRadius = CornerRadiusSpec(all = 12),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef(borderToken)),
            fillMaxWidth = true,
        )

    inline fun <reified T> encode(value: T): JsonObject {
        val elem = SduiJson.encodeToJsonElement(serializer<T>(), value)
        return elem as JsonObject
    }

    fun formatUsd(cents: Int): String = "\$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    fun text(
        value: String,
        typography: String,
        color: String = "text.primary",
    ): TextAtom = TextAtom(text = value, color = ColorTokenRef(color), typography = TypographyTokenRef(typography))

    fun navAction(
        id: String,
        route: String,
    ): ActionRef = ActionRef(id = id, type = ActionTypes.NAVIGATE, payload = buildJsonObject { put("route", route) })

    fun backAction(id: String): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.BACK_PRESS,
            payload = buildJsonObject { put("steps", 1) },
        )

    fun toastAction(
        id: String,
        message: String,
        variant: String = "info",
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SHOW_TOAST,
            payload =
                buildJsonObject {
                    put("message", message)
                    put("duration_ms", 2200L)
                    put("variant", variant)
                },
        )

    fun apiCallAction(
        id: String,
        endpoint: String,
        body: JsonObject? = null,
        validations: List<ValidationRuleContract> = emptyList(),
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", endpoint)
                    put("method", "POST")
                    if (body != null) put("body", body)
                },
            validations = validations,
        )

    fun shareAction(
        id: String,
        title: String,
        text: String,
        url: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SHARE,
            payload =
                buildJsonObject {
                    put("title", title)
                    put("text", text)
                    put("url", url)
                },
        )

    fun celebrationAction(
        id: String,
        title: String,
        subtitle: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.TRIGGER_FEEDBACK,
            payload =
                buildJsonObject {
                    put("effect", "celebration")
                    put("title", title)
                    put("subtitle", subtitle)
                    put("duration_ms", 2200L)
                },
        )

    fun saveStateAction(
        id: String,
        key: String,
        value: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SAVE_STATE,
            payload =
                buildJsonObject {
                    put("key", key)
                    put("value", value)
                    put("scope", "session")
                },
        )

    fun biometricAuthAction(
        id: String,
        title: String,
        reason: String,
        onAuthenticated: ActionRef,
        onFailed: ActionRef,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.BIOMETRIC_AUTH,
            payload =
                buildJsonObject {
                    put("title", title)
                    put("reason", reason)
                    put("allow_device_credential", true)
                    put("on_authenticated", SduiJson.encodeToJsonElement(ActionRef.serializer(), onAuthenticated))
                    put("on_failed", SduiJson.encodeToJsonElement(ActionRef.serializer(), onFailed))
                },
        )

    fun batchAction(
        id: String,
        actions: List<ActionRef>,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.BATCH,
            payload =
                buildJsonObject {
                    put(
                        "actions",
                        SduiJson.encodeToJsonElement(
                            ListSerializer(ActionRef.serializer()),
                            actions,
                        ),
                    )
                },
        )

    fun button(
        id: String,
        label: String,
        onClick: ActionRef,
        variant: ButtonVariant = ButtonVariant.PRIMARY,
        size: ButtonSize = ButtonSize.MEDIUM,
        fullWidth: Boolean = false,
    ): ButtonAtom =
        ButtonAtom(
            id = id,
            label = label,
            variant = variant,
            size = size,
            fullWidth = fullWidth,
            onClick = onClick,
        )

    fun toolbar(
        title: String,
        subtitle: String,
        backActionId: String,
        trailingActions: List<IconAtom> = emptyList(),
    ): ToolbarSpec =
        ToolbarSpec(
            title = text(title, "title.large"),
            subtitle = text(subtitle, "body.small", "text.secondary"),
            navigationIcon =
                IconAtom(
                    name = "arrow_back",
                    sizeDp = 22,
                    color = ColorTokenRef("text.primary"),
                    contentDescription = "Navigate back",
                    onClick = backAction(backActionId),
                ),
            trailingActions = trailingActions,
            bgColor = ColorTokenRef("surface.primary"),
        )

    fun searchBar(
        snippetId: String,
        searchId: String,
        placeholder: String,
        routeBase: String,
        query: String = "",
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.SEARCH_BAR,
            data =
                encode(
                    SearchBarSnippetData(
                        searchId = searchId,
                        query = query,
                        placeholder = placeholder,
                        leadingIcon = IconAtom(name = "search", sizeDp = 18, color = ColorTokenRef("icon.tertiary")),
                        submitLabel = "Search",
                        clearable = true,
                        onSubmit = navAction("submit_$searchId", "$routeBase?q={{query}}"),
                        onClear = navAction("clear_$searchId", routeBase),
                        layout = searchBarLayout,
                    ),
                ),
        )

    fun chipGroup(
        snippetId: String,
        groupId: String,
        routeBase: String,
        chips: List<Pair<String, String>>,
        selectedId: String = chips.firstOrNull()?.first.orEmpty(),
        paramName: String = "filter",
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.CHIP_GROUP,
            data =
                encode(
                    ChipGroupSnippetData(
                        groupId = groupId,
                        chips =
                            chips.map { (id, label) ->
                                FilterChipItem(
                                    id = id,
                                    label = label,
                                    selected = id == selectedId,
                                    onClick = navAction("chip_${groupId}_$id", "$routeBase?$paramName=$id"),
                                )
                            },
                        layout = chipRowLayout,
                    ),
                ),
        )

    fun actionChipGroup(
        snippetId: String,
        groupId: String,
        chips: List<Triple<String, String, ActionRef>>,
        selectedId: String = "",
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.CHIP_GROUP,
            data =
                encode(
                    ChipGroupSnippetData(
                        groupId = groupId,
                        chips =
                            chips.map { (id, label, action) ->
                                FilterChipItem(
                                    id = id,
                                    label = label,
                                    selected = id == selectedId,
                                    onClick = action,
                                )
                            },
                        layout = chipRowLayout,
                    ),
                ),
        )

    fun banner(
        snippetId: String,
        title: String,
        message: String,
        badge: String,
        cta: ButtonAtom,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.BANNER,
            data =
                encode(
                    BannerSnippetData(
                        title = text(title, "title.medium"),
                        body = text(message, "body.small", "text.secondary"),
                        tag = TagAtom(label = badge, bgColor = ColorTokenRef("brand.primary")),
                        cta = cta,
                        layout = bannerLayout,
                    ),
                ),
        )

    fun header(
        snippetId: String,
        title: String,
        subtitle: String,
        badge: String,
        trailingAction: ButtonAtom? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.HEADER,
            data =
                encode(
                    HeaderSnippetData(
                        title = text(title, "title.large"),
                        subtitle = text(subtitle, "body.small", "text.secondary"),
                        trailingTag = TagAtom(label = badge, bgColor = ColorTokenRef("brand.container")),
                        trailingAction = trailingAction,
                        layout = horizontalLayout,
                    ),
                ),
        )

    fun listingCard(spec: DemoListingSpec): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "card_${spec.id}",
            snippetType = SnippetTypes.CARD,
            data =
                encode(
                    CardSnippetData(
                        title = text(spec.title, "title.medium"),
                        subtitle = text(spec.subtitle, "body.small", "text.secondary"),
                        description = text(spec.details, "label.medium", "text.brand"),
                        badge = TagAtom(label = spec.badge.first, bgColor = ColorTokenRef(spec.badge.second)),
                        primaryButton =
                            button(
                                id = "btn_${spec.id}",
                                label = spec.ctaLabel,
                                onClick = navAction("nav_${spec.id}", spec.route),
                                size = ButtonSize.SMALL,
                            ),
                        onClick = navAction("tap_${spec.id}", spec.route),
                        layout = cardLayout,
                    ),
                ),
        )

    fun carousel(
        snippetId: String,
        title: String,
        items: List<Triple<String, String, String>>,
        route: String,
        badge: String,
    ): SnippetEnvelope {
        val cards =
            items.map { (id, heading, desc) ->
                val tag = TagAtom(label = badge, bgColor = ColorTokenRef("brand.container"))
                SnippetEnvelope(
                    snippetId = id,
                    snippetParentId = snippetId,
                    snippetType = SnippetTypes.CARD,
                    data =
                        encode(
                            CardSnippetData(
                                title = text(heading, "title.small"),
                                description = text(desc, "body.small", "text.secondary"),
                                badge = tag,
                                onClick = navAction("open_$id", route),
                                layout = compactCardLayout,
                            ),
                        ),
                )
            }
        return SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.CAROUSEL,
            data =
                encode(
                    CarouselSnippetData(
                        title = text(title, "title.medium"),
                        itemWidthDp = 248,
                        itemWidthByWidth = ResponsiveIntSpec(compact = 248, medium = 280, expanded = 300),
                        spacingDp = 12,
                        items = cards,
                        layout = horizontalLayout,
                    ),
                ),
        )
    }

    fun productGrid(
        gridId: String,
        title: String,
        items: List<DemoSkuSpec>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = gridId,
            snippetType = SnippetTypes.GRID,
            data =
                encode(
                    GridSnippetData(
                        title = text(title, "title.medium"),
                        columns = 2,
                        columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4),
                        spacingDp = 12,
                        items = items.mapIndexed { idx, sku -> productCard(gridId, idx, sku) },
                        layout = horizontalLayout,
                    ),
                ),
        )

    private fun productCard(
        parentId: String,
        index: Int,
        sku: DemoSkuSpec,
    ): SnippetEnvelope {
        val price = formatUsd(sku.cents)
        val onClick =
            sku.route?.let { navAction("open_${sku.id}", it) }
                ?: toastAction("peek_${sku.id}", "${sku.title} ($price)")
        return SnippetEnvelope(
            snippetId = "prod_${sku.id}",
            snippetParentId = parentId,
            snippetType = SnippetTypes.PRODUCT_CARD,
            data =
                encode(
                    ProductCardSnippetData(
                        productId = sku.id,
                        title = text(sku.title, "title.small"),
                        subtitle = text(sku.subtitle, "body.small", "text.secondary"),
                        price = text(price, "label.large"),
                        originalPrice = text(formatUsd(sku.cents + 80), "body.small", "text.tertiary"),
                        unitPriceCents = sku.cents,
                        badge = TagAtom(label = sku.badge.first, bgColor = ColorTokenRef(sku.badge.second)),
                        image =
                            ImageAtom(
                                url = "https://cdn.abeta.dev/catalog/${sku.id}.png",
                                contentDescription = sku.title,
                                heightDp = 96,
                                scaleMode = ImageScaleMode.CROP,
                                cornerRadius = CornerRadiusSpec(all = 8),
                                placeholderColor = ColorTokenRef(sku.badge.second),
                            ),
                        stepper = StepperAtom("cart_qty_${sku.id}", "ADD", sku.qty, maxQuantity = 10),
                        rating = text("4.8 ★", "label.small", "status.success"),
                        onClick = onClick,
                        layout = compactCardLayout,
                    ),
                ),
            motion =
                MotionSpec(
                    enter = MotionTransition.FADE_IN,
                    durationToken = MotionDurationToken.FAST,
                    staggerMs = 35,
                    delayMs = index * 35,
                ),
        )
    }

    fun actionRow(
        snippetId: String,
        title: String,
        subtitle: String,
        icon: Pair<String, String>,
        tag: Pair<String, String>? = null,
        onClick: ActionRef? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.ACTION_ROW,
            data =
                encode(
                    ActionRowSnippetData(
                        title = text(title, "title.small"),
                        subtitle = text(subtitle, "body.small", "text.secondary"),
                        leadingIcon = IconAtom(name = icon.first, sizeDp = 20, color = ColorTokenRef(icon.second)),
                        trailingTag = tag?.let { TagAtom(label = it.first, bgColor = ColorTokenRef(it.second)) },
                        onClick = onClick,
                        layout = cardLayout,
                    ),
                ),
        )

    fun accordion(
        snippetId: String,
        title: String,
        subtitle: String,
        children: List<SnippetEnvelope>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.ACCORDION,
            data =
                encode(
                    AccordionSnippetData(
                        title = text(title, "title.medium"),
                        subtitle = text(subtitle, "body.small", "text.secondary"),
                        initiallyExpanded = true,
                        snippets = children,
                        layout = cardLayout,
                    ),
                ),
        )

    fun metric(
        snippetId: String,
        label: String,
        value: String,
        badge: String,
        caption: String,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                encode(
                    MetricCardSnippetData(
                        label = text(label, "label.medium", "text.secondary"),
                        value = text(value, "headline.small", "text.brand"),
                        delta = TagAtom(label = badge, bgColor = ColorTokenRef("status.success_container")),
                        caption = text(caption, "body.small", "text.secondary"),
                        layout = cardLayout,
                    ),
                ),
        )
}
