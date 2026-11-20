package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractsSerializationTest {
    private val sampleAction =
        ActionRef(
            id = "act_1",
            type = "navigate",
            payload =
                buildJsonObject {
                    put("route", "/checkout")
                    put("step", 2)
                },
            validations =
                listOf(
                    ValidationRuleContract(
                        fieldId = "full_name",
                        rule = "required",
                        message = "Full name is required",
                    ),
                    ValidationRuleContract(
                        fieldId = "work_email",
                        rule = "regex",
                        value = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                        message = "Enter a valid email address",
                    ),
                ),
            secondaryActions =
                listOf(
                    ActionRef(
                        type = "track_analytics",
                        payload = buildJsonObject { put("event", "cta_tapped") },
                    ),
                ),
        )

    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 12),
            margin = SpacingValues(top = 8, bottom = 8),
            bgColor = ColorTokenRef(token = "surface.elevated", alpha = 0.95f, fallbackHex = "#FFFFFF"),
            cornerRadius = CornerRadiusSpec(token = "radius.md", all = 12),
            border = BorderSpec(widthDp = 1f, widthToken = "thin", color = ColorTokenRef(token = "border.default")),
            fillMaxWidth = true,
        )

    @Test
    fun actionRefAndTrackingRoundTrip() {
        val tracking =
            TrackingContract(
                eventName = "screen_viewed",
                properties = mapOf("screen" to "home", "source" to "deep_link"),
            )
        val encodedAction = SduiJson.encodeToString(sampleAction)
        assertTrue(encodedAction.contains("\"secondary_actions\""))
        assertTrue(encodedAction.contains("\"field_id\""))
        assertEquals(sampleAction, SduiJson.decodeFromString<ActionRef>(encodedAction))

        val encodedTracking = SduiJson.encodeToString(tracking)
        assertTrue(encodedTracking.contains("\"event_name\""))
        assertEquals(tracking, SduiJson.decodeFromString<TrackingContract>(encodedTracking))
    }

    @Test
    fun displayAtomsRoundTripCleanly() {
        val textAtom =
            TextAtom(
                id = "txt_1",
                text = "Welcome back",
                color = ColorTokenRef(token = "text.primary", alpha = 0.9f, fallbackHex = "#111318"),
                typography =
                    TypographyTokenRef(
                        token = "headline.small",
                        fontWeight = "semi_bold",
                        letterSpacing = 0.2,
                    ),
                maxLines = 2,
                alignment = AlignmentToken.CENTER,
                markdown = true,
                strikeThrough = false,
                underline = true,
                prefixIcon = IconAtom(name = "sparkles", sizeDp = 16),
                suffixIcon = IconAtom(name = "chevron_right", sizeDp = 16),
                onClick = sampleAction,
                layout = sampleLayout,
            )
        assertEquals(textAtom, SduiJson.decodeFromString(SduiJson.encodeToString(textAtom)))

        val iconAtom =
            IconAtom(
                id = "ico_1",
                name = "arrow_forward",
                sizeDp = 20,
                color = ColorTokenRef(hex = "#4F46E5"),
                contentDescription = "Continue",
                onClick = sampleAction,
                layout = sampleLayout,
            )
        assertEquals(iconAtom, SduiJson.decodeFromString(SduiJson.encodeToString(iconAtom)))

        val tagAtom =
            TagAtom(
                id = "tag_1",
                label = "Active",
                variant = TagVariant.SUCCESS,
                bgColor = ColorTokenRef(token = "status.success_container"),
                textColor = ColorTokenRef(token = "text.primary"),
                border = BorderSpec(widthDp = 1f, color = ColorTokenRef(token = "border.subtle")),
                icon = IconAtom(name = "check", sizeDp = 14),
                onClick = sampleAction,
                layout = sampleLayout,
            )
        assertEquals(tagAtom, SduiJson.decodeFromString(SduiJson.encodeToString(tagAtom)))
    }

    @Test
    fun interactiveAndMediaAtomsRoundTripCleanly() {
        val buttonAtom =
            ButtonAtom(
                id = "btn_1",
                label = "Submit Order",
                subLabel = "Instant confirmation",
                variant = ButtonVariant.SECONDARY,
                size = ButtonSize.LARGE,
                enabled = true,
                loading = false,
                fullWidth = true,
                leadingIcon = IconAtom(name = "check"),
                trailingIcon = IconAtom(name = "chevron_right"),
                onClick = sampleAction,
                tracking = TrackingContract(eventName = "submit_clicked"),
                layout = sampleLayout,
            )
        assertEquals(buttonAtom, SduiJson.decodeFromString(SduiJson.encodeToString(buttonAtom)))

        val imageAtom =
            ImageAtom(
                id = "img_1",
                url = "https://cdn.abeta.dev/banner.png",
                contentDescription = "Promo banner",
                widthDp = 320,
                heightDp = 160,
                aspectRatio = 2.0f,
                scaleMode = ImageScaleMode.FIT,
                cornerRadius = CornerRadiusSpec(all = 8),
                placeholderColor = ColorTokenRef(token = "surface.secondary"),
                onClick = sampleAction,
                layout = sampleLayout,
            )
        assertEquals(imageAtom, SduiJson.decodeFromString(SduiJson.encodeToString(imageAtom)))
    }

    @Test
    fun inputAndLayoutAtomsRoundTripCleanly() {
        val textFieldAtom =
            TextFieldAtom(
                fieldId = "work_email",
                label = "Work Email",
                placeholder = "name@company.com",
                initialValue = "eng@abeta.dev",
                helperText = "We never share your email",
                errorText = null,
                enabled = true,
                readOnly = false,
                singleLine = true,
                maxLines = 1,
                maxLength = 120,
                required = true,
                regex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                keyboardType = KeyboardKind.EMAIL,
                prefixIcon = IconAtom(name = "person"),
                suffixIcon = IconAtom(name = "close"),
                onValueChange = ActionRef(type = "form_field_change"),
                onSubmit = ActionRef(type = "form_submit"),
                layout = sampleLayout,
            )
        assertEquals(textFieldAtom, SduiJson.decodeFromString(SduiJson.encodeToString(textFieldAtom)))

        val dividerAtom =
            DividerAtom(
                id = "div_1",
                thicknessDp = 1.5f,
                color = ColorTokenRef(token = "divider.default"),
                insetStartDp = 16,
                layout = sampleLayout,
            )
        assertEquals(dividerAtom, SduiJson.decodeFromString(SduiJson.encodeToString(dividerAtom)))

        val spacerAtom = SpacerAtom(id = "spc_1", widthDp = 16, heightDp = 24, weight = 1.0f)
        assertEquals(spacerAtom, SduiJson.decodeFromString(SduiJson.encodeToString(spacerAtom)))

        val progressAtom =
            ProgressAtom(
                id = "prg_1",
                kind = ProgressKind.CIRCULAR,
                progress = 0.72f,
                label = "72%",
                color = ColorTokenRef(token = "status.success"),
                trackColor = ColorTokenRef(token = "border.subtle"),
                layout = sampleLayout,
            )
        assertEquals(progressAtom, SduiJson.decodeFromString(SduiJson.encodeToString(progressAtom)))
    }

    @Test
    fun contentSnippetDtosRoundTrip() {
        val header =
            HeaderSnippetData(
                title = TextAtom(text = "Overview", typography = TypographyTokenRef(token = "title.large")),
                subtitle = TextAtom(text = "Updated just now"),
                leadingIcon = IconAtom(name = "sparkles", sizeDp = 24),
                trailingTag = TagAtom(label = "Live", variant = TagVariant.INFO),
                trailingAction =
                    ButtonAtom(
                        label = "Catalog",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                    ),
                trailingIcon = IconAtom(name = "settings"),
                onClick = sampleAction,
            )
        val banner =
            BannerSnippetData(
                title = TextAtom(text = "System Healthy"),
                body = TextAtom(text = "All services operational"),
                icon = IconAtom(name = "info"),
                tag = TagAtom(label = "v1.0"),
                cta = ButtonAtom(label = "Details", variant = ButtonVariant.GHOST),
            )
        val card =
            CardSnippetData(
                title = TextAtom(text = "KMP Kit"),
                subtitle = TextAtom(text = "Layer 1"),
                description = TextAtom(text = "Pure tokens and atoms."),
                media = ImageAtom(url = "https://cdn.abeta.dev/preview.png", heightDp = 120),
                badge = TagAtom(label = "STABLE"),
                leadingIcon = IconAtom(name = "sparkles"),
                primaryButton = ButtonAtom(label = "Explore"),
                secondaryButton = ButtonAtom(label = "Docs", variant = ButtonVariant.SECONDARY),
            )

        assertEquals(header, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(header)))
        assertEquals(banner, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(banner)))
        assertEquals(card, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(card)))
    }

    @Test
    fun interactiveAndLayoutSnippetDtosRoundTrip() {
        val actionRow =
            ActionRowSnippetData(
                title = TextAtom(text = "Billing Settings"),
                leadingIcon = IconAtom(name = "settings"),
                trailingTag = TagAtom(label = "Pro"),
                trailingIcon = IconAtom(name = "chevron_right"),
                onClick = sampleAction,
            )
        val form =
            FormSnippetData(
                formId = "profile_form",
                title = TextAtom(text = "Profile"),
                fields = listOf(TextFieldAtom(fieldId = "name", label = "Name")),
                submitButton = ButtonAtom(label = "Save"),
                cancelButton = ButtonAtom(label = "Cancel", variant = ButtonVariant.OUTLINE),
            )
        val emptyState =
            EmptyStateSnippetData(
                title = TextAtom(text = "No results"),
                message = TextAtom(text = "Try adjusting filters."),
                icon = IconAtom(name = "search"),
                actionButton = ButtonAtom(label = "Reset", variant = ButtonVariant.OUTLINE),
            )
        val divider = DividerSnippetData(divider = DividerAtom(thicknessDp = 1f, insetStartDp = 16))
        val spacer = SpacerSnippetData(spacer = SpacerAtom(heightDp = 16))

        assertEquals(actionRow, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(actionRow)))
        assertEquals(form, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(form)))
        assertEquals(emptyState, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(emptyState)))
        assertEquals(divider, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(divider)))
        assertEquals(spacer, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(spacer)))
    }

    @Test
    fun metricSnippetAndEnvelopeRoundTrip() {
        val metric =
            MetricCardSnippetData(
                label = TextAtom(text = "Daily Active Users"),
                value = TextAtom(text = "42,190", typography = TypographyTokenRef(token = "display.large")),
                delta = TagAtom(label = "+12.4%", variant = TagVariant.SUCCESS),
                progress = ProgressAtom(kind = ProgressKind.LINEAR, progress = 0.84f, label = "Target"),
            )
        assertEquals(metric, SduiJson.decodeFromJsonElement(SduiJson.encodeToJsonElement(metric)))

        val envelope =
            SnippetEnvelope(
                snippetId = "snip_1",
                snippetParentId = "root",
                snippetType = SnippetTypes.METRIC_CARD,
                data = SduiJson.encodeToJsonElement(metric).jsonObject,
                tracking = TrackingContract(eventName = "metric_impression"),
            )
        val decodedEnvelope =
            SduiJson.decodeFromString<SnippetEnvelope>(
                SduiJson.encodeToString(envelope),
            )
        assertEquals(envelope, decodedEnvelope)
        assertEquals(metric, SduiJson.decodeFromJsonElement<MetricCardSnippetData>(decodedEnvelope.data))
    }

    @Test
    fun pageResponseDecodesRawGoBffJsonWithUnknownFields() {
        val rawBffJson =
            """
            {
              "page_id": "home_dashboard",
              "title": "Dashboard",
              "bff_version": "2026-11",
              "toolbar": {
                "title": { "text": "Dashboard", "typography": { "token": "title.large" } },
                "navigation_icon": { "name": "menu" },
                "trailing_actions": [{ "name": "notifications" }],
                "bg_color": { "token": "surface.primary" }
              },
              "snippets": [
                {
                  "snippet_id": "hdr_1",
                  "snippet_type": "header_v1",
                  "data": {
                    "title": { "text": "Today", "typography": { "token": "headline.medium" } },
                    "trailing_tag": { "label": "Beta", "variant": "brand" }
                  }
                }
              ],
              "bottom_bar": {
                "primary_button": {
                  "label": "Create",
                  "variant": "primary",
                  "size": "lg",
                  "full_width": true,
                  "on_click": { "type": "open_sheet", "payload": { "sheet_id": "new_item" } }
                },
                "helper_text": { "text": "Synced" }
              },
              "page_actions": [
                { "type": "prefetch", "payload": { "target": "/settings" } }
              ],
              "tracking": {
                "event_name": "page_loaded",
                "properties": { "page_id": "home_dashboard" }
              }
            }
            """.trimIndent()

        val page = SduiJson.decodeFromString<PageResponse>(rawBffJson)
        assertEquals("home_dashboard", page.pageId)
        assertEquals("Dashboard", page.title)
        assertNotNull(page.toolbar)
        assertEquals("menu", page.toolbar.navigationIcon?.name)
        assertEquals("surface.primary", page.toolbar.bgColor?.token)
        assertEquals(1, page.snippets.size)
        assertNull(page.snippets.first().snippetParentId)
        assertEquals(ButtonSize.LARGE, page.bottomBar?.primaryButton?.size)
        assertEquals("Synced", page.bottomBar?.helperText?.text)
        assertEquals(1, page.pageActions.size)
        assertEquals("page_loaded", page.tracking?.eventName)

        val reEncoded = SduiJson.encodeToString(page)
        val roundTripped = SduiJson.decodeFromString<PageResponse>(reEncoded)
        assertEquals(page, roundTripped)
    }

    @Test
    fun switchCheckboxAndSliderAtomsRoundTripCleanly() {
        val switchAtom =
            SwitchAtom(
                fieldId = "push_enabled",
                label = "Push Notifications",
                subtitle = "Receive order updates",
                checked = true,
                enabled = true,
                onChange = sampleAction,
                layout = sampleLayout,
            )
        val encodedSwitch = SduiJson.encodeToString(switchAtom)
        assertTrue(encodedSwitch.contains("\"field_id\""))
        assertTrue(encodedSwitch.contains("\"on_change\""))
        assertEquals(switchAtom, SduiJson.decodeFromString<SwitchAtom>(encodedSwitch))

        val checkboxAtom =
            CheckboxAtom(
                fieldId = "accept_terms",
                label = "Accept Terms",
                subtitle = "Required to continue",
                checked = false,
                enabled = true,
                required = true,
                onChange = sampleAction,
                layout = sampleLayout,
            )
        val encodedCheckbox = SduiJson.encodeToString(checkboxAtom)
        assertTrue(encodedCheckbox.contains("\"field_id\""))
        assertTrue(encodedCheckbox.contains("\"on_change\""))
        assertEquals(checkboxAtom, SduiJson.decodeFromString<CheckboxAtom>(encodedCheckbox))

        val sliderAtom =
            SliderAtom(
                fieldId = "delivery_radius",
                label = "Search Radius",
                value = 15f,
                minValue = 1f,
                maxValue = 50f,
                steps = 9,
                showValue = false,
                unitLabel = "km",
                enabled = true,
                onChange = sampleAction,
                layout = sampleLayout,
            )
        val encodedSlider = SduiJson.encodeToString(sliderAtom)
        assertTrue(encodedSlider.contains("\"min_value\""))
        assertTrue(encodedSlider.contains("\"max_value\""))
        assertTrue(encodedSlider.contains("\"show_value\""))
        assertTrue(encodedSlider.contains("\"unit_label\""))
        assertTrue(encodedSlider.contains("\"on_change\""))
        assertEquals(sliderAtom, SduiJson.decodeFromString<SliderAtom>(encodedSlider))
    }

    @Test
    fun avatarAndSkeletonAtomsRoundTripCleanly() {
        val avatarAtom =
            AvatarAtom(
                id = "av_1",
                url = "https://cdn.abeta.dev/avatar.png",
                initials = "AL",
                sizeDp = 48,
                bgColor = ColorTokenRef(token = "brand.container"),
                textColor = ColorTokenRef(token = "brand.primary"),
                contentDescription = "Ada Lovelace",
                onClick = sampleAction,
                layout = sampleLayout,
            )
        val encodedAvatar = SduiJson.encodeToString(avatarAtom)
        assertTrue(encodedAvatar.contains("\"size_dp\""))
        assertTrue(encodedAvatar.contains("\"bg_color\""))
        assertTrue(encodedAvatar.contains("\"text_color\""))
        assertTrue(encodedAvatar.contains("\"content_description\""))
        assertTrue(encodedAvatar.contains("\"on_click\""))
        assertEquals(avatarAtom, SduiJson.decodeFromString<AvatarAtom>(encodedAvatar))

        val skeletons =
            listOf(
                SkeletonAtom(id = "sk_txt", kind = SkeletonKind.TEXT, widthDp = 180, heightDp = 16),
                SkeletonAtom(
                    id = "sk_rect",
                    kind = SkeletonKind.RECTANGLE,
                    widthDp = 300,
                    heightDp = 120,
                    layout = sampleLayout,
                ),
                SkeletonAtom(id = "sk_circ", kind = SkeletonKind.CIRCLE, widthDp = 48, heightDp = 48),
            )
        skeletons.forEach { skeleton ->
            val encoded = SduiJson.encodeToString(skeleton)
            assertEquals(skeleton, SduiJson.decodeFromString<SkeletonAtom>(encoded))
        }
        assertTrue(SduiJson.encodeToString(skeletons[0]).contains("\"text\""))
        assertTrue(SduiJson.encodeToString(skeletons[2]).contains("\"circle\""))
    }

    @Test
    fun motionSpecAndFeedbackEnumsRoundTrip() {
        val motion =
            MotionSpec(
                enter = MotionTransition.SPRING_BOUNCE,
                exit = MotionTransition.SLIDE_DOWN,
                durationToken = MotionDurationToken.FAST,
                durationMs = 180,
                staggerMs = 45,
                delayMs = 20,
                loopEffect = MotionLoopEffect.PULSE,
            )
        val encodedMotion = SduiJson.encodeToString(motion)
        assertTrue(encodedMotion.contains("\"spring_bounce\""))
        assertTrue(encodedMotion.contains("\"slide_down\""))
        assertTrue(encodedMotion.contains("\"duration_token\":\"fast\""))
        assertTrue(encodedMotion.contains("\"duration_ms\":180"))
        assertTrue(encodedMotion.contains("\"stagger_ms\":45"))
        assertTrue(encodedMotion.contains("\"delay_ms\":20"))
        assertTrue(encodedMotion.contains("\"loop_effect\":\"pulse\""))
        assertEquals(motion, SduiJson.decodeFromString<MotionSpec>(encodedMotion))

        MotionTransition.entries.forEach { assertEquals(it, SduiJson.decodeFromString(SduiJson.encodeToString(it))) }
        MotionDurationToken.entries.forEach { assertEquals(it, SduiJson.decodeFromString(SduiJson.encodeToString(it))) }
        MotionLoopEffect.entries.forEach { assertEquals(it, SduiJson.decodeFromString(SduiJson.encodeToString(it))) }
        FeedbackEffect.entries.forEach { assertEquals(it, SduiJson.decodeFromString(SduiJson.encodeToString(it))) }
        assertEquals("\"celebration\"", SduiJson.encodeToString(FeedbackEffect.CELEBRATION))
        assertEquals("\"cart_bounce\"", SduiJson.encodeToString(FeedbackEffect.CART_BOUNCE))
        assertEquals("\"shake_error\"", SduiJson.encodeToString(FeedbackEffect.SHAKE_ERROR))
        assertEquals("\"haptic_tick\"", SduiJson.encodeToString(FeedbackEffect.HAPTIC_TICK))
    }

    @Test
    fun stepperAndAtomMotionRoundTrip() {
        val motion = MotionSpec(enter = MotionTransition.SCALE_IN, durationToken = MotionDurationToken.FAST)
        val stepper =
            StepperAtom(
                fieldId = "qty_avocado",
                label = "Quantity",
                quantity = 2,
                minQuantity = 0,
                maxQuantity = 12,
                enabled = true,
                onIncrement = ActionRef(type = "call_api", payload = buildJsonObject { put("delta", 1) }),
                onDecrement = ActionRef(type = "call_api", payload = buildJsonObject { put("delta", -1) }),
                layout = sampleLayout,
            )
        val encodedStepper = SduiJson.encodeToString(stepper)
        assertTrue(encodedStepper.contains("\"field_id\":\"qty_avocado\""))
        assertTrue(encodedStepper.contains("\"max_quantity\":12"))
        assertTrue(encodedStepper.contains("\"on_increment\""))
        assertTrue(encodedStepper.contains("\"on_decrement\""))
        assertEquals(stepper, SduiJson.decodeFromString<StepperAtom>(encodedStepper))

        val buttonWithMotion = ButtonAtom(label = "Add", motion = motion)
        val tagWithMotion = TagAtom(label = "Express", variant = TagVariant.BRAND, motion = motion)
        assertEquals(buttonWithMotion, SduiJson.decodeFromString(SduiJson.encodeToString(buttonWithMotion)))
        assertEquals(tagWithMotion, SduiJson.decodeFromString(SduiJson.encodeToString(tagWithMotion)))
    }

    @Test
    fun productCardAndFloatingCartRoundTrip() {
        val motion =
            MotionSpec(
                enter = MotionTransition.SLIDE_UP,
                exit = MotionTransition.FADE_IN,
                durationToken = MotionDurationToken.NORMAL,
                staggerMs = 30,
                loopEffect = MotionLoopEffect.SHIMMER,
            )
        val productCard =
            ProductCardSnippetData(
                productId = "sku_101",
                title = TextAtom(text = "Organic Hass Avocados"),
                subtitle = TextAtom(text = "2 pcs · 300 g"),
                price = TextAtom(text = "$4.49"),
                originalPrice = TextAtom(text = "$5.99", strikeThrough = true),
                unitPriceCents = 449,
                badge = TagAtom(label = "25% OFF", variant = TagVariant.BRAND, motion = motion),
                image = ImageAtom(url = "https://cdn.abeta.dev/avocado.png", widthDp = 96, heightDp = 96),
                stepper = StepperAtom(fieldId = "qty_avocado", quantity = 1),
                rating = TextAtom(text = "4.9"),
                onClick = sampleAction,
                layout = sampleLayout,
            )
        val encodedCard = SduiJson.encodeToString(productCard)
        assertTrue(encodedCard.contains("\"product_id\":\"sku_101\""))
        assertTrue(encodedCard.contains("\"original_price\""))
        assertTrue(encodedCard.contains("\"unit_price_cents\":449"))
        assertEquals(productCard, SduiJson.decodeFromString<ProductCardSnippetData>(encodedCard))
        assertEquals("product_card", SnippetTypes.PRODUCT_CARD)

        val envelope =
            SnippetEnvelope(
                snippetId = "prod_snip_1",
                snippetType = SnippetTypes.PRODUCT_CARD,
                data = SduiJson.encodeToJsonElement(productCard).jsonObject,
                motion = motion,
            )
        val floatingCart =
            FloatingCartSpec(
                label = "2 items · $8.98",
                etaBadge = TagAtom(label = "8 min", variant = TagVariant.SUCCESS, motion = motion),
                cta = ButtonAtom(label = "Checkout", motion = motion),
                motion = motion,
            )
        val page =
            PageResponse(
                pageId = "groceries",
                snippets = listOf(envelope),
                bottomBar = BottomBarSpec(primaryButton = ButtonAtom(label = "Checkout"), motion = motion),
                floatingCart = floatingCart,
            )
        val encodedPage = SduiJson.encodeToString(page)
        assertTrue(encodedPage.contains("\"floating_cart\""))
        assertTrue(encodedPage.contains("\"eta_badge\""))
        assertEquals(page, SduiJson.decodeFromString<PageResponse>(encodedPage))
    }
}
