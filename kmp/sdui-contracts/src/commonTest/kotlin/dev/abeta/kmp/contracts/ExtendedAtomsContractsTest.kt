package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtendedAtomsContractsTest {
    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 12, vertical = 8),
            fillMaxWidth = true,
        )

    private val sampleOptions =
        listOf(
            OptionItem(id = "express", label = "Express", subtitle = "10 mins", badge = "FAST"),
            OptionItem(id = "standard", label = "Standard", enabled = false),
        )

    private fun sampleAction(
        id: String,
        type: String = "save_state",
    ): ActionRef =
        ActionRef(
            id = id,
            type = type,
            payload = buildJsonObject { put("source", id) },
        )

    @Test
    fun radioGroupAndSelectRoundTripWithWireKeysAndDefaults() {
        val radio =
            RadioGroupAtom(
                fieldId = "delivery_speed",
                label = "Delivery Speed",
                options = sampleOptions,
                selectedId = "express",
                onChange = sampleAction("speed_changed"),
                layout = sampleLayout,
            )
        val encodedRadio = SduiJson.encodeToString(radio)
        assertWireKeys(encodedRadio, "field_id", "selected_id", "on_change")
        assertEquals(radio, SduiJson.decodeFromString<RadioGroupAtom>(encodedRadio))

        val select =
            SelectAtom(
                fieldId = "country",
                label = "Country",
                placeholder = "Choose country",
                options = sampleOptions,
                selectedId = "express",
                helperText = "Used for tax calculation",
                onChange = sampleAction("country_changed"),
                layout = sampleLayout,
            )
        val encodedSelect = SduiJson.encodeToString(select)
        assertWireKeys(encodedSelect, "field_id", "selected_id", "helper_text", "on_change")
        assertEquals(select, SduiJson.decodeFromString<SelectAtom>(encodedSelect))

        val minimalSelect = SduiJson.decodeFromString<SelectAtom>("""{"field_id":"plan"}""")
        assertEquals("Select an option", minimalSelect.placeholder)
        assertTrue(minimalSelect.enabled)
        assertTrue(minimalSelect.options.isEmpty())
    }

    @Test
    fun otpInputAndSegmentedControlRoundTripWithWireKeysAndDefaults() {
        val otp =
            OtpInputAtom(
                fieldId = "sms_code",
                label = "Verification Code",
                length = 6,
                initialValue = "12",
                masked = true,
                helperText = "Sent to +1 555-0100",
                errorText = "Invalid code",
                onComplete = sampleAction("otp_done", "call_api"),
                layout = sampleLayout,
            )
        val encodedOtp = SduiJson.encodeToString(otp)
        assertWireKeys(encodedOtp, "field_id", "initial_value", "helper_text", "error_text", "on_complete")
        assertEquals(otp, SduiJson.decodeFromString<OtpInputAtom>(encodedOtp))

        val minimalOtp = SduiJson.decodeFromString<OtpInputAtom>("""{"field_id":"pin"}""")
        assertEquals(6, minimalOtp.length)
        assertEquals("", minimalOtp.initialValue)
        assertFalse(minimalOtp.masked)

        val segmented =
            SegmentedControlAtom(
                fieldId = "billing_cycle",
                options = sampleOptions,
                selectedId = "express",
                onChange = sampleAction("cycle_changed"),
                layout = sampleLayout,
            )
        val encodedSeg = SduiJson.encodeToString(segmented)
        assertWireKeys(encodedSeg, "field_id", "selected_id", "on_change")
        assertEquals(segmented, SduiJson.decodeFromString<SegmentedControlAtom>(encodedSeg))
    }

    @Test
    fun chipAndRatingRoundTripWithWireKeysAndDefaults() {
        val chip =
            ChipAtom(
                id = "vegan",
                label = "Vegan",
                selected = true,
                removable = true,
                leadingIcon = IconAtom(name = "star"),
                onClick = sampleAction("chip_tap"),
                onRemove = sampleAction("chip_rm"),
                layout = sampleLayout,
            )
        val encodedChip = SduiJson.encodeToString(chip)
        assertWireKeys(encodedChip, "leading_icon", "on_click", "on_remove")
        assertEquals(chip, SduiJson.decodeFromString<ChipAtom>(encodedChip))

        val rating =
            RatingAtom(
                id = "sku_rating",
                fieldId = "user_rating",
                value = 4.8f,
                maxStars = 10,
                interactive = true,
                reviewCount = 124,
                label = "Rating",
                starSizeDp = 24,
                onRate = sampleAction("rated"),
                layout = sampleLayout,
            )
        val encodedRating = SduiJson.encodeToString(rating)
        assertWireKeys(encodedRating, "field_id", "max_stars", "review_count", "star_size_dp", "on_rate")
        assertEquals(rating, SduiJson.decodeFromString<RatingAtom>(encodedRating))

        val minimalRating = SduiJson.decodeFromString<RatingAtom>("""{}""")
        assertEquals(5, minimalRating.maxStars)
        assertEquals(20, minimalRating.starSizeDp)
        assertFalse(minimalRating.interactive)
    }

    @Test
    fun priceAndBadgeRoundTripWithWireKeysAndDefaults() {
        val price =
            PriceAtom(
                id = "hero_price",
                amountText = "$12.99",
                originalAmountText = "$16.50",
                discountLabel = "21% OFF",
                unitSuffix = "/ kg",
                emphasized = true,
                layout = sampleLayout,
            )
        val encodedPrice = SduiJson.encodeToString(price)
        assertWireKeys(encodedPrice, "amount_text", "original_amount_text", "discount_label", "unit_suffix")
        assertEquals(price, SduiJson.decodeFromString<PriceAtom>(encodedPrice))

        val badge =
            BadgeAtom(
                id = "unread",
                count = 120,
                maxCount = 50,
                dotOnly = true,
                variant = TagVariant.BRAND,
                layout = sampleLayout,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val encodedBadge = SduiJson.encodeToString(badge)
        assertWireKeys(encodedBadge, "max_count", "dot_only")
        assertEquals(badge, SduiJson.decodeFromString<BadgeAtom>(encodedBadge))

        val minimalBadge = SduiJson.decodeFromString<BadgeAtom>("""{}""")
        assertEquals(99, minimalBadge.maxCount)
        assertEquals(TagVariant.ERROR, minimalBadge.variant)
        assertFalse(minimalBadge.dotOnly)
        assertNull(minimalBadge.count)
    }

    @Test
    fun alertBannerAndStatTileRoundTripWithWireKeys() {
        val banner =
            AlertBannerAtom(
                id = "kyc_banner",
                title = "Verify identity",
                message = "Unlock higher transfer limits",
                variant = TagVariant.WARNING,
                icon = IconAtom(name = "warning"),
                actionLabel = "Verify Now",
                onAction = sampleAction("start_kyc", "navigate"),
                dismissible = true,
                onDismiss = sampleAction("dismiss_kyc", "track_event"),
                layout = sampleLayout,
            )
        val encodedBanner = SduiJson.encodeToString(banner)
        assertWireKeys(encodedBanner, "action_label", "on_action", "on_dismiss")
        assertEquals(banner, SduiJson.decodeFromString<AlertBannerAtom>(encodedBanner))

        val stat =
            StatTileAtom(
                id = "gmv",
                label = "Daily GMV",
                value = "$42.8K",
                deltaLabel = "+12.4%",
                deltaPositive = true,
                caption = "vs yesterday",
                icon = IconAtom(name = "star"),
                onClick = sampleAction("open_metrics", "navigate"),
                layout = sampleLayout,
            )
        val encodedStat = SduiJson.encodeToString(stat)
        assertWireKeys(encodedStat, "delta_label", "delta_positive", "on_click")
        assertEquals(stat, SduiJson.decodeFromString<StatTileAtom>(encodedStat))
    }

    @Test
    fun keyValueRowAndQrCodeRoundTripWithWireKeysAndDefaults() {
        val kv =
            KeyValueRowAtom(
                id = "grand_total",
                label = "Total Due",
                value = "$24.50",
                sublabel = "Includes taxes",
                valueColor = ColorTokenRef(token = "text.success"),
                emphasized = true,
                showDivider = true,
                onClick = sampleAction("toggle_breakdown"),
                layout = sampleLayout,
            )
        val encodedKv = SduiJson.encodeToString(kv)
        assertWireKeys(encodedKv, "value_color", "show_divider", "on_click")
        assertEquals(kv, SduiJson.decodeFromString<KeyValueRowAtom>(encodedKv))

        val qr =
            QrCodeAtom(
                id = "ticket_qr",
                payload = "abeta://checkin/ORD-4091",
                label = "Pickup Pass",
                caption = "Scan at counter 3",
                sizeDp = 180,
                onClick = sampleAction("zoom_qr"),
                layout = sampleLayout,
            )
        val encodedQr = SduiJson.encodeToString(qr)
        assertWireKeys(encodedQr, "size_dp", "on_click")
        assertEquals(qr, SduiJson.decodeFromString<QrCodeAtom>(encodedQr))

        val minimalQr = SduiJson.decodeFromString<QrCodeAtom>("""{"payload":"abeta://pay"}""")
        assertEquals(160, minimalQr.sizeDp)
    }

    @Test
    fun composedNodeKindsExposeAllTwelveExtendedWireNames() {
        val expectedWireKinds =
            mapOf(
                ComposedNodeKind.RADIO_GROUP to "\"radio_group\"",
                ComposedNodeKind.SELECT to "\"select\"",
                ComposedNodeKind.OTP_INPUT to "\"otp_input\"",
                ComposedNodeKind.CHIP to "\"chip\"",
                ComposedNodeKind.SEGMENTED_CONTROL to "\"segmented_control\"",
                ComposedNodeKind.RATING to "\"rating\"",
                ComposedNodeKind.PRICE to "\"price\"",
                ComposedNodeKind.BADGE to "\"badge\"",
                ComposedNodeKind.ALERT_BANNER to "\"alert_banner\"",
                ComposedNodeKind.STAT_TILE to "\"stat_tile\"",
                ComposedNodeKind.KEY_VALUE_ROW to "\"key_value_row\"",
                ComposedNodeKind.QR_CODE to "\"qr_code\"",
            )
        expectedWireKinds.forEach { (kind, wireName) ->
            assertEquals(wireName, SduiJson.encodeToString(kind))
            assertEquals(kind, SduiJson.decodeFromString<ComposedNodeKind>(wireName))
        }
    }

    @Test
    fun composedNodeRoundTripsAllTwelveExtendedFields() {
        val root =
            ComposedNode(
                nodeId = "extended_root",
                kind = ComposedNodeKind.COLUMN,
                children = buildExtendedComposedChildren(),
            )
        val encoded = SduiJson.encodeToString(root)
        assertWireKeys(
            encoded,
            "radio_group",
            "select",
            "otp_input",
            "chip",
            "segmented_control",
            "rating",
            "price",
            "badge",
            "alert_banner",
            "stat_tile",
            "key_value_row",
            "qr_code",
        )
        assertEquals(root, SduiJson.decodeFromString<ComposedNode>(encoded))
    }

    private fun buildExtendedComposedChildren(): List<ComposedNode> =
        listOf(
            ComposedNode(kind = ComposedNodeKind.RADIO_GROUP, radioGroup = RadioGroupAtom(fieldId = "r1")),
            ComposedNode(kind = ComposedNodeKind.SELECT, select = SelectAtom(fieldId = "s1")),
            ComposedNode(kind = ComposedNodeKind.OTP_INPUT, otpInput = OtpInputAtom(fieldId = "o1")),
            ComposedNode(kind = ComposedNodeKind.CHIP, chip = ChipAtom(id = "c1", label = "Fast")),
            ComposedNode(
                kind = ComposedNodeKind.SEGMENTED_CONTROL,
                segmentedControl = SegmentedControlAtom(fieldId = "seg1"),
            ),
            ComposedNode(kind = ComposedNodeKind.RATING, rating = RatingAtom(id = "rt1", value = 4.5f)),
            ComposedNode(kind = ComposedNodeKind.PRICE, price = PriceAtom(amountText = "$9.99")),
            ComposedNode(kind = ComposedNodeKind.BADGE, badge = BadgeAtom(count = 7)),
            ComposedNode(kind = ComposedNodeKind.ALERT_BANNER, alertBanner = AlertBannerAtom(title = "Notice")),
            ComposedNode(kind = ComposedNodeKind.STAT_TILE, statTile = StatTileAtom(label = "Orders", value = "128")),
            ComposedNode(
                kind = ComposedNodeKind.KEY_VALUE_ROW,
                keyValueRow = KeyValueRowAtom(label = "Subtotal", value = "$18.00"),
            ),
            ComposedNode(kind = ComposedNodeKind.QR_CODE, qrCode = QrCodeAtom(payload = "abeta://pass")),
        )

    private fun assertWireKeys(
        encodedJson: String,
        vararg keys: String,
    ) {
        keys.forEach { key ->
            assertTrue(encodedJson.contains("\"$key\""), "Expected wire key \"$key\" in $encodedJson")
        }
    }
}
