package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

/**
 * Facade providing built-in offline SDUI sample pages and shared builder primitives.
 */
@Suppress("MagicNumber")
internal object SampleScreens {
    internal val cardLayout =
        LayoutConfig(
            padding = SpacingValues(all = 16),
            margin = SpacingValues(horizontal = 16, vertical = 4),
            bgColor = ColorTokenRef(token = "surface.primary"),
            cornerRadius = CornerRadiusSpec(all = 12),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef(token = "border.subtle")),
            fillMaxWidth = true,
        )

    internal inline fun <reified T> encode(value: T): JsonObject {
        val element = SduiJson.encodeToJsonElement(serializer<T>(), value)
        return element as JsonObject
    }

    internal fun text(
        value: String,
        typography: String,
        color: String = "text.primary",
    ): TextAtom =
        TextAtom(
            text = value,
            color = ColorTokenRef(token = color),
            typography = TypographyTokenRef(token = typography),
        )

    internal fun navAction(
        id: String,
        route: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.NAVIGATE,
            payload = buildJsonObject { put("route", route) },
        )

    internal fun backAction(id: String): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.BACK_PRESS,
            payload = buildJsonObject { put("steps", 1) },
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
        )
}
