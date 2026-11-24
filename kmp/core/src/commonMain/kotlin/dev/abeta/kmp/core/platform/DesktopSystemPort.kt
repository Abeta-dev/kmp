package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult

/**
 * Target system preferences / settings pane requested via [DesktopSystemPort.openOsSettings].
 */
enum class OsSettingsSection {
    GENERAL,
    PRIVACY_MICROPHONE,
    PRIVACY_ACCESSIBILITY,
    NOTIFICATIONS,
    NETWORK,
    DISPLAY,
}

/**
 * Visual status category for a desktop menu bar / system tray item.
 */
enum class TrayStatusKind {
    IDLE,
    ACTIVE,
    SYNCING,
    WARNING,
    ERROR,
}

/**
 * Immutable presentation snapshot applied to a desktop tray or menu bar indicator.
 */
data class DesktopTrayState(
    val tooltip: String,
    val status: TrayStatusKind = TrayStatusKind.IDLE,
    val badgeCount: Int = 0,
)

/**
 * Dependency-inverted port for desktop OS integrations (tray status, settings deep links, badges, attention).
 */
interface DesktopSystemPort {
    /** Returns `true` when the current host supports interactive desktop OS integrations. */
    val isSupported: Boolean

    /** Updates the desktop tray / menu bar tooltip, status indicator, and optional badge count. */
    suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit>

    /** Opens the host OS settings application directly to [section]. */
    suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit>

    /** Updates the application dock or taskbar badge count (0 clears the badge). */
    suspend fun setBadgeCount(count: Int): AppResult<Unit>

    /** Requests user attention in the dock or taskbar; [critical] escalates urgency when supported. */
    suspend fun requestUserAttention(critical: Boolean = false): AppResult<Unit>
}

/**
 * Fallback [DesktopSystemPort] for mobile, wearable, browser, or headless targets.
 */
object UnavailableDesktopSystemPort : DesktopSystemPort {
    override val isSupported: Boolean = false

    override suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit> =
        unavailableFailure("Desktop tray updates are not supported on this host.")

    override suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit> =
        unavailableFailure("Desktop OS settings launch is not supported on this host.")

    override suspend fun setBadgeCount(count: Int): AppResult<Unit> =
        unavailableFailure("Desktop dock/taskbar badges are not supported on this host.")

    override suspend fun requestUserAttention(critical: Boolean): AppResult<Unit> =
        unavailableFailure("Desktop user attention requests are not supported on this host.")

    private fun unavailableFailure(message: String): AppResult<Unit> =
        AppResult.Failure(ForbiddenError(resource = "desktop_system", message = message))
}

/**
 * Deterministic test double and preview recorder capturing all [DesktopSystemPort] calls.
 */
class RecordingDesktopSystemPort : DesktopSystemPort {
    private val _trayStates = mutableListOf<DesktopTrayState>()
    private val _openedSettings = mutableListOf<OsSettingsSection>()
    private val _badgeCounts = mutableListOf<Int>()
    private val _attentionRequests = mutableListOf<Boolean>()

    override val isSupported: Boolean = true

    /** Immutable snapshot of recorded [updateTrayState] calls. */
    val trayStates: List<DesktopTrayState>
        get() = _trayStates.toList()

    /** Immutable snapshot of recorded [openOsSettings] calls. */
    val openedSettings: List<OsSettingsSection>
        get() = _openedSettings.toList()

    /** Immutable snapshot of recorded [setBadgeCount] calls. */
    val badgeCounts: List<Int>
        get() = _badgeCounts.toList()

    /** Immutable snapshot of recorded [requestUserAttention] `critical` flags. */
    val attentionRequests: List<Boolean>
        get() = _attentionRequests.toList()

    override suspend fun updateTrayState(state: DesktopTrayState): AppResult<Unit> {
        _trayStates.add(state)
        return AppResult.Success(Unit)
    }

    override suspend fun openOsSettings(section: OsSettingsSection): AppResult<Unit> {
        _openedSettings.add(section)
        return AppResult.Success(Unit)
    }

    override suspend fun setBadgeCount(count: Int): AppResult<Unit> {
        _badgeCounts.add(count)
        return AppResult.Success(Unit)
    }

    override suspend fun requestUserAttention(critical: Boolean): AppResult<Unit> {
        _attentionRequests.add(critical)
        return AppResult.Success(Unit)
    }

    /** Clears all recorded desktop system interactions. */
    fun clear() {
        _trayStates.clear()
        _openedSettings.clear()
        _badgeCounts.clear()
        _attentionRequests.clear()
    }
}

/** Creates the platform-native [DesktopSystemPort] for the current KMP target. */
expect fun createPlatformDesktopSystemPort(): DesktopSystemPort
