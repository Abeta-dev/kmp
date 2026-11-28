package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.test.runTest
import platform.AppKit.NSApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacOsDesktopAndFilePickerTest {
    @Test
    fun macOsSettingsUrisCoverAllSections() {
        val uris = OsSettingsSection.entries.associateWith(::resolveMacOsSystemSettingsUri)
        assertEquals(OsSettingsSection.entries.size, uris.values.toSet().size)
        uris.values.forEach { uri ->
            assertTrue(uri.startsWith("x-apple.systempreferences:"), "Unexpected URI: $uri")
        }
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone",
            uris[OsSettingsSection.PRIVACY_MICROPHONE],
        )
    }

    @Test
    fun macOsDesktopPortTracksTrayStateAndGuardsHeadlessProcess() =
        runTest {
            val port = assertIs<MacOsDesktopSystemPort>(createPlatformDesktopSystemPort())
            assertTrue(port.isSupported)
            assertNull(port.trayState.value)

            val trayState = DesktopTrayState(tooltip = "Abeta Sync", status = TrayStatusKind.SYNCING, badgeCount = 2)
            assertIs<AppResult.Success<Unit>>(port.updateTrayState(trayState))
            assertEquals(trayState, port.trayState.value)
            assertEquals("2", resolveMacOsTrayBadgeLabel(trayState))
            assertEquals("!", resolveMacOsTrayBadgeLabel(DesktopTrayState("Warn", TrayStatusKind.WARNING, 0)))
            assertNull(resolveMacOsTrayBadgeLabel(DesktopTrayState("Idle", TrayStatusKind.IDLE, 0)))

            if (NSApp == null) {
                val badgeFailure = assertIs<AppResult.Failure>(port.setBadgeCount(5))
                assertIs<ForbiddenError>(badgeFailure.error)
                val attnFailure = assertIs<AppResult.Failure>(port.requestUserAttention(critical = true))
                assertIs<ForbiddenError>(attnFailure.error)
                val pickerFailure = assertIs<AppResult.Failure>(createPlatformFilePickerPort().pickFiles())
                assertIs<ForbiddenError>(pickerFailure.error)
            }
        }
}
