package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import java.awt.SystemTray
import java.awt.Taskbar
import java.io.IOException

private const val MAC_PREF_PREFIX = "x-apple.systempreferences:com.apple.preference."

internal fun resolveOsSettingsUri(
    osName: String,
    section: OsSettingsSection,
): String? {
    val normalizedOs = osName.lowercase()
    return when {
        normalizedOs.contains("mac") || normalizedOs.contains("darwin") -> resolveMacOsSettingsUri(section)
        normalizedOs.contains("win") -> resolveWindowsSettingsUri(section)
        else -> null
    }
}

internal fun resolveLinuxSettingsCommands(section: OsSettingsSection): List<List<String>> =
    when (section) {
        OsSettingsSection.GENERAL -> {
            listOf(
                listOf("gnome-control-center", "system"),
                listOf("gnome-control-center"),
                listOf("systemsettings"),
                listOf("systemsettings5"),
                listOf("xdg-open", "settings://"),
            )
        }

        OsSettingsSection.PRIVACY_MICROPHONE -> {
            listOf(
                listOf("gnome-control-center", "microphone"),
                listOf("gnome-control-center", "privacy"),
                listOf("systemsettings", "kcm_pulseaudio"),
                listOf("systemsettings5", "kcm_pulseaudio"),
                listOf("xdg-open", "settings://privacy"),
            )
        }

        OsSettingsSection.PRIVACY_ACCESSIBILITY -> {
            listOf(
                listOf("gnome-control-center", "universal-access"),
                listOf("systemsettings", "kcm_access"),
                listOf("systemsettings5", "kcm_access"),
                listOf("xdg-open", "settings://accessibility"),
            )
        }

        OsSettingsSection.NOTIFICATIONS -> {
            listOf(
                listOf("gnome-control-center", "notifications"),
                listOf("systemsettings", "kcm_notifications"),
                listOf("systemsettings5", "kcm_notifications"),
                listOf("xdg-open", "settings://notifications"),
            )
        }

        OsSettingsSection.NETWORK -> {
            listOf(
                listOf("gnome-control-center", "network"),
                listOf("systemsettings", "kcm_networkmanagement"),
                listOf("systemsettings5", "kcm_networkmanagement"),
                listOf("xdg-open", "settings://network"),
            )
        }

        OsSettingsSection.DISPLAY -> {
            listOf(
                listOf("gnome-control-center", "display"),
                listOf("systemsettings", "kcm_kscreen"),
                listOf("systemsettings5", "kcm_kscreen"),
                listOf("xdg-open", "settings://display"),
            )
        }
    }

internal fun formatDesktopTrayTooltip(state: DesktopTrayState): String {
    val base = state.tooltip.trim()
    val statusLabel = state.status.name.lowercase()
    return when {
        state.status == TrayStatusKind.IDLE -> base
        base.isEmpty() -> statusLabel
        else -> "$base [$statusLabel]"
    }
}

internal fun resolveTrayTaskbarBadgeText(state: DesktopTrayState): String? =
    when {
        state.badgeCount > 0 -> state.badgeCount.toString()
        state.status == TrayStatusKind.ERROR -> "!"
        state.status == TrayStatusKind.WARNING -> "!"
        state.status == TrayStatusKind.SYNCING -> "..."
        else -> null
    }

private fun resolveMacOsSettingsUri(section: OsSettingsSection): String =
    when (section) {
        OsSettingsSection.GENERAL -> "${MAC_PREF_PREFIX}general"
        OsSettingsSection.PRIVACY_MICROPHONE -> "${MAC_PREF_PREFIX}security?Privacy_Microphone"
        OsSettingsSection.PRIVACY_ACCESSIBILITY -> "${MAC_PREF_PREFIX}security?Privacy_Accessibility"
        OsSettingsSection.NOTIFICATIONS -> "${MAC_PREF_PREFIX}notifications"
        OsSettingsSection.NETWORK -> "${MAC_PREF_PREFIX}network"
        OsSettingsSection.DISPLAY -> "${MAC_PREF_PREFIX}displays"
    }

private fun resolveWindowsSettingsUri(section: OsSettingsSection): String =
    when (section) {
        OsSettingsSection.GENERAL -> "ms-settings:about"
        OsSettingsSection.PRIVACY_MICROPHONE -> "ms-settings:privacy-microphone"
        OsSettingsSection.PRIVACY_ACCESSIBILITY -> "ms-settings:easeofaccess"
        OsSettingsSection.NOTIFICATIONS -> "ms-settings:notifications"
        OsSettingsSection.NETWORK -> "ms-settings:network"
        OsSettingsSection.DISPLAY -> "ms-settings:display"
    }

@Suppress("TooManyFunctions")
internal class JvmDesktopSystemPort : DesktopSystemPort {
    override val isSupported: Boolean
        get() = !isJvmHeadlessSafely()

    override suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit> {
        if (!isSupported) {
            return desktopForbidden("Desktop tray is unavailable in headless JVM.")
        }
        val trayUpdated = applySystemTrayState(state)
        val badgeUpdated = applyTaskbarBadgeIfSupported(resolveTrayTaskbarBadgeText(state))
        return if (trayUpdated || badgeUpdated) {
            AppResult.Success(Unit)
        } else {
            desktopForbidden(
                "No desktop tray icon or taskbar badge is available to reflect tray state (${state.status}).",
            )
        }
    }

