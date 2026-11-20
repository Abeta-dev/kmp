package dev.abeta.kmp.state.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Returns the boolean stored for [key], or [defaultValue] if absent or unparseable. */
fun PreferenceStore.getBoolean(
    key: PreferenceKey<Boolean>,
    defaultValue: Boolean? = null,
): Boolean? = getString(PreferenceKey(key.name))?.toBooleanStrictOrNull() ?: defaultValue

/** Stores a boolean [value] under [key]. */
fun PreferenceStore.putBoolean(
    key: PreferenceKey<Boolean>,
    value: Boolean,
) {
    putString(PreferenceKey(key.name), value.toString())
}

/** Observes changes to the boolean stored for [key], falling back to [defaultValue]. */
fun PreferenceStore.observeBoolean(
    key: PreferenceKey<Boolean>,
    defaultValue: Boolean? = null,
): Flow<Boolean?> =
    observeRaw(key.name)
        .map { raw -> raw?.toBooleanStrictOrNull() ?: defaultValue }
        .distinctUntilChanged()

/** Returns the double stored for [key], or [defaultValue] if absent or unparseable. */
fun PreferenceStore.getDouble(
    key: PreferenceKey<Double>,
    defaultValue: Double? = null,
): Double? = getString(PreferenceKey(key.name))?.toDoubleOrNull() ?: defaultValue

/** Stores a double [value] under [key]. */
fun PreferenceStore.putDouble(
    key: PreferenceKey<Double>,
    value: Double,
) {
    putString(PreferenceKey(key.name), value.toString())
}

/** Observes changes to the double stored for [key], falling back to [defaultValue]. */
fun PreferenceStore.observeDouble(
    key: PreferenceKey<Double>,
    defaultValue: Double? = null,
): Flow<Double?> =
    observeRaw(key.name)
        .map { raw -> raw?.toDoubleOrNull() ?: defaultValue }
        .distinctUntilChanged()

/**
 * Stores a `@Serializable` object under [key] wrapped in a schema-versioned [VersionedPreferenceEnvelope].
 */
fun <T> PreferenceStore.putObject(
    key: PreferenceKey<T>,
    value: T,
    serializer: KSerializer<T>,
    version: Int = 1,
    json: Json = DefaultPreferenceJson,
) {
    val payload = json.encodeToJsonElement(serializer, value)
    val envelope = VersionedPreferenceEnvelope(version = version, data = payload)
    putString(PreferenceKey(key.name), json.encodeToString(VersionedPreferenceEnvelope.serializer(), envelope))
}

/**
 * Retrieves a schema-versioned `@Serializable` object from [key], invoking [onMigrate] if the
 * persisted schema version differs from [version] and re-persisting the migrated representation.
 */
@Suppress("ReturnCount")
fun <T> PreferenceStore.getObject(
    key: PreferenceKey<T>,
    serializer: KSerializer<T>,
    version: Int = 1,
    json: Json = DefaultPreferenceJson,
    onMigrate: ((oldData: JsonElement, oldVersion: Int) -> T)? = null,
): T? {
    val raw = getString(PreferenceKey(key.name)) ?: return null
    val envelope = runCatching { json.decodeFromString(VersionedPreferenceEnvelope.serializer(), raw) }.getOrNull()
    if (envelope == null) {
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }
    if (envelope.version == version) {
        return runCatching { json.decodeFromJsonElement(serializer, envelope.data) }.getOrNull()
    }
    val migrated = onMigrate?.invoke(envelope.data, envelope.version) ?: return null
    putObject(key = key, value = migrated, serializer = serializer, version = version, json = json)
    return migrated
}

/**
 * Reactive versioned object observation over [PreferenceStore].
 */
fun <T> PreferenceStore.observeObject(
    key: PreferenceKey<T>,
    serializer: KSerializer<T>,
    version: Int = 1,
    json: Json = DefaultPreferenceJson,
    onMigrate: ((oldData: JsonElement, oldVersion: Int) -> T)? = null,
): Flow<T?> =
    observeString(PreferenceKey(key.name))
        .map {
            getObject(
                key = key,
                serializer = serializer,
                version = version,
                json = json,
                onMigrate = onMigrate,
            )
        }.distinctUntilChanged()
