package dev.abeta.kmp.actions.model

import kotlinx.serialization.json.JsonElement

/**
 * Immutable execution context passed to action validators and handlers.
 *
 * @property sessionState Current key-value session or form state visible to actions.
 * @property metadata Additional environmental or page metadata (e.g. `page_id`, `locale`).
 */
data class ActionContext(
    val sessionState: Map<String, JsonElement> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)
