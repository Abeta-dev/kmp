package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgeAndComposedContractsTest {
    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 12),
            cornerRadius = CornerRadiusSpec(token = "radius.md", all = 12),
            fillMaxWidth = true,
        )

    private fun action(
        id: String,
        type: String,
        key: String,
        value: String,
    ): ActionRef = ActionRef(id = id, type = type, payload = buildJsonObject { put(key, value) })

    @Test
    fun snippetTypesAndMotionLoopEffectsExposeCanonicalWireIdentifiers() {
        assertEquals("nudge_bar", SnippetTypes.NUDGE_BAR)
        assertEquals("marquee_strip", SnippetTypes.MARQUEE_STRIP)
        assertEquals("order_tracker", SnippetTypes.ORDER_TRACKER)
        assertEquals("composed_block", SnippetTypes.COMPOSED_BLOCK)

        val expectedLoopWireNames =
            mapOf(
                MotionLoopEffect.NONE to "\"none\"",
                MotionLoopEffect.PULSE to "\"pulse\"",
                MotionLoopEffect.SHIMMER to "\"shimmer\"",
                MotionLoopEffect.WIGGLE to "\"wiggle\"",
                MotionLoopEffect.FLOAT to "\"float\"",
                MotionLoopEffect.HEARTBEAT to "\"heartbeat\"",
                MotionLoopEffect.GLOW_RING to "\"glow_ring\"",
                MotionLoopEffect.MARQUEE to "\"marquee\"",
            )
        assertEquals(expectedLoopWireNames.size, MotionLoopEffect.entries.size)
        expectedLoopWireNames.forEach { (effect, wireJson) ->
            assertEquals(wireJson, SduiJson.encodeToString(effect))
            assertEquals(effect, SduiJson.decodeFromString<MotionLoopEffect>(wireJson))
        }
    }

    @Test
    fun countdownAtomAndFloatingCartThresholdRoundTripAndPreserveDefaults() {
        val countdown =
            CountdownAtom(
                countdownId = "flash_deal",
                remainingSeconds = 240,
                label = "Ends in",
                urgentThresholdSeconds = 45,
                showHours = true,
                expiredText = "EXPIRED",
                onExpire = action("exp_1", "refresh_page", "reason", "expired"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.HEARTBEAT),
            )
        val encodedCountdown = SduiJson.encodeToString(countdown)
        for (key in listOf(
            "countdown_id",
            "remaining_seconds",
            "urgent_threshold_seconds",
            "show_hours",
            "expired_text",
            "on_expire",
        )) {
            assertTrue(encodedCountdown.contains("\"$key\""), "Missing wire key $key")
        }
        assertEquals(countdown, SduiJson.decodeFromString<CountdownAtom>(encodedCountdown))

        val minimalCountdown =
            SduiJson.decodeFromString<CountdownAtom>("""{"countdown_id":"c1","remaining_seconds":30}""")
        assertEquals(60, minimalCountdown.urgentThresholdSeconds)
        assertFalse(minimalCountdown.showHours)
        assertEquals("00:00", minimalCountdown.expiredText)

        val cart =
            FloatingCartSpec(
                label = "2 items · $8.48",
                cta = ButtonAtom(label = "View Cart"),
                freeDeliveryThresholdCents = 1500,
                nudgePrefix = "Add",
                unlockedLabel = "FREE express delivery unlocked!",
            )
        val encodedCart = SduiJson.encodeToString(cart)
        assertTrue(encodedCart.contains("\"free_delivery_threshold_cents\""))
        assertTrue(encodedCart.contains("\"nudge_prefix\""))
        assertTrue(encodedCart.contains("\"unlocked_label\""))
        assertEquals(cart, SduiJson.decodeFromString<FloatingCartSpec>(encodedCart))

        val legacyCart =
            SduiJson.decodeFromString<FloatingCartSpec>(
                """{"label":"1 item · $4.00","cta":{"label":"Checkout"}}""",
            )
        assertNull(legacyCart.freeDeliveryThresholdCents)
        assertNull(legacyCart.nudgePrefix)
        assertNull(legacyCart.unlockedLabel)
    }

    @Test
    fun nudgeBarAndMarqueeStripRoundTripWithWireKeysAndDefaults() {
        val nudge =
            NudgeBarSnippetData(
                nudgeId = "free_ship",
                title = TextAtom(text = "Almost at FREE delivery"),
                subtitle = TextAtom(text = "Add $2.50 more to unlock zero fee"),
                icon = IconAtom(name = "star", sizeDp = 18),
                badge = TagAtom(label = "SAVE", variant = TagVariant.SUCCESS),
                countdown = CountdownAtom(countdownId = "nudge_timer", remainingSeconds = 90),
                progress = 0.72f,
                cta = ButtonAtom(label = "Browse Deals", size = ButtonSize.SMALL),
                dismissible = true,
                pointerEdge = "top",
                onDismiss = action("dismiss_nudge", "track_event", "event", "nudge_dismissed"),
                onClick = action("click_nudge", "navigate", "route", "/deals"),
                layout = sampleLayout,
            )
        val encodedNudge = SduiJson.encodeToString(nudge)
        for (key in listOf("nudge_id", "pointer_edge", "on_dismiss", "on_click")) {
            assertTrue(encodedNudge.contains("\"$key\""), "Missing wire key $key")
        }
        assertEquals(nudge, SduiJson.decodeFromString<NudgeBarSnippetData>(encodedNudge))

        val minimalNudge =
            SduiJson.decodeFromString<NudgeBarSnippetData>(
                """{"nudge_id":"n1","title":{"text":"Hello"}}""",
            )
        assertTrue(minimalNudge.dismissible)
        assertNull(minimalNudge.progress)

        val marquee =
            MarqueeStripSnippetData(
                stripId = "offers_ticker",
                items =
                    listOf(
                        MarqueeItem(
                            id = "m1",
                            text = "20% OFF Fresh Produce",
                            icon = IconAtom(name = "star"),
                            badge = TagAtom(label = "HOT", variant = TagVariant.BRAND),
                            onClick = action("m1_click", "show_toast", "message", "Promo applied"),
                        ),
                    ),
                velocityDpPerSec = 60,
                separator = "★",
                layout = sampleLayout,
            )
        val encodedMarquee = SduiJson.encodeToString(marquee)
        assertTrue(encodedMarquee.contains("\"strip_id\""))
        assertTrue(encodedMarquee.contains("\"velocity_dp_per_sec\""))
        assertEquals(marquee, SduiJson.decodeFromString<MarqueeStripSnippetData>(encodedMarquee))

        val minimalMarquee = SduiJson.decodeFromString<MarqueeStripSnippetData>("""{"strip_id":"s1"}""")
        assertEquals(48, minimalMarquee.velocityDpPerSec)
        assertEquals("•", minimalMarquee.separator)
        assertTrue(minimalMarquee.items.isEmpty())
    }

    @Test
    fun orderTrackerRoundTripsWithWireKeys() {
        val tracker =
            OrderTrackerSnippetData(
                trackerId = "order_live_1",
                orderCode = "#AB-4092",
                headline = TextAtom(text = "Rider is 2 mins away"),
                etaCountdown = CountdownAtom(countdownId = "eta_1", remainingSeconds = 120),
                etaBadge = TagAtom(label = "LIVE", variant = TagVariant.SUCCESS),
                riderName = "Aarav K.",
                riderCaption = "Electric Scooter · 4.9★",
                steps =
                    listOf(
                        OrderTrackerStep(id = "s1", label = "Confirmed", completed = true),
                        OrderTrackerStep(
                            id = "s2",
                            label = "On the way",
                            caption = "600m away",
                            icon = IconAtom(name = "home"),
                            active = true,
                        ),
                        OrderTrackerStep(id = "s3", label = "Delivered"),
                    ),
                primaryAction = ButtonAtom(label = "Call Rider", variant = ButtonVariant.PRIMARY),
                secondaryAction = ButtonAtom(label = "Help", variant = ButtonVariant.OUTLINE),
                layout = sampleLayout,
            )
        val encodedTracker = SduiJson.encodeToString(tracker)
        for (key in listOf(
            "tracker_id",
            "order_code",
            "eta_countdown",
            "eta_badge",
            "rider_name",
            "rider_caption",
            "primary_action",
            "secondary_action",
        )) {
            assertTrue(encodedTracker.contains("\"$key\""), "Missing wire key $key")
        }
        assertEquals(tracker, SduiJson.decodeFromString<OrderTrackerSnippetData>(encodedTracker))
    }

    @Test
    fun composedBlockRoundTripsAllKinds() {
        assertEquals(17, ComposedNodeKind.entries.size)
        ComposedNodeKind.entries.forEach { kind ->
            assertEquals(kind, SduiJson.decodeFromString<ComposedNodeKind>(SduiJson.encodeToString(kind)))
        }

        val composed =
            ComposedBlockSnippetData(
                blockId = "flash_deal_card",
                recipeName = "flash_deal_v1",
                root =
                    ComposedNode(
                        nodeId = "root_card",
                        kind = ComposedNodeKind.CARD,
                        spacingDp = 10,
                        children =
                            listOf(
                                ComposedNode(
                                    nodeId = "counter_1",
                                    kind = ComposedNodeKind.ROLLING_COUNTER,
                                    counterValue = "$14.99",
                                    counterTypography = TypographyTokenRef(token = "title.large"),
                                    counterColor = ColorTokenRef(token = "brand.primary"),
                                ),
                                ComposedNode(
                                    nodeId = "stepper_1",
                                    kind = ComposedNodeKind.STEPPER,
                                    stepper = StepperAtom(fieldId = "bundle_qty", quantity = 1),
                                ),
                            ),
                    ),
                layout = sampleLayout,
            )
        val encodedComposed = SduiJson.encodeToString(composed)
        assertTrue(encodedComposed.contains("\"block_id\""))
        assertTrue(encodedComposed.contains("\"recipe_name\""))
        assertTrue(encodedComposed.contains("\"rolling_counter\""))
        assertEquals(composed, SduiJson.decodeFromString<ComposedBlockSnippetData>(encodedComposed))
    }

    @Test
    fun composedBlockDecodesGoBffAliases() {
        val bffAliasedJson =
            """
            {
              "block_id": "bff_recipe",
              "root": {
                "id": "row_1",
                "kind": "row",
                "horizontal_alignment": "center",
                "vertical_alignment": "center",
                "main_axis_arrangement": "space_between",
                "children": [
                  { "id": "rc_1", "kind": "rolling_counter", "rolling_value": "42" }
                ]
              }
            }
            """.trimIndent()
        val decodedBff = SduiJson.decodeFromString<ComposedBlockSnippetData>(bffAliasedJson)
        assertEquals("row_1", decodedBff.root.nodeId)
        assertEquals("space_between", decodedBff.root.mainAxisArrangement)
        assertEquals(
            "rc_1",
            decodedBff.root.children
                .first()
                .nodeId,
        )
        assertEquals(
            "42",
            decodedBff.root.children
                .first()
                .counterValue,
        )
    }
}
