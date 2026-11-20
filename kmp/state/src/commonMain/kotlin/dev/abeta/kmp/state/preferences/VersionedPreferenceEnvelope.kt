package dev.abeta.kmp.state.preferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Versioned JSON envelope supporting schema evolution when persisting complex preference objects.
 */
@Serializable
data class VersionedPreferenceEnvelope(
    @SerialName("_v")
    val version: Int,
    @SerialName("_data")
    val data: JsonElement,
)