    override suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit> =
        if (!isSupported) {
            desktopForbidden("OS settings cannot be opened in headless JVM.")
        } else {
            val osName = System.getProperty("os.name").orEmpty()
            val uri = resolveOsSettingsUri(osName = osName, section = section)
            when {
                uri != null -> launchOsSettingsUri(osName = osName, uri = uri)
                osName.lowercase().contains("linux") -> launchLinuxSettings(section)
                else -> desktopForbidden("OS settings URI for $section is not supported on '$osName'.")
            }
        }

    override suspend fun setBadgeCount(count: Int): AppResult<Unit> {
        if (!isSupported) {
            return desktopForbidden("Taskbar badges are unavailable in headless JVM.")
        }
        return runTaskbarFeature(
            feature = Taskbar.Feature.ICON_BADGE_TEXT,
            unsupportedMessage = "Taskbar badge text is not supported on this desktop environment.",
        ) { taskbar ->
            val badge = if (count > 0) count.toString() else null
            taskbar.setIconBadge(badge)
        }
    }

    override suspend fun requestUserAttention(critical: Boolean): AppResult<Unit> {
        if (!isSupported) {
            return desktopForbidden("User attention requests are unavailable in headless JVM.")
        }
        return runTaskbarFeature(
            feature = Taskbar.Feature.USER_ATTENTION,
            unsupportedMessage = "Taskbar user attention is not supported on this desktop environment.",
        ) { taskbar ->
            taskbar.requestUserAttention(true, critical)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun applySystemTrayState(state: DesktopTrayState): Boolean =
        try {
            if (!SystemTray.isSupported()) {
                false
            } else {
                val icon = SystemTray.getSystemTray().trayIcons.firstOrNull()
                if (icon != null) {
                    icon.toolTip = formatDesktopTrayTooltip(state)
                    true
                } else {
                    false
                }
            }
        } catch (_: Throwable) {
            false
        }

    @Suppress("TooGenericExceptionCaught")
    private fun applyTaskbarBadgeIfSupported(badgeText: String?): Boolean =
        try {
            if (!Taskbar.isTaskbarSupported()) {
                false
            } else {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
                    taskbar.setIconBadge(badgeText)
                    true
                } else {
                    false
                }
            }
        } catch (_: Throwable) {
            false
        }

    @Suppress("TooGenericExceptionCaught")
    private inline fun runTaskbarFeature(
        feature: Taskbar.Feature,
        unsupportedMessage: String,
        action: (Taskbar) -> Unit,
    ): AppResult<Unit> =
        try {
            if (!Taskbar.isTaskbarSupported()) {
                desktopForbidden(unsupportedMessage)
            } else {
                val taskbar = Taskbar.getTaskbar()
                if (!taskbar.isSupported(feature)) {
                    desktopForbidden(unsupportedMessage)
                } else {
                    action(taskbar)
                    AppResult.Success(Unit)
                }
            }
        } catch (err: SecurityException) {
            AppResult.Failure(
                UnexpectedError(message = "Desktop taskbar operation denied: ${err.message}", cause = err),
            )
        } catch (err: Throwable) {
            AppResult.Failure(
                ForbiddenError(resource = "desktop_system", message = unsupportedMessage, cause = err),
            )
        }

    private fun launchLinuxSettings(section: OsSettingsSection): AppResult<Unit> {
        var result: AppResult<Unit>? = null
        var lastError: Throwable? = null
        val iterator = resolveLinuxSettingsCommands(section).iterator()
        while (result == null && iterator.hasNext()) {
            val command = iterator.next()
            try {
                ProcessBuilder(command).start()
                result = AppResult.Success(Unit)
            } catch (err: IOException) {
                lastError = err
            } catch (err: SecurityException) {
                result =
                    AppResult.Failure(
                        UnexpectedError(message = "OS settings launch denied: ${err.message}", cause = err),
                    )
            }
        }
        return result ?: AppResult.Failure(
            ForbiddenError(
                resource = "desktop_system",
                message = "No supported Linux settings utility found for $section.",
                cause = lastError,
            ),
        )
    }

    private fun launchOsSettingsUri(
        osName: String,
        uri: String,
    ): AppResult<Unit> {
        val normalizedOs = osName.lowercase()
        val command =
            when {
                normalizedOs.contains("mac") || normalizedOs.contains("darwin") -> listOf("open", uri)
                normalizedOs.contains("win") -> listOf("cmd", "/c", "start", "", uri)
                else -> return desktopForbidden("Unsupported desktop OS for settings launch: $osName")
            }
        return try {
            ProcessBuilder(command).start()
            AppResult.Success(Unit)
        } catch (err: IOException) {
            AppResult.Failure(UnexpectedError(message = "Failed to open OS settings: ${err.message}", cause = err))
        } catch (err: SecurityException) {
            AppResult.Failure(UnexpectedError(message = "OS settings launch denied: ${err.message}", cause = err))
        }
    }

    private fun desktopForbidden(message: String): AppResult<Unit> =
        AppResult.Failure(ForbiddenError(resource = "desktop_system", message = message))
}

/** Creates a JVM desktop [DesktopSystemPort] supporting macOS, Windows, and Linux desktops. */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = JvmDesktopSystemPort()
