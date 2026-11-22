package dev.abeta.kmp.layout.snippet

import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.layout.engine.LayoutFormSeeder
import dev.abeta.kmp.layout.ui.computeFreeDeliveryNudge
import dev.abeta.kmp.state.session.InMemorySessionStore
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgeAndComposedRenderersTest {
    @Test
    fun freeDeliveryNudgeComputesProgressRemainingAndUnlockedState() {
        val noThreshold = FloatingCartSpec(label = "Cart", cta = ButtonAtom(label = "Checkout"))
        assertNull(computeFreeDeliveryNudge(noThreshold, totalCents = 500))

        val zeroThreshold = noThreshold.copy(freeDeliveryThresholdCents = 0)
        assertNull(computeFreeDeliveryNudge(zeroThreshold, totalCents = 500))

        val withThreshold =
            noThreshold.copy(
                freeDeliveryThresholdCents = 1000,
                nudgePrefix = "Add",
                unlockedLabel = "FREE express delivery unlocked!",
            )
        val halfWay = assertNotNull(computeFreeDeliveryNudge(withThreshold, totalCents = 650))
        assertEquals(0.65f, halfWay.progress)
        assertEquals(350, halfWay.remainingCents)
        assertFalse(halfWay.unlocked)
        assertEquals("Add $3.50 more for FREE delivery", halfWay.message)

        val unlocked = assertNotNull(computeFreeDeliveryNudge(withThreshold, totalCents = 1200))
        assertEquals(1f, unlocked.progress)
        assertEquals(0, unlocked.remainingCents)
        assertTrue(unlocked.unlocked)
        assertEquals("FREE express delivery unlocked!", unlocked.message)

        val defaultUnlocked =
            assertNotNull(
                computeFreeDeliveryNudge(
                    noThreshold.copy(freeDeliveryThresholdCents = 500),
                    totalCents = 500,
                ),
            )
        assertEquals("FREE delivery unlocked!", defaultUnlocked.message)
    }

    @Test
    fun marqueeVelocityAndOrderTrackerProgressResolveDeterministically() {
        assertEquals(48, resolveMarqueeVelocityDpPerSec(0))
        assertEquals(12, resolveMarqueeVelocityDpPerSec(4))
        assertEquals(240, resolveMarqueeVelocityDpPerSec(999))
        assertEquals(60, resolveMarqueeVelocityDpPerSec(60))

        assertEquals(8000, resolveMarqueeCycleDurationMs(contentWidthPx = 0, density = 2f, velocityDpPerSec = 48))
        assertEquals(5000, resolveMarqueeCycleDurationMs(contentWidthPx = 600, density = 2f, velocityDpPerSec = 60))

        assertEquals(0f, resolveOrderTrackerProgress(emptyList()))
        assertEquals(
            1f,
            resolveOrderTrackerProgress(listOf(OrderTrackerStep(id = "s1", label = "Done", completed = true))),
        )
        assertEquals(
            0.5f,
            resolveOrderTrackerProgress(
                listOf(
                    OrderTrackerStep(id = "s1", label = "Placed", completed = true),
                    OrderTrackerStep(id = "s2", label = "Packing", active = true),
                    OrderTrackerStep(id = "s3", label = "Delivered"),
                ),
            ),
        )
        assertEquals(
            1f,
            resolveOrderTrackerProgress(
                listOf(
                    OrderTrackerStep(id = "s1", label = "Placed", completed = true),
                    OrderTrackerStep(id = "s2", label = "Delivered", completed = true),
                ),
            ),
        )
    }

    @Test
    fun composedDepthGuardAndStepperFormSeedingRespectMaxDepth() {
        assertTrue(canRenderComposedDepth(0))
        assertTrue(canRenderComposedDepth(MAX_COMPOSED_DEPTH))
        assertFalse(canRenderComposedDepth(-1))
        assertFalse(canRenderComposedDepth(MAX_COMPOSED_DEPTH + 1))

        val deepStepper =
            ComposedNode(
                kind = ComposedNodeKind.STEPPER,
                stepper = StepperAtom(fieldId = "too_deep_qty", quantity = 3),
            )
        var wrapped = deepStepper
        repeat(MAX_COMPOSED_DEPTH + 1) {
            wrapped = ComposedNode(kind = ComposedNodeKind.COLUMN, children = listOf(wrapped))
        }

        val block =
            ComposedBlockSnippetData(
                blockId = "bundle_block",
                root =
                    ComposedNode(
                        kind = ComposedNodeKind.COLUMN,
                        children =
                            listOf(
                                ComposedNode(
                                    kind = ComposedNodeKind.STEPPER,
                                    stepper = StepperAtom(fieldId = "bundle_qty", quantity = 2),
                                ),
                                wrapped,
                            ),
                    ),
            )
        val envelope =
            SnippetEnvelope(
                snippetId = "cb_1",
                snippetType = SnippetTypes.COMPOSED_BLOCK,
                data = SduiJson.encodeToJsonElement(ComposedBlockSnippetData.serializer(), block) as JsonObject,
            )
        val session = InMemorySessionStore()
        val seeded =
            LayoutFormSeeder.extractInitialFormValues(
                PageResponse(pageId = "p", snippets = listOf(envelope)),
                session,
            )
        assertEquals("2", seeded["bundle_qty"])
        assertNull(seeded["too_deep_qty"])
    }
}
