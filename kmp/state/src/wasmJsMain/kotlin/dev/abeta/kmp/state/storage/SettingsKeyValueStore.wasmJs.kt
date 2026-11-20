package dev.abeta.kmp.state.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

internal actual fun createPlatformSettings(name: String): Settings = StorageSettings()
