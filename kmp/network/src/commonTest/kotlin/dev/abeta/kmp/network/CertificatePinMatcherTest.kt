@file:Suppress("MagicNumber")

package dev.abeta.kmp.network

import dev.abeta.kmp.network.client.CertificatePinMatcher
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CertificatePinMatcherTest {
    @Test
    fun matchesLiteralAndWildcardHostnames() {
        assertTrue(CertificatePinMatcher.matchesHostname("api.abeta.dev", "api.abeta.dev"))
        assertTrue(CertificatePinMatcher.matchesHostname("api.abeta.dev.", "api.abeta.dev"))
        assertTrue(CertificatePinMatcher.matchesHostname("API.ABETA.DEV.", "*.abeta.dev."))
        assertFalse(CertificatePinMatcher.matchesHostname("deep.sub.abeta.dev", "*.abeta.dev"))
        assertFalse(CertificatePinMatcher.matchesHostname("evil.com", "*.abeta.dev"))

        val pins =
            CertificatePinMatcher.pinsForHost(
                host = "api.abeta.dev",
                certificatePins =
                    mapOf(
                        "*.abeta.dev" to listOf("sha256/AAAA="),
                        "other.example" to listOf("sha256/BBBB="),
                    ),
            )
        assertEquals(setOf("sha256/AAAA="), pins)
    }

    @Test
    fun extractsSubjectPublicKeyInfoFromMinimalX509Der() {
        val spki = byteArrayOf(0x30, 0x05, 0x06, 0x01, 0x00, 0x03, 0x00)
        val tbsChildren =
            byteArrayOf(
                0xA0.toByte(),
                0x03,
                0x02,
                0x01,
                0x02, // [0] version
                0x02,
                0x01,
                0x01, // serialNumber
                0x30,
                0x00, // signature alg
                0x30,
                0x00, // issuer
                0x30,
                0x00, // validity
                0x30,
                0x00, // subject
            ) + spki
        val tbs = byteArrayOf(0x30, tbsChildren.size.toByte()) + tbsChildren
        val certPayload = tbs + byteArrayOf(0x30, 0x00, 0x03, 0x00)
        val cert = byteArrayOf(0x30, certPayload.size.toByte()) + certPayload

        val extracted = CertificatePinMatcher.extractSpkiDerOrNull(cert)
        assertNotNull(extracted)
        assertContentEquals(spki, extracted)
        assertNull(CertificatePinMatcher.extractSpkiDerOrNull(byteArrayOf(0x01, 0x02)))

        // Crafted 4-byte DER length (0xFFFFFFFF) must not overflow Int arithmetic.
        val overflowDer =
            byteArrayOf(
                0x30,
                0x84.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0x00,
                0x00,
            )
        assertNull(CertificatePinMatcher.extractSpkiDerOrNull(overflowDer))
    }
}
