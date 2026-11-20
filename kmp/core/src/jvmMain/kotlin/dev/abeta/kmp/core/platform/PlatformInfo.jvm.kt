package dev.abeta.kmp.core.platform

/** Returns the JVM host [PlatformInfo], distinguishing Android ART and Wear OS when present. */
actual fun currentPlatformInfo(): PlatformInfo {
    val vmName = System.getProperty("java.vm.name").orEmpty().lowercase()
    val formFactor = System.getProperty("abeta.device.form_factor").orEmpty().lowercase()
    val osName = System.getProperty("os.name").orEmpty().ifBlank { "JVM" }
    val kind =
        when {
            vmName.contains("dalvik") && formFactor == "watch" -> PlatformKind.WEAR_OS
            vmName.contains("dalvik") -> PlatformKind.ANDROID
            formFactor == "wear_os" -> PlatformKind.WEAR_OS
            else -> PlatformKind.JVM_DESKTOP
        }
    return PlatformInfo(kind = kind, name = osName)
}
