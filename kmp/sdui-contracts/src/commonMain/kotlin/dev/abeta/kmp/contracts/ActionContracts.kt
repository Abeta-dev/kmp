package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Declarative client/server validation rule attached to an [ActionRef].
 */
@Serializable
data class ValidationRuleContract(
    @SerialName("field_id")
    val fieldId: String,
    val rule: String,
    val value: String? = null,
    val message: String,
)

/**
 * Lightweight, serializable descriptor of an action emitted by a UI atom or page.
 *
 * UI atoms carry and emit [ActionRef] instances without knowing how actions are executed.
 */
@Serializable
data class ActionRef(
    val id: String? = null,
    val type: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val validations: List<ValidationRuleContract> = emptyList(),
    @SerialName("secondary_actions")
    val secondaryActions: List<ActionRef> = emptyList(),
    val tracking: TrackingContract? = null,
)

/**
 * Analytics tracking descriptor attached to pages, snippets, or interactive atoms.
 */
@Serializable
data class TrackingContract(
    @SerialName("event_name")
    val eventName: String,
    val properties: Map<String, String> = emptyMap(),
)
