package dev.abeta.kmp.state.storage

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

internal actual fun createPlatformSettings(name: String): Settings {
    val sanitized = name.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "abeta_kmp" }
    return PreferencesSettings(Preferences.userRoot().node("dev/abeta/kmp/$sanitized"))
}
