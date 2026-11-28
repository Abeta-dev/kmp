@file:Suppress("MagicNumber")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.getOrNull
import dev.abeta.kmp.core.security.BiometricAvailability
import dev.abeta.kmp.core.security.BiometricPromptRequest
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.core.security.FakeBiometricAuthenticator
import dev.abeta.kmp.core.security.InMemorySecureKeyValueStore
import dev.abeta.kmp.core.security.createPlatformBiometricAuthenticator
import dev.abeta.kmp.core.security.createSecureKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorePlatformBridgesTest {
    @Test
    fun capabilitiesExposeLocaleAndTimezone() {
        val caps = currentPlatformCapabilities()
        assertEquals(currentPlatformInfo().kind, caps.platform.kind)
        assertTrue(caps.localeTag.isNotBlank())
        assertTrue(caps.timezoneId.isNotBlank())
        assertTrue(caps.osVersion.isNotBlank())
        assertTrue(currentLocaleTag().isNotBlank())
    }

    @Test
    fun formatCurrencyCentsFormatsNonEmptyLocalizedText() {
        val positive = formatCurrencyCents(1250L, "USD")
        val negative = formatCurrencyCents(-349L, "USD")
        assertTrue(positive.contains("12") || positive.contains("USD"))
        assertTrue(negative.contains("-") || negative.contains("("))

        val yen = formatCurrencyCents(1500L, "JPY")
        assertTrue(yen.contains("1,500") || yen.contains("1500"))
        assertFalse(yen.contains("15.00"))

        val dinar = formatCurrencyCents(1250L, "KWD")
        assertTrue(dinar.contains("250"))

        val minValueUsd = formatCurrencyCents(Long.MIN_VALUE, "USD")
        val minValueJpy = formatCurrencyCents(Long.MIN_VALUE, "JPY")
        assertTrue(minValueUsd.isNotBlank())
        assertTrue(minValueJpy.isNotBlank())
        assertEquals(
            "-JPY 9223372036854775808",
            formatCurrencyMinorUnitsFallback(Long.MIN_VALUE, "JPY"),
        )
        assertEquals(
            "-KWD 9223372036854775.808",
            formatCurrencyMinorUnitsFallback(Long.MIN_VALUE, "KWD"),
        )
    }

    @Test
    fun deepLinkParserHandlesCustomSchemeAndUniversalLinks() {
        val custom = DeepLinkParser.parse("abeta://screen/store-home?promo=FRESH10&tab=fruits")
        assertNotNull(custom)
        assertEquals("abeta", custom.scheme)
        assertEquals("store-home", custom.screenId)
        assertEquals("FRESH10", custom.params["promo"])
        assertEquals("fruits", custom.params["tab"])

        val hostAsScreen = DeepLinkParser.parse("abeta://catalog?sort=popular")
        assertNotNull(hostAsScreen)
        assertEquals("catalog", hostAsScreen.screenId)
        assertEquals("popular", hostAsScreen.params["sort"])

        val universal = DeepLinkParser.parse("https://app.abeta.dev/screens/form-demo?step=2%20of%203")
        assertNotNull(universal)
        assertEquals("form-demo", universal.screenId)
        assertEquals("2 of 3", universal.params["step"])

        val complex =
            DeepLinkParser.parse(
                "https://user:pass@app.abeta.dev:8443/screens/c++%20guide?q=hello+world#section?ignored=1",
            )
        assertNotNull(complex)
        assertEquals("app.abeta.dev", complex.host)
        assertEquals("c++ guide", complex.screenId)
        assertEquals("hello world", complex.params["q"])
        assertNull(complex.params["ignored"])

        val fragmentBeforeQuery = DeepLinkParser.parse("https://app.abeta.dev/screens/orders#anchor?fake=true")
        assertNotNull(fragmentBeforeQuery)
        assertEquals("orders", fragmentBeforeQuery.screenId)
        assertTrue(fragmentBeforeQuery.params.isEmpty())

        assertNull(DeepLinkParser.parse("https://app.abeta.dev/"))
        assertNull(DeepLinkParser.parse("not-a-uri"))
    }

    @Test
    fun externalUrlValidationRejectsUnsafeSchemes() =
        runTest {
            val launcher = RecordingExternalLauncherPort()
            assertTrue(launcher.canOpenUrl("https://abeta.dev/docs"))
            assertFalse(launcher.canOpenUrl("javascript:alert(1)"))
            assertFalse(launcher.canOpenUrl("file:///etc/passwd"))
            assertFalse(launcher.canOpenUrl("https://abeta.dev/path with space"))
            assertFalse(launcher.canOpenUrl("https://abeta.dev/path\r\nHeader: evil"))
            assertFalse(launcher.canOpenUrl("https://abeta.dev/path\tfoo"))
            assertFalse(launcher.canOpenUrl("https://abeta.dev/path\u0000null"))

            val blocked = launcher.openUrl("javascript:alert(1)")
            val failure = assertIs<AppResult.Failure>(blocked)
            assertIs<ValidationError>(failure.error)

            val ok = launcher.openUrl("https://abeta.dev/orders/123")
            assertIs<AppResult.Success<Unit>>(ok)
            assertEquals(listOf("https://abeta.dev/orders/123"), launcher.openedUrls)
        }

    @Test
    fun shareRequestFormatsCombinedTextCleanly() =
        runTest {
            val request =
                ShareRequest(
                    title = "Abeta Fresh Order",
                    text = "Arriving in 8 mins",
                    url = "https://abeta.dev/orders/88",
                )
            assertEquals(
                "Abeta Fresh Order\nArriving in 8 mins\nhttps://abeta.dev/orders/88",
                request.formatCombinedText(),
            )
            val port = RecordingSharePort()
            assertIs<AppResult.Success<Unit>>(port.share(request))
            assertEquals(listOf(request), port.requests)
        }

    @Test
    fun inMemoryAndPlatformBridgeDefaultsAreConsistent() =
        runTest {
            val clipboard = InMemoryClipboardPort()
            clipboard.copy("PROMO20", label = "Coupon")
            assertEquals("PROMO20", clipboard.readText().getOrNull())
            assertEquals("Coupon", clipboard.lastLabel)

            val haptics = RecordingHapticEngine()
            haptics.perform(HapticStyle.TICK)
            haptics.perform(HapticStyle.SUCCESS)
            assertEquals(listOf(HapticStyle.TICK, HapticStyle.SUCCESS), haptics.performed)

            val connectivity = MutableConnectivityMonitor(ConnectivityStatus.OFFLINE)
            assertFalse(connectivity.isOnline())
            connectivity.setStatus(ConnectivityStatus.ONLINE)
            assertTrue(connectivity.isOnline())

            assertEquals(PermissionStatus.UNSUPPORTED, UnsupportedPermissionPort.checkStatus(OsPermission.LOCATION))
            assertIs<AppResult.Failure>(UnavailableLocationPort.currentLocation())
            assertIs<AppResult.Failure>(UnavailableFilePickerPort.pickFiles())
            assertEquals(null, UnavailablePushNotificationPort.currentToken().getOrNull())
            assertIs<AppResult.Failure>(UnavailablePushNotificationPort.registerForPush())
        }

    @Test
    fun secureStoreAndBiometricPortsWorkSafely() =
        runTest {
            val inMemorySecure = InMemorySecureKeyValueStore()
            inMemorySecure.putString("token", "secret-123")
            assertEquals("secret-123", inMemorySecure.getString("token"))
            assertTrue(inMemorySecure.contains("token"))
            inMemorySecure.remove("token")
            assertNull(inMemorySecure.getString("token"))

            val platformSecure = createSecureKeyValueStore("abeta.core.bridge.test")
            platformSecure.clear()
            platformSecure.putString("api_key", "abeta-token-value")
            assertEquals("abeta-token-value", platformSecure.getString("api_key"))
            assertTrue("api_key" in platformSecure.keys())
            platformSecure.remove("api_key")
            assertNull(platformSecure.getString("api_key"))
            platformSecure.clear()

            val fakeBio = FakeBiometricAuthenticator(BiometricAvailability.AVAILABLE, BiometricResult.Authenticated)
            assertEquals(BiometricAvailability.AVAILABLE, fakeBio.availability())
            assertEquals(BiometricResult.Authenticated, fakeBio.authenticate(BiometricPromptRequest("Confirm pay")))

            val platformBio = createPlatformBiometricAuthenticator()
            assertNotNull(platformBio.availability())
            createPlatformHapticEngine().perform(HapticStyle.TICK)
            createPlatformClipboardPort()
            createPlatformExternalLauncher()
            createPlatformSharePort()
            assertNotNull(createPlatformFilePickerPort())
            assertNotNull(createPlatformAudioCapturePort())
            assertNotNull(createPlatformLocationPort())
            val monitor = createPlatformConnectivityMonitor()
            monitor.start()
            monitor.refresh()
            monitor.stop()
        }
}
