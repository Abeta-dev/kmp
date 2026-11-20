package dev.abeta.kmp.state.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Observes changes to the string stored for [key], falling back to [defaultValue]. */
fun PreferenceStore.observeString(
    key: PreferenceKey<String>,
    defaultValue: String? = null,
): Flow<String?> = observeRaw(key.name).map { raw -> raw ?: defaultValue }.distinctUntilChanged()

/** Returns the integer stored for [key], or [defaultValue] if absent or unparseable. */
fun PreferenceStore.getInt(
    key: PreferenceKey<Int>,
    defaultValue: Int? = null,
): Int? = getString(PreferenceKey(key.name))?.toIntOrNull() ?: defaultValue

/** Stores an integer [value] under [key]. */
fun PreferenceStore.putInt(
    key: PreferenceKey<Int>,
    value: Int,
) {
    putString(PreferenceKey(key.name), value.toString())
}

/** Observes changes to the integer stored for [key], falling back to [defaultValue]. */
fun PreferenceStore.observeInt(
    key: PreferenceKey<Int>,
    defaultValue: Int? = null,
): Flow<Int?> =
    observeRaw(key.name)
        .map { raw -> raw?.toIntOrNull() ?: defaultValue }
        .distinctUntilChanged()

/** Returns the long stored for [key], or [defaultValue] if absent or unparseable. */
fun PreferenceStore.getLong(
    key: PreferenceKey<Long>,
    defaultValue: Long? = null,
): Long? = getString(PreferenceKey(key.name))?.toLongOrNull() ?: defaultValue

/** Stores a long [value] under [key]. */
fun PreferenceStore.putLong(
    key: PreferenceKey<Long>,
    value: Long,
) {
    putString(PreferenceKey(key.name), value.toString())
}

/** Observes changes to the long stored for [key], falling back to [defaultValue]. */
fun PreferenceStore.observeLong(
    key: PreferenceKey<Long>,
    defaultValue: Long? = null,
): Flow<Long?> =
    observeRaw(key.name)
        .map { raw -> raw?.toLongOrNull() ?: defaultValue }
        .distinctUntilChanged()
