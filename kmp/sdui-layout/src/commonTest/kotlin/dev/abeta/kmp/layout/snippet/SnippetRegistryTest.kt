package dev.abeta.kmp.layout.snippet

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.DividerSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacerSnippetData
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SnippetRegistryTest {
    @Serializable
    private data class CustomPromoBanner(
        @SerialName("promo_code")
        val promoCode: String,
    )

    @Test
    fun customSnippetRegistersViaOcpWithoutModifyingRegistryCore() {
        val customRenderer =
            object : SnippetRenderer<CustomPromoBanner> {
                override val snippetType: String = "custom_promo_v1"
                private val decoder = kotlinxSnippetDecoder<CustomPromoBanner>()

                override fun decode(data: JsonObject): AppResult<CustomPromoBanner> = decoder(data)

                @Composable
                @Suppress("ktlint:standard:function-naming", "FunctionName")
                override fun Render(
                    model: CustomPromoBanner,
                    envelope: SnippetEnvelope,
                    formValues: Map<String, String>,
                    onFormValueChange: (String, String) -> Unit,
                    onAction: (ActionRef) -> Unit,
                ) {
                    Text(text = model.promoCode)
                }
            }

        val registry =
            BuiltInSnippetModule
                .registerDefaults(SnippetRegistry.builder())
                .register(customRenderer)
                .build()

        val resolved = assertNotNull(registry.resolve("custom_promo_v1"))
        val decoded =
            resolved.decode(
                buildJsonObject {
                    put("promo_code", "ABETA2026")
                },
            )
        val model = assertIs<AppResult.Success<CustomPromoBanner>>(decoded).value
        assertEquals("ABETA2026", model.promoCode)
        assertNull(registry.resolve("non_existent_snippet"))
    }

    @Test
    fun allThirteenBuiltInSnippetRenderersRegisterAndDecodeCoreSnippets() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()

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
        assertEquals(expectedTypes, registry.registeredTypes())

        val header =
            assertIs<AppResult.Success<HeaderSnippetData>>(
                registry.resolve(SnippetTypes.HEADER)!!.decode(
                    buildJsonObject { put("title", buildJsonObject { put("text", "Hello") }) },
                ),
            ).value
        assertEquals("Hello", header.title.text)

        val banner =
            assertIs<AppResult.Success<BannerSnippetData>>(
                registry.resolve(SnippetTypes.BANNER)!!.decode(
                    buildJsonObject { put("title", buildJsonObject { put("text", "Promo") }) },
                ),
            ).value
        assertEquals("Promo", banner.title.text)

        val metric =
            assertIs<AppResult.Success<MetricCardSnippetData>>(
                registry.resolve(SnippetTypes.METRIC_CARD)!!.decode(
                    buildJsonObject {
                        put("label", buildJsonObject { put("text", "Uptime") })
                        put("value", buildJsonObject { put("text", "99.9%") })
                    },
                ),
            ).value
        assertEquals("99.9%", metric.value.text)

        val card =
            assertIs<AppResult.Success<CardSnippetData>>(
                registry.resolve(SnippetTypes.CARD)!!.decode(
                    buildJsonObject { put("title", buildJsonObject { put("text", "Card") }) },
                ),
            ).value
        assertEquals("Card", card.title.text)
    }

    @Test
    fun builtInSnippetDecodersDecodeActionFormEmptyDividerAndSpacer() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()

        val row =
            assertIs<AppResult.Success<ActionRowSnippetData>>(
                registry.resolve(SnippetTypes.ACTION_ROW)!!.decode(
                    buildJsonObject { put("title", buildJsonObject { put("text", "Profile") }) },
                ),
            ).value
        assertEquals("Profile", row.title.text)

        val form =
            assertIs<AppResult.Success<FormSnippetData>>(
                registry.resolve(SnippetTypes.FORM)!!.decode(
                    buildJsonObject {
                        put("form_id", "onboarding")
                        put("fields", buildJsonArray { })
                    },
                ),
            ).value
        assertEquals("onboarding", form.formId)

        val empty =
            assertIs<AppResult.Success<EmptyStateSnippetData>>(
                registry.resolve(SnippetTypes.EMPTY_STATE)!!.decode(
                    buildJsonObject { put("title", buildJsonObject { put("text", "Nothing here") }) },
                ),
            ).value
        assertEquals("Nothing here", empty.title.text)

        val divider =
            assertIs<AppResult.Success<DividerSnippetData>>(
                registry.resolve(SnippetTypes.DIVIDER)!!.decode(buildJsonObject { }),
            ).value
        assertNotNull(divider.divider)

        val spacer =
            assertIs<AppResult.Success<SpacerSnippetData>>(
                registry.resolve(SnippetTypes.SPACER)!!.decode(
                    buildJsonObject { put("spacer", buildJsonObject { put("height_dp", 16) }) },
                ),
            ).value
        assertEquals(16, spacer.spacer.heightDp)
    }

    @Test
    fun builtInContainerSnippetDecodersDecodeCarouselGridTabsAndAccordion() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()

        val carousel =
            assertIs<AppResult.Success<CarouselSnippetData>>(
                registry.resolve(SnippetTypes.CAROUSEL)!!.decode(
                    buildJsonObject { put("item_width_dp", 220) },
                ),
            ).value
        assertEquals(220, carousel.itemWidthDp)

        val grid =
            assertIs<AppResult.Success<GridSnippetData>>(
                registry.resolve(SnippetTypes.GRID)!!.decode(
                    buildJsonObject { put("columns", 3) },
                ),
            ).value
        assertEquals(3, grid.columns)

        val tabs =
            assertIs<AppResult.Success<TabsSnippetData>>(
                registry.resolve(SnippetTypes.TABS)!!.decode(
                    buildJsonObject { put("selected_index", 1) },
                ),
            ).value
        assertEquals(1, tabs.selectedIndex)

        val accordion =
            assertIs<AppResult.Success<AccordionSnippetData>>(
                registry.resolve(SnippetTypes.ACCORDION)!!.decode(
                    buildJsonObject {
                        put("title", buildJsonObject { put("text", "FAQ") })
                        put("initially_expanded", true)
                    },
                ),
            ).value
        assertEquals("FAQ", accordion.title.text)
    }

    @Test
    fun malformedSnippetDataReturnsSerializationError() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()
        val result = registry.resolve(SnippetTypes.METRIC_CARD)!!.decode(buildJsonObject { })
        val failure = assertIs<AppResult.Failure>(result)
        assertIs<SerializationError>(failure.error)
    }
}
