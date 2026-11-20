package dev.abeta.kmp.state.flags

import dev.abeta.kmp.core.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Strongly-typed feature flag key bundling flag [name] and compile-time [defaultValue].
 */
data class FeatureFlagKey<T>(
    val name: String,
    val defaultValue: T,
)

/**
 * Port for querying runtime feature flags and remote configuration values.
 */
interface FeatureFlagProvider {
    /** Returns the boolean value for [key], or [defaultValue] if missing or incompatible. */
    fun isEnabled(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean

    /** Returns the string value for [key], or [defaultValue] if missing or incompatible. */
    fun getString(
        key: String,
        defaultValue: String = "",
    ): String

    /** Returns the integer value for [key], or [defaultValue] if missing or incompatible. */
    fun getInt(
        key: String,
        defaultValue: Int = 0,
    ): Int

    /** Returns the double value for [key], or [defaultValue] if missing or incompatible. */
    fun getDouble(
        key: String,
        defaultValue: Double = 0.0,
    ): Double
}

/**
 * Reactive extension of [FeatureFlagProvider] emitting live updates when remote or local overrides change.
 */
interface ObservableFeatureFlagProvider : FeatureFlagProvider {
    /** Observable snapshot of raw JSON flag values keyed by flag name. */
    val flagsState: StateFlow<Map<String, JsonElement>>

    /** Observes boolean flag [key], falling back to [defaultValue]. */
    fun observeBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Flow<Boolean> =
        flagsState
            .map { map -> (map[key] as? JsonPrimitive)?.booleanOrNull ?: defaultValue }
            .distinctUntilChanged()

    /** Observes string flag [key], falling back to [defaultValue]. */
    fun observeString(
        key: String,
        defaultValue: String = "",
    ): Flow<String> =
        flagsState
            .map { map -> (map[key] as? JsonPrimitive)?.contentOrNull ?: defaultValue }
            .distinctUntilChanged()

    /** Observes integer flag [key], falling back to [defaultValue]. */
    fun observeInt(
        key: String,
        defaultValue: Int = 0,
    ): Flow<Int> =
        flagsState
            .map { map -> (map[key] as? JsonPrimitive)?.intOrNull ?: defaultValue }
            .distinctUntilChanged()

