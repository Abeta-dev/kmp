package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.ColorAccessibility
import dev.abeta.kmp.tokens.ThemePreset
import dev.abeta.kmp.tokens.resolveColorTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorybookCatalogTest {
    @Test
    fun `story catalog defines unique ids across all four categories`() {
        val entries = StoryCatalog.entries
        val uniqueIds = entries.map { it.id }.toSet()
        assertEquals(entries.size, uniqueIds.size)

        for (category in StoryCategory.entries) {
            assertTrue(entries.any { it.category == category }, "Missing category $category")
        }

        val expectedIds =
            setOf(
                StoryCatalog.STORY_SUPER_APP_DEMOS,
                StoryCatalog.STORY_INTRODUCTION,
                StoryCatalog.STORY_DESIGN_TOKENS,
                StoryCatalog.STORY_BUTTON,
                StoryCatalog.STORY_TEXT_TAG,
                StoryCatalog.STORY_TEXT_FIELD,
                StoryCatalog.STORY_MEDIA_FEEDBACK,
                StoryCatalog.STORY_CONTROLS_DATA,
                StoryCatalog.STORY_SNIPPETS,
                StoryCatalog.STORY_PLAYGROUND,
                StoryCatalog.STORY_MOTION_STORE,
                StoryCatalog.STORY_LIVE_BFF,
                StoryCatalog.STORY_RESPONSIVE_BRIDGES,
                StoryCatalog.STORY_DISCOVERY_SEARCH,
                StoryCatalog.STORY_ANIMATION_NUDGES,
                StoryCatalog.STORY_SNIPPET_CRAFTER,
                StoryCatalog.STORY_E2E_JOURNEYS,
            )
        assertEquals(17, entries.size)
        assertEquals(expectedIds, uniqueIds)
        assertEquals(StoryCatalog.STORY_INTRODUCTION, StoryCatalog.findById("unknown").id)
    }

    @Test
    fun `super app demo catalog covers all 6 verticals and watch screen`() {
        val appIds = SuperAppDemoCatalog.apps.map { it.id }.toSet()
        assertTrue("abeta-fresh" in appIds)
        assertTrue("abeta-bites" in appIds)
        assertTrue("abeta-pulse" in appIds)
        assertTrue("abeta-mart" in appIds)
        assertTrue("abeta-spark" in appIds)
        assertTrue("abeta-trade" in appIds)
        assertTrue("abeta-watch" in appIds)
        assertEquals("bites-home", SuperAppDemoCatalog.resolveInitialScreen("abeta-bites"))
        assertEquals("spark-deck", SuperAppDemoCatalog.resolveInitialScreen("spark-deck"))
        assertEquals("fresh-home", SuperAppDemoCatalog.resolveInitialScreen("unknown"))
    }

    @Test
    fun `snippet gallery samples cover all 21 canonical snippet types`() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()
        val samples = SnippetsSamples.allItems()
        val sampleTypes = samples.map { it.envelope.snippetType }.toSet()
        val expectedTypes =
            setOf(
                SnippetTypes.HEADER,
                SnippetTypes.BANNER,
                SnippetTypes.METRIC_CARD,
                SnippetTypes.CARD,
                SnippetTypes.ACTION_ROW,
                SnippetTypes.FORM,
                SnippetTypes.EMPTY_STATE,
                SnippetTypes.DIVIDER,
                SnippetTypes.SPACER,
                SnippetTypes.CAROUSEL,
                SnippetTypes.GRID,
                SnippetTypes.TABS,
                SnippetTypes.ACCORDION,
                SnippetTypes.PRODUCT_CARD,
                SnippetTypes.SEARCH_BAR,
                SnippetTypes.CHIP_GROUP,
                SnippetTypes.SWIPE_DECK,
                SnippetTypes.NUDGE_BAR,
                SnippetTypes.MARQUEE_STRIP,
                SnippetTypes.ORDER_TRACKER,
                SnippetTypes.COMPOSED_BLOCK,
            )

        assertEquals(expectedTypes.size, samples.size)
        assertEquals(expectedTypes, sampleTypes)
        assertEquals(expectedTypes, registry.registeredTypes())

        for (sample in samples) {
            val renderer = assertNotNull(registry.resolve(sample.envelope.snippetType))
            val decoded = renderer.decode(sample.envelope.data)
            assertTrue(decoded is AppResult.Success, "Failed to decode ${sample.envelope.snippetType}")
        }
    }

    @Test
    fun `snippet crafter presets mold valid composed block wire contracts`() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()
        val composedRenderer = assertNotNull(registry.resolve(SnippetTypes.COMPOSED_BLOCK))
        for (preset in CrafterPreset.entries) {
            for (root in CrafterRootContainer.entries) {
                val config = SnippetCrafterPresets.defaultConfigFor(preset).copy(rootContainer = root)
                val envelope = SnippetCrafterPresets.buildMoldedEnvelope(config = config, stepperQty = 2)
                assertEquals(SnippetTypes.COMPOSED_BLOCK, envelope.snippetType)
                assertTrue(composedRenderer.decode(envelope.data) is AppResult.Success)
                val json = SnippetCrafterPresets.formatEnvelopeJson(envelope)
                assertTrue(json.contains(preset.recipeName))
            }
        }
    }

    @Test
    fun `storybook presets and wire contracts stay valid for live rendering`() {
        for (preset in ThemePreset.entries) {
            val light = resolveColorTokens(preset = preset, darkTheme = false)
            assertTrue(ColorAccessibility.meetsWcagAA(light.text.primary, light.surface.primary))
        }
        val sampleJson =
            SduiJson.encodeToString(
                ButtonAtom.serializer(),
                ButtonAtom(label = "Storybook", variant = ButtonVariant.PRIMARY),
            )
        assertTrue(sampleJson.contains("Storybook"))
    }
}
