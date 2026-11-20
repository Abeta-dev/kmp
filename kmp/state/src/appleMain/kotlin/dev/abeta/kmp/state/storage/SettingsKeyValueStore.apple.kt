package dev.abeta.kmp.state.storage

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

internal actual fun createPlatformSettings(name: String): Settings {
    val defaults = NSUserDefaults(suiteName = "dev.abeta.kmp.$name")
    return NSUserDefaultsSettings(defaults)
}
