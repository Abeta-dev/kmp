package dev.abeta.kmp.layout.snippet

import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import kotlinx.serialization.json.Json

/**
 * Registers all 21 built-in SDUI snippet renderers backed exclusively by stateless `ui-atoms` atoms.
 */
object BuiltInSnippetModule {
    /** Populates [builder] with renderers for all canonical [SnippetTypes]. */
    fun registerDefaults(
        builder: SnippetRegistry.Builder,
        json: Json = SduiJson,
        registryProvider: () -> SnippetRegistry = { builder.resolveBuiltOrSnapshot() },
    ): SnippetRegistry.Builder =
        builder.apply {
            register(HeaderSnippetRenderer(json))
            register(BannerSnippetRenderer(json))
            register(MetricCardSnippetRenderer(json))
            register(CardSnippetRenderer(json))
            register(ActionRowSnippetRenderer(json))
            register(FormSnippetRenderer(json))
            register(EmptyStateSnippetRenderer(json))
            register(DividerSnippetRenderer(json))
            register(SpacerSnippetRenderer(json))
            register(CarouselSnippetRenderer(json, registryProvider))
            register(GridSnippetRenderer(json, registryProvider))
            register(TabsSnippetRenderer(json, registryProvider))
            register(AccordionSnippetRenderer(json, registryProvider))
            register(ProductCardSnippetRenderer(json))
            register(SwipeDeckSnippetRenderer(json))
            register(SearchBarSnippetRenderer(json))
            register(ChipGroupSnippetRenderer(json))
            register(NudgeBarSnippetRenderer(json))
            register(MarqueeStripSnippetRenderer(json))
            register(OrderTrackerSnippetRenderer(json))
            register(ComposedBlockSnippetRenderer(json))
        }

    /** Convenience factory creating a [SnippetRegistry] pre-populated with all built-in renderers. */
    fun createDefaultRegistry(json: Json = SduiJson): SnippetRegistry {
        var registry: SnippetRegistry? = null
        val built =
            registerDefaults(
                builder = SnippetRegistry.builder(),
                json = json,
                registryProvider = { checkNotNull(registry) { "SnippetRegistry accessed before build()." } },
            ).build()
        registry = built
        return built
    }
}
