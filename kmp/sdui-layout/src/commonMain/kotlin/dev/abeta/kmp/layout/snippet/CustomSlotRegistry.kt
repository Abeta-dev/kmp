package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.abeta.kmp.contracts.ActionRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Runtime context passed to a host-registered [CustomSlotRenderer] for a `custom_slot` snippet.
 */
data class CustomSlotContext(
    val snippetId: String,
    val slotId: String,
    val slotType: String,
    val initialState: JsonObject,
    val formValues: Map<String, String>,
    val onFormValueChange: (String, String) -> Unit,
    val onAction: (ActionRef) -> Unit,
    val onEvent: Map<String, ActionRef>,
) {
    /**
     * Looks up the declarative [ActionRef] registered under [eventName] in [onEvent], optionally
     * merges [extraPayload] into its [ActionRef.payload] (with [extraPayload] keys overriding
     * existing payload keys), and dispatches the resulting action through [onAction].
     */
    fun emitEvent(
        eventName: String,
        extraPayload: JsonObject? = null,
    ) {
        val action = onEvent[eventName] ?: return
        val enrichedAction =
            if (extraPayload != null && extraPayload.isNotEmpty()) {
                val mergedPayload =
                    buildJsonObject {
                        action.payload.forEach { (key, value) -> put(key, value) }
                        extraPayload.forEach { (key, value) -> put(key, value) }
                    }
                action.copy(payload = mergedPayload)
            } else {
                action
            }
        onAction(enrichedAction)
    }
}

/**
 * Host-provided composable renderer bound to a `slot_type` key inside [CustomSlotRegistry].
 */
fun interface CustomSlotRenderer {
    /** Renders the host-specific UI surface for [context]. */
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    fun Render(context: CustomSlotContext)
}

/**
 * Immutable, OCP-compliant registry mapping `custom_slot` `slot_type` identifiers to
 * host-supplied [CustomSlotRenderer] implementations.
 */
class CustomSlotRegistry private constructor(
    private val renderers: Map<String, CustomSlotRenderer>,
) {
    /** Resolves the [CustomSlotRenderer] registered for [slotType], or `null` if unregistered. */
    fun resolve(slotType: String): CustomSlotRenderer? = renderers[slotType]

    /** Returns all registered `slot_type` identifiers in this snapshot. */
    fun registeredSlotTypes(): Set<String> = renderers.keys

    /** Creates a mutable [Builder] initialized with this registry's entries. */
    fun toBuilder(): Builder = Builder(renderers)

    /** Mutable builder for constructing an immutable [CustomSlotRegistry] snapshot. */
    class Builder internal constructor(
        initial: Map<String, CustomSlotRenderer> = emptyMap(),
    ) {
        private val renderers = initial.toMutableMap()

        /** Registers or overrides the [renderer] for [slotType]. */
        fun register(
            slotType: String,
            renderer: CustomSlotRenderer,
        ): Builder =
            apply {
                require(slotType.isNotBlank()) { "Custom slot type must not be blank." }
                renderers[slotType] = renderer
            }

        /** Builds an immutable [CustomSlotRegistry] snapshot. */
        fun build(): CustomSlotRegistry = CustomSlotRegistry(renderers.toMap())
    }

    companion object {
        /** Shared empty [CustomSlotRegistry] containing no registered host slots. */
        val Empty: CustomSlotRegistry = CustomSlotRegistry(emptyMap())

        /** Returns a new empty [Builder]. */
        fun builder(): Builder = Builder()
    }
}

/**
 * [ProvidableCompositionLocal] supplying a host [CustomSlotRegistry] to `custom_slot` snippet
 * renderers without altering the [SnippetRenderer.Render] interface contract.
 */
val LocalCustomSlotRegistry: ProvidableCompositionLocal<CustomSlotRegistry> =
    staticCompositionLocalOf { CustomSlotRegistry.Empty }
