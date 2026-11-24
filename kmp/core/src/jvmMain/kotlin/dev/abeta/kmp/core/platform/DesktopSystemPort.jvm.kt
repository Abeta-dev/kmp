package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import java.awt.GraphicsEnvironment
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

internal class JvmDesktopSystemPort : DesktopSystemPort {
    override val isSupported: Boolean
        get() = !GraphicsEnvironment.isHeadless()

    override suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit> {
        if (!isSupported) {
            return desktopForbidden("Desktop tray is unavailable in headless JVM.")
        }
        applySystemTrayTooltip(state.tooltip)
        applyTaskbarBadgeIfSupported(state.badgeCount)
        return AppResult.Success(Unit)
    }

    override suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit> =
        if (!isSupported) {
            desktopForbidden("OS settings cannot be opened in headless JVM.")
        } else {
            val osName = System.getProperty("os.name").orEmpty()
            val uri = resolveOsSettingsUri(osName = osName, section = section)
            if (uri == null) {
                desktopForbidden("OS settings URI for $section is not supported on '$osName'.")
            } else {
                launchOsSettingsUri(osName = osName, uri = uri)
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

    private fun applySystemTrayTooltip(tooltip: String) {
        if (!SystemTray.isSupported()) return
        try {
            SystemTray
                .getSystemTray()
                .trayIcons
                .firstOrNull()
                ?.toolTip = tooltip
        } catch (_: UnsupportedOperationException) {
            // Tray icons may not be supported in restricted desktop sessions.
        } catch (_: SecurityException) {
            // Ignore tray access restrictions when running under a security policy.
        }
    }

    private fun applyTaskbarBadgeIfSupported(count: Int) {
        if (!Taskbar.isTaskbarSupported()) return
        try {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
                taskbar.setIconBadge(if (count > 0) count.toString() else null)
            }
        } catch (_: UnsupportedOperationException) {
            // Gracefully ignore when desktop environment declines badge updates.
        } catch (_: SecurityException) {
            // Gracefully ignore under restricted security policies.
        }
    }

    private inline fun runTaskbarFeature(
        feature: Taskbar.Feature,
        unsupportedMessage: String,
        action: (Taskbar) -> Unit,
    ): AppResult<Unit> {
        if (!Taskbar.isTaskbarSupported()) {
            return desktopForbidden(unsupportedMessage)
        }
        return try {
            val taskbar = Taskbar.getTaskbar()
            if (!taskbar.isSupported(feature)) {
                desktopForbidden(unsupportedMessage)
            } else {
                action(taskbar)
                AppResult.Success(Unit)
            }
        } catch (err: UnsupportedOperationException) {
            AppResult.Failure(
                ForbiddenError(resource = "desktop_system", message = unsupportedMessage, cause = err),
            )
        } catch (err: SecurityException) {
            AppResult.Failure(
                UnexpectedError(message = "Desktop taskbar operation denied: ${err.message}", cause = err),
            )
        }
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
