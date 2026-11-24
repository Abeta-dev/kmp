package dev.abeta.kmp.core.platform

import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmDesktopSystemPortTest {
    @Test
    fun resolveOsSettingsUriMapsAllSectionsOnMacOs() {
        val uris =
            OsSettingsSection.entries.associateWith { section ->
                resolveOsSettingsUri(osName = "Mac OS X", section = section)
            }

        OsSettingsSection.entries.forEach { section ->
            val uri = assertNotNull(uris[section], "Expected macOS URI for $section")
            assertTrue(uri.startsWith("x-apple.systempreferences:"), "Unexpected macOS URI: $uri")
        }
        assertEquals(OsSettingsSection.entries.size, uris.values.toSet().size)
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.general",
            uris[OsSettingsSection.GENERAL],
        )
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone",
            uris[OsSettingsSection.PRIVACY_MICROPHONE],
        )
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility",
            uris[OsSettingsSection.PRIVACY_ACCESSIBILITY],
        )
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.notifications",
            uris[OsSettingsSection.NOTIFICATIONS],
        )
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.network",
            uris[OsSettingsSection.NETWORK],
        )
        assertEquals(
            "x-apple.systempreferences:com.apple.preference.displays",
            uris[OsSettingsSection.DISPLAY],
        )
        assertEquals(
            uris[OsSettingsSection.PRIVACY_MICROPHONE],
            resolveOsSettingsUri(osName = "Darwin", section = OsSettingsSection.PRIVACY_MICROPHONE),
        )
    }

    @Test
    fun resolveOsSettingsUriMapsAllSectionsOnWindows() {
        val uris =
            OsSettingsSection.entries.associateWith { section ->
                resolveOsSettingsUri(osName = "Windows 11", section = section)
            }

        OsSettingsSection.entries.forEach { section ->
            val uri = assertNotNull(uris[section], "Expected Windows URI for $section")
            assertTrue(uri.startsWith("ms-settings:"), "Unexpected Windows URI: $uri")
        }
        assertEquals(OsSettingsSection.entries.size, uris.values.toSet().size)
        assertEquals("ms-settings:about", uris[OsSettingsSection.GENERAL])
        assertEquals("ms-settings:privacy-microphone", uris[OsSettingsSection.PRIVACY_MICROPHONE])
        assertEquals("ms-settings:easeofaccess", uris[OsSettingsSection.PRIVACY_ACCESSIBILITY])
        assertEquals("ms-settings:notifications", uris[OsSettingsSection.NOTIFICATIONS])
        assertEquals("ms-settings:network", uris[OsSettingsSection.NETWORK])
        assertEquals("ms-settings:display", uris[OsSettingsSection.DISPLAY])
    }

    @Test
    fun resolveOsSettingsUriReturnsNullOnLinuxForAllSections() {
        OsSettingsSection.entries.forEach { section ->
            assertNull(resolveOsSettingsUri(osName = "Linux", section = section))
            assertNull(resolveOsSettingsUri(osName = "Ubuntu 24.04", section = section))
        }
    }

    @Test
    fun platformFactoryCreatesJvmDesktopSystemPortMatchingHeadlessState() {
        val port = createPlatformDesktopSystemPort()
        assertIs<JvmDesktopSystemPort>(port)
        assertEquals(!GraphicsEnvironment.isHeadless(), port.isSupported)
    }
}
