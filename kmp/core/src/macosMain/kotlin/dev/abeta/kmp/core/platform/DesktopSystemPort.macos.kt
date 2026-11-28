package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AppKit.NSApp
import platform.AppKit.NSCriticalRequest
import platform.AppKit.NSInformationalRequest
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

private const val MAC_PREF_PREFIX = "x-apple.systempreferences:com.apple.preference."

internal fun resolveMacOsSystemSettingsUri(section: OsSettingsSection): String =
    when (section) {
        OsSettingsSection.GENERAL -> "${MAC_PREF_PREFIX}general"
        OsSettingsSection.PRIVACY_MICROPHONE -> "${MAC_PREF_PREFIX}security?Privacy_Microphone"
        OsSettingsSection.PRIVACY_ACCESSIBILITY -> "${MAC_PREF_PREFIX}security?Privacy_Accessibility"
        OsSettingsSection.NOTIFICATIONS -> "${MAC_PREF_PREFIX}notifications"
        OsSettingsSection.NETWORK -> "${MAC_PREF_PREFIX}network"
        OsSettingsSection.DISPLAY -> "${MAC_PREF_PREFIX}displays"
    }

internal fun resolveMacOsTrayBadgeLabel(state: DesktopTrayState): String? =
    when {
        state.badgeCount > 0 -> state.badgeCount.toString()
        state.status == TrayStatusKind.ERROR || state.status == TrayStatusKind.WARNING -> "!"
        state.status == TrayStatusKind.SYNCING -> "..."
        else -> null
    }

internal class MacOsDesktopSystemPort : DesktopSystemPort {
    private val _trayState = MutableStateFlow<DesktopTrayState?>(null)

    /** Latest [DesktopTrayState] applied via [updateTrayState]. */
    val trayState: StateFlow<DesktopTrayState?> = _trayState.asStateFlow()

    override val isSupported: Boolean = true

    override suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit> {
        _trayState.value = state
        val app = NSApp
        if (app != null) {
            app.dockTile.setBadgeLabel(resolveMacOsTrayBadgeLabel(state))
        }
        return AppResult.Success(Unit)
    }

    override suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit> {
        val uri = resolveMacOsSystemSettingsUri(section)
        val nsUrl =
            NSURL.URLWithString(uri)
                ?: return AppResult.Failure(UnexpectedError(message = "Invalid macOS settings URI: $uri"))
        return if (NSWorkspace.sharedWorkspace.openURL(nsUrl)) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                UnexpectedError(message = "NSWorkspace failed to open macOS System Settings for $section."),
            )
        }
    }

    override suspend fun setBadgeCount(count: Int): AppResult<Unit> {
        val app =
            NSApp
                ?: return AppResult.Failure(
                    ForbiddenError(
                        resource = "desktop_system",
                        message = "AppKit NSApplication is not active in this headless macOS process.",
                    ),
                )
        val label = if (count > 0) count.toString() else null
        app.dockTile.setBadgeLabel(label)
        return AppResult.Success(Unit)
    }

    override suspend fun requestUserAttention(critical: Boolean): AppResult<Unit> {
        val app =
            NSApp
                ?: return AppResult.Failure(
                    ForbiddenError(
                        resource = "desktop_system",
                        message = "AppKit NSApplication is not active in this headless macOS process.",
                    ),
                )
        val requestType = if (critical) NSCriticalRequest else NSInformationalRequest
        app.requestUserAttention(requestType)
        return AppResult.Success(Unit)
    }
}

/** Creates a native AppKit [DesktopSystemPort] for macOS desktop hosts. */
actual fun createPlatformDesktopSystemPort(): DesktopSystemPort = MacOsDesktopSystemPort()
