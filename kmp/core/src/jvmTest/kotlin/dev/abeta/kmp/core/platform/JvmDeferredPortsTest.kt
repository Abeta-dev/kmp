@file:Suppress("MagicNumber")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmDeferredPortsTest {
    @Test
    fun filePickerMimeMatcherFiltersExtensionsCorrectly() {
        assertTrue(matchesRequestedMimeTypes("photo.png", listOf("*/*")))
        assertTrue(matchesRequestedMimeTypes("photo.JPG", listOf("image/*")))
        assertFalse(matchesRequestedMimeTypes("notes.txt", listOf("image/*")))
        assertTrue(matchesRequestedMimeTypes("invoice.pdf", listOf("application/pdf")))
        assertTrue(matchesRequestedMimeTypes("report.csv", listOf("text/csv")))
        assertFalse(matchesRequestedMimeTypes("README", listOf("text/plain")))
        assertTrue(matchesRequestedMimeTypes("README", emptyList()))
    }

    @Test
    fun jvmFactoryReturnsRealFilePickerAudioCaptureAndUnavailableLocation() =
        runTest {
            assertIs<JvmFilePickerPort>(createPlatformFilePickerPort())
            assertIs<JvmAudioCapturePort>(createPlatformAudioCapturePort())
            assertEquals(UnavailableLocationPort, createPlatformLocationPort())

            if (isJvmHeadlessSafely()) {
                val failure = assertIs<AppResult.Failure>(createPlatformFilePickerPort().pickFiles())
                assertIs<ForbiddenError>(failure.error)
            }
        }

    @Test
    fun pcmAudioLevelComputesRmsAndPeakSafely() {
        val silence = ByteArray(16)
        val silentSample = computePcmAudioLevel(silence, silence.size, bitDepth = 16, timestampEpochMillis = 42L)
        assertEquals(0f, silentSample.normalizedAmplitude)
        assertEquals(0f, silentSample.peakAmplitude)
        assertEquals(42L, silentSample.timestampEpochMillis)

        // Two 16-bit little-endian samples at +16384 (~0.5 full scale)
        val halfScale = byteArrayOf(0x00, 0x40, 0x00, 0x40)
        val level = computePcmAudioLevel(halfScale, halfScale.size, bitDepth = 16, timestampEpochMillis = 99L)
        assertTrue(level.normalizedAmplitude in 0.49f..0.51f)
        assertTrue(level.peakAmplitude in 0.49f..0.51f)

        // 8-bit signed PCM at +64 (0.5 full scale)
        val pcm8 = byteArrayOf(64, -64)
        val level8 = computePcmAudioLevel(pcm8, pcm8.size, bitDepth = 8, timestampEpochMillis = 100L)
        assertTrue(level8.normalizedAmplitude in 0.49f..0.51f)
        assertTrue(level8.peakAmplitude in 0.49f..0.51f)
    }

    @Test
    fun jvmAudioCaptureValidatesConfigAndHandlesIdleStopAndCancel() =
        runTest {
            val port = JvmAudioCapturePort()
            assertEquals(AudioCaptureState.IDLE, port.state.value)

            val invalidRate =
                assertIs<AppResult.Failure>(
                    port.startCapture(AudioCaptureConfig(sampleRateHz = 0)),
                )
            assertIs<ValidationError>(invalidRate.error)

            val invalidDepth =
                assertIs<AppResult.Failure>(
                    port.startCapture(AudioCaptureConfig(bitDepth = 32)),
                )
            assertIs<ValidationError>(invalidDepth.error)

            val idleStop = assertIs<AppResult.Failure>(port.stopCapture())
            assertIs<ForbiddenError>(idleStop.error)

            port.cancelCapture()
            assertEquals(AudioCaptureState.IDLE, port.state.value)
        }
}
