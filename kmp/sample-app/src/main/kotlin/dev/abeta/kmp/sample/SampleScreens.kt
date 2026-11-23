package dev.abeta.kmp.sample

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
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ResponsiveIntSpec
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

internal data class ListingCardSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val details: String,
    val badge: Pair<String, String>,
    val ctaLabel: String,
    val route: String,
)

/**
 * Facade providing built-in offline SDUI sample pages and shared builder primitives.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SampleScreens {
    internal val centeredAdaptive = AdaptivePageSpec(layoutMode = ScreenLayoutMode.CENTERED, maxWidthDp = 1120)
    internal val horizontalPaddingLayout =
        LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 8), fillMaxWidth = true)
    internal val chipRowLayout =
        LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 4), fillMaxWidth = true)

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

    internal val cardLayout = boxLayout("surface.primary")
    internal val bannerLayout = boxLayout("brand.container")
    internal val searchBarLayout =
        boxLayout("surface.primary", "border.default", SpacingValues(horizontal = 12, vertical = 6))

    internal inline fun <reified T> encode(value: T): JsonObject {
        val element = SduiJson.encodeToJsonElement(serializer<T>(), value)
        return element as JsonObject
    }

    internal fun formatUsd(cents: Int): String = "\$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    internal fun text(
        value: String,
        typography: String,
        color: String = "text.primary",
    ): TextAtom = TextAtom(text = value, color = ColorTokenRef(color), typography = TypographyTokenRef(typography))

    internal fun navAction(
        id: String,
        route: String,
    ): ActionRef = ActionRef(id = id, type = ActionTypes.NAVIGATE, payload = buildJsonObject { put("route", route) })

    internal fun backAction(id: String): ActionRef =
        ActionRef(id = id, type = ActionTypes.BACK_PRESS, payload = buildJsonObject { put("steps", 1) })

    internal fun toastAction(
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

    internal fun apiCallAction(
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

    internal fun shareAction(
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

    @Suppress("LongParameterList")
    internal fun button(
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

    internal fun floatingCart(
        label: String,
        eta: String,
        ctaLabel: String,
        action: ActionRef,
    ): FloatingCartSpec =
        FloatingCartSpec(
            label = label,
            etaBadge =
                TagAtom(
                    label = eta,
                    bgColor = ColorTokenRef("status.success_container"),
                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                ),
            cta = button("btn_cart_${action.id}", ctaLabel, action),
        )

    internal fun verticalToolbar(
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

    internal fun searchBarEnvelope(
        snippetId: String,
        searchId: String,
        placeholder: String,
        routeBase: String,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.SEARCH_BAR,
            data =
                encode(
                    SearchBarSnippetData(
                        searchId = searchId,
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

    internal fun chipGroupEnvelope(
        snippetId: String,
        groupId: String,
        routeBase: String,
        chips: List<Pair<String, String>>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.CHIP_GROUP,
            data =
                encode(
                    ChipGroupSnippetData(
                        groupId = groupId,
                        chips =
                            chips.mapIndexed { index, (chipId, label) ->
                                FilterChipItem(
                                    id = chipId,
                                    label = label,
                                    selected = index == 0,
                                    onClick = navAction("chip_${groupId}_$chipId", "$routeBase?filter=$chipId"),
                                )
                            },
                        layout = chipRowLayout,
                    ),
                ),
        )

    internal fun headerEnvelope(
        snippetId: String,
        title: String,
        subtitle: String,
        tagLabel: String,
        trailingAction: ButtonAtom? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.HEADER,
            data =
                encode(
                    HeaderSnippetData(
                        title = text(title, "headline.small"),
                        subtitle = text(subtitle, "body.small", "text.secondary"),
                        trailingTag = TagAtom(label = tagLabel, bgColor = ColorTokenRef("status.success_container")),
                        trailingAction = trailingAction,
                        layout = cardLayout,
                    ),
                ),
        )

    internal fun bannerEnvelope(
        snippetId: String,
        title: String,
        body: String,
        tagLabel: String,
        cta: ButtonAtom? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.BANNER,
            data =
                encode(
                    BannerSnippetData(
                        title = text(title, "title.medium"),
                        body = text(body, "body.small", "text.secondary"),
                        icon = IconAtom(name = "star", sizeDp = 22, color = ColorTokenRef("brand.primary")),
                        tag =
                            TagAtom(
                                label = tagLabel,
                                bgColor = ColorTokenRef("status.success_container"),
                                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                            ),
                        cta = cta,
                        layout = bannerLayout,
                    ),
                ),
        )

    internal fun carouselEnvelope(
        snippetId: String,
        title: String,
        cards: List<Triple<String, String, String>>,
        route: String,
        badgeLabel: String,
    ): SnippetEnvelope {
        val badge = TagAtom(label = badgeLabel, bgColor = ColorTokenRef("brand.container"))
        val envelopes =
            cards.map { (id, cardTitle, subtitle) ->
                SnippetEnvelope(
                    snippetId = id,
                    snippetParentId = snippetId,
                    snippetType = SnippetTypes.CARD,
                    data =
                        encode(
                            CardSnippetData(
                                title = text(cardTitle, "title.small"),
                                subtitle = text(subtitle, "body.small", "text.secondary"),
                                badge = badge,
                                onClick = navAction("open_$id", route),
                                layout = storeCompactCardLayout,
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
                        items = envelopes,
                        itemWidthDp = 240,
                        itemWidthByWidth = ResponsiveIntSpec(compact = 220, medium = 260, expanded = 280),
                        spacingDp = 12,
                        layout = horizontalPaddingLayout,
                    ),
                ),
        )
    }

    internal fun listingCardEnvelope(spec: ListingCardSpec): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = spec.id,
            snippetType = SnippetTypes.CARD,
            data =
                encode(
                    CardSnippetData(
                        title = text(spec.title, "title.medium"),
                        subtitle = text(spec.subtitle, "body.small", "text.brand"),
                        description = text(spec.details, "body.small", "text.secondary"),
                        badge = TagAtom(label = spec.badge.first, bgColor = ColorTokenRef(spec.badge.second)),
                        primaryButton =
                            button(
                                id = "btn_${spec.id}",
                                label = spec.ctaLabel,
                                onClick = navAction("nav_${spec.id}", spec.route),
                                size = ButtonSize.SMALL,
                            ),
                        onClick = navAction("card_${spec.id}", spec.route),
                        layout = cardLayout,
                    ),
                ),
        )

    @Suppress("LongParameterList")
    internal fun metricEnvelope(
        snippetId: String,
        label: String,
        value: String,
        delta: String,
        caption: String? = null,
        progress: ProgressAtom? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                encode(
                    MetricCardSnippetData(
                        label = text(label, "label.medium", "text.secondary"),
                        value = text(value, "headline.small"),
                        delta = TagAtom(label = delta, bgColor = ColorTokenRef("status.success_container")),
                        caption = caption?.let { text(it, "body.small", "text.secondary") },
                        progress = progress,
                        layout = cardLayout,
                    ),
                ),
        )

    internal fun accordionEnvelope(
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

    @Suppress("LongParameterList")
    internal fun actionRowEnvelope(
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
                        trailingIcon =
                            onClick?.let {
                                IconAtom(name = "chevron_right", sizeDp = 18, color = ColorTokenRef("text.tertiary"))
                            },
                        onClick = onClick,
                        layout = cardLayout,
                    ),
                ),
        )

    fun home(): PageResponse = buildHomeSamplePage()

    fun catalog(): PageResponse = buildCatalogSamplePage()

    fun profile(): PageResponse = buildProfileSamplePage()

    fun formDemo(): PageResponse = buildFormDemoSamplePage()

    fun storeHome(): PageResponse = SampleStoreScreen.storeHome()

    fun watchStatus(): PageResponse = SampleWatchScreen.watchStatus()

    fun allPages(): Map<String, PageResponse> =
        mapOf(
            "home" to home(),
            "store-home" to storeHome(),
            "catalog" to catalog(),
            "profile" to profile(),
            "form-demo" to formDemo(),
            "containers" to containers(),
            "watch-status" to watchStatus(),
            "fresh-home" to SampleVerticalsCommerce.freshHome(),
            "bites-home" to SampleVerticalsCommerce.bitesHome(),
            "bites-restaurant" to SampleVerticalsCommerce.bitesRestaurant(),
            "bites-order-track" to SampleVerticalsCommerce.bitesOrderTrack(),
            "mart-home" to SampleVerticalsCommerce.martHome(),
            "mart-product-detail" to SampleVerticalsCommerce.martProductDetail(),
            "pulse-home" to SampleVerticalsDiscovery.pulseHome(),
            "pulse-event-detail" to SampleVerticalsDiscovery.pulseEventDetail(),
            "spark-deck" to SampleVerticalsDiscovery.sparkDeck(),
            "spark-matches" to SampleVerticalsDiscovery.sparkMatches(),
            "trade-home" to SampleVerticalsDiscovery.tradeHome(),
            "trade-supplier" to SampleVerticalsDiscovery.tradeSupplier(),
            "pay-home" to SamplePayVertical.payHome(),
            "pay-transfer" to SamplePayVertical.payTransfer(),
            "pay-cards" to SamplePayVertical.payCards(),
            "pay-receipt" to SamplePayVertical.payReceipt(),
        )
}
