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
            )
        assertEquals(expectedIds, uniqueIds)
        assertEquals(StoryCatalog.STORY_INTRODUCTION, StoryCatalog.findById("unknown").id)
    }

    @Test
    fun `snippet gallery samples cover all 14 canonical snippet types`() {
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