    /** Observes double flag [key], falling back to [defaultValue]. */
    fun observeDouble(
        key: String,
        defaultValue: Double = 0.0,
    ): Flow<Double> =
        flagsState
            .map { map -> (map[key] as? JsonPrimitive)?.doubleOrNull ?: defaultValue }
            .distinctUntilChanged()
}

/** Evaluates a typed boolean [FeatureFlagKey]. */
fun FeatureFlagProvider.isEnabled(flag: FeatureFlagKey<Boolean>): Boolean = isEnabled(flag.name, flag.defaultValue)

/** Evaluates a typed string [FeatureFlagKey]. */
fun FeatureFlagProvider.getString(flag: FeatureFlagKey<String>): String = getString(flag.name, flag.defaultValue)

/** Evaluates a typed integer [FeatureFlagKey]. */
fun FeatureFlagProvider.getInt(flag: FeatureFlagKey<Int>): Int = getInt(flag.name, flag.defaultValue)

/** Evaluates a typed double [FeatureFlagKey]. */
fun FeatureFlagProvider.getDouble(flag: FeatureFlagKey<Double>): Double = getDouble(flag.name, flag.defaultValue)

/**
 * Thread-safe reactive in-memory [ObservableFeatureFlagProvider] for overrides, previews, and tests.
 */
class InMemoryFeatureFlagProvider(
    initialFlags: Map<String, Any> = emptyMap(),
) : ObservableFeatureFlagProvider {
    private val _flagsState = MutableStateFlow(initialFlags.mapValues { (_, v) -> toJsonElement(v) })

    override val flagsState: StateFlow<Map<String, JsonElement>> = _flagsState.asStateFlow()

    override fun isEnabled(
        key: String,
        defaultValue: Boolean,
    ): Boolean = (_flagsState.value[key] as? JsonPrimitive)?.booleanOrNull ?: defaultValue

    override fun getString(
        key: String,
        defaultValue: String,
    ): String =
        (_flagsState.value[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: defaultValue

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = (_flagsState.value[key] as? JsonPrimitive)?.intOrNull ?: defaultValue

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = (_flagsState.value[key] as? JsonPrimitive)?.doubleOrNull ?: defaultValue

    /** Sets or replaces a flag [value] under [key]. */
    fun setFlag(
        key: String,
        value: Any,
    ) {
        _flagsState.update { current -> current + (key to toJsonElement(value)) }
    }

    /** Removes the flag entry for [key], if present. */
    fun removeFlag(key: String) {
        _flagsState.update { current -> current - key }
    }

    /** Clears all stored flags. */
    fun clear() {
        _flagsState.value = emptyMap()
    }
}

/**
 * Durable [ObservableFeatureFlagProvider] persisting remote configuration JSON bundles in a [KeyValueStore].
 */
class PersistentFeatureFlagStore(
    private val backingStore: KeyValueStore,
    private val storageKey: String = DEFAULT_STORAGE_KEY,
    private val json: Json = DefaultFlagJson,
) : ObservableFeatureFlagProvider {
    private val _flagsState = MutableStateFlow(loadPersisted())

    override val flagsState: StateFlow<Map<String, JsonElement>> = _flagsState.asStateFlow()

    override fun isEnabled(
        key: String,
        defaultValue: Boolean,
    ): Boolean = (_flagsState.value[key] as? JsonPrimitive)?.booleanOrNull ?: defaultValue

    override fun getString(
        key: String,
        defaultValue: String,
    ): String =
        (_flagsState.value[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: defaultValue

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = (_flagsState.value[key] as? JsonPrimitive)?.intOrNull ?: defaultValue

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = (_flagsState.value[key] as? JsonPrimitive)?.doubleOrNull ?: defaultValue

    /** Replaces the cached flag set with values parsed from a remote JSON object payload. */
    fun applyRemoteJson(rawJson: String): Boolean {
        val parsed = runCatching { json.decodeFromString<JsonObject>(rawJson) }.getOrNull() ?: return false
        val map = parsed.toMap()
        _flagsState.value = map
        backingStore.putString(storageKey, json.encodeToString(JsonObject.serializer(), JsonObject(map)))
        return true
    }

    /** Clears persisted and in-memory cached flags. */
    fun clear() {
        _flagsState.value = emptyMap()
        backingStore.remove(storageKey)
    }

    private fun loadPersisted(): Map<String, JsonElement> {
        val raw = backingStore.getString(storageKey) ?: return emptyMap()
        return runCatching { json.decodeFromString<JsonObject>(raw).toMap() }.getOrElse { emptyMap() }
    }

    companion object {
        const val DEFAULT_STORAGE_KEY: String = "abeta_remote_feature_flags"
    }
}

/**
 * Priority-ordered [FeatureFlagProvider] chain that resolves the first provider defining a key
 * before falling through to lower-priority layers (for example `debugOverrides -> remoteCache -> defaults`).
 */
class LayeredFeatureFlagProvider(
    private val layers: List<ObservableFeatureFlagProvider>,
) : FeatureFlagProvider {
    constructor(vararg layers: ObservableFeatureFlagProvider) : this(layers.toList())

    override fun isEnabled(
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val match = layers.firstNotNullOfOrNull { it.flagsState.value[key] as? JsonPrimitive }
        return match?.booleanOrNull ?: defaultValue
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ): String {
        val match = layers.firstNotNullOfOrNull { it.flagsState.value[key] as? JsonPrimitive }
        return match?.takeIf { it.isString }?.contentOrNull ?: defaultValue
    }

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int {
        val match = layers.firstNotNullOfOrNull { it.flagsState.value[key] as? JsonPrimitive }
        return match?.intOrNull ?: defaultValue
    }

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double {
        val match = layers.firstNotNullOfOrNull { it.flagsState.value[key] as? JsonPrimitive }
        return match?.doubleOrNull ?: defaultValue
    }

    /** Observes the highest-priority layer holding a boolean value for [key]. */
    fun observeBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Flow<Boolean> {
        if (layers.isEmpty()) return flowOf(defaultValue)
        return combine(layers.map { it.flagsState }) { snapshots ->
            val resolved = snapshots.firstNotNullOfOrNull { map -> (map[key] as? JsonPrimitive)?.booleanOrNull }
            resolved ?: defaultValue
        }.distinctUntilChanged()
    }
}

private fun toJsonElement(value: Any): JsonElement =
    when (value) {
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        else -> JsonPrimitive(value.toString())
    }

private val DefaultFlagJson: Json =
    Json {
        ignoreUnknownKeys = true
    }
