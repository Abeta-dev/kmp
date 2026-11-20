package dev.abeta.kmp.layout.snippet

import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.engine.collectAllEnvelopes
import dev.abeta.kmp.layout.engine.computeFloatingCartSummary
import dev.abeta.kmp.layout.engine.formatUsdCents
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoreSnippetRenderersTest {
    @Test
    fun productCardSnippetRendererDecodesValidPayload() {
        val registry = BuiltInSnippetModule.createDefaultRegistry()
        val renderer = assertNotNull(registry.resolve(SnippetTypes.PRODUCT_CARD))
        val payload =
            ProductCardSnippetData(
                productId = "avocados",
                title = TextAtom(text = "Hass Avocados (2 pcs)"),
                price = TextAtom(text = "$3.49"),
                originalPrice = TextAtom(text = "$4.49"),
                unitPriceCents = 349,
                stepper = StepperAtom(fieldId = "cart_qty_avocados", label = "ADD", quantity = 1),
            )
        val encoded = SduiJson.encodeToJsonElement(ProductCardSnippetData.serializer(), payload) as JsonObject
        val decoded = assertIs<AppResult.Success<ProductCardSnippetData>>(renderer.decode(encoded)).value
        assertEquals("avocados", decoded.productId)
        assertEquals(349, decoded.unitPriceCents)
        assertEquals("cart_qty_avocados", decoded.stepper.fieldId)
    }

    @Test
    fun formatUsdCentsFormatsZeroSingleDigitAndMultiDollarValues() {
        assertEquals("$0.00", formatUsdCents(0))
        assertEquals("$0.05", formatUsdCents(5))
        assertEquals("$3.49", formatUsdCents(349))
        assertEquals("$11.97", formatUsdCents(1197))
        assertEquals("$0.00", formatUsdCents(-42))
    }

    @Test
    fun computeFloatingCartSummaryAggregatesNestedTabAndGridProductsAndDeduplicates() {
        val avocadoEnvelope = productEnvelope(id = "avocados", qty = 1, unitPriceCents = 349)
        val sourdoughEnvelope = productEnvelope(id = "sourdough", qty = 1, unitPriceCents = 499)
        val page =
            PageResponse(
                pageId = "store-home",
                snippets =
                    listOf(
                        SnippetEnvelope(
                            snippetId = "tabs",
                            snippetType = SnippetTypes.TABS,
                            data =
                                encodeObject(
                                    TabsSnippetData(
                                        tabs =
                                            listOf(
                                                TabItemSpec(
                                                    id = "produce",
                                                    title = "Produce",
                                                    snippets = listOf(avocadoEnvelope),
                                                ),
                                                TabItemSpec(
                                                    id = "bakery",
                                                    title = "Bakery",
                                                    snippets = listOf(sourdoughEnvelope, avocadoEnvelope),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )

        assertTrue(collectAllEnvelopes(page.snippets).size >= 3)

        val initialSummary = computeFloatingCartSummary(page, emptyMap())
        assertTrue(initialSummary.hasProductCards)
        assertEquals(2, initialSummary.itemCount)
        assertEquals(848, initialSummary.totalCents)
        assertEquals("2 items · $8.48", initialSummary.formattedLabel())

        val singleItemSummary =
            computeFloatingCartSummary(
                page,
                mapOf("cart_qty_avocados" to "1", "cart_qty_sourdough" to "0"),
            )
        assertEquals(1, singleItemSummary.itemCount)
        assertEquals("1 item · $3.49", singleItemSummary.formattedLabel())

        val emptySummary = computeFloatingCartSummary(PageResponse(pageId = "empty"), emptyMap())
        assertFalse(emptySummary.hasProductCards)
    }

    private fun productEnvelope(
        id: String,
        qty: Int,
        unitPriceCents: Int,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "prod_$id",
            snippetType = SnippetTypes.PRODUCT_CARD,
            data =
                encodeObject(
                    ProductCardSnippetData(
                        productId = id,
                        title = TextAtom(text = id, color = ColorTokenRef("text.primary")),
                        price = TextAtom(text = formatUsdCents(unitPriceCents)),
                        unitPriceCents = unitPriceCents,
                        stepper = StepperAtom(fieldId = "cart_qty_$id", quantity = qty),
                    ),
                ),
        )

    private inline fun <reified T> encodeObject(value: T): JsonObject {
        val element = SduiJson.encodeToJsonElement(serializer<T>(), value)
        return element as JsonObject
    }
}
