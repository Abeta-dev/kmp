@file:Suppress("MagicNumber")

package dev.abeta.kmp.core.platform

import app.cash.turbine.test
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceAndDesktopPortsTest {
    @Test
    fun audioCaptureConfigAndLevelSampleUseExpectedDefaults() {
        val config = AudioCaptureConfig()
        assertEquals(16_000, config.sampleRateHz)
        assertEquals(1, config.channels)
        assertEquals(16, config.bitDepth)

        val level = AudioLevelSample(normalizedAmplitude = 0.42f)
        assertEquals(0.42f, level.normalizedAmplitude)
        assertEquals(0.42f, level.peakAmplitude)
        assertEquals(0L, level.timestampEpochMillis)
    }

    @Test
    fun unavailableAudioCapturePortReturnsForbiddenErrorsAndIdleState() =
        runTest {
            assertEquals(AudioCaptureState.IDLE, UnavailableAudioCapturePort.state.value)
            assertEquals(emptyList(), UnavailableAudioCapturePort.levels.toList())

            val startFailure = assertIs<AppResult.Failure>(UnavailableAudioCapturePort.startCapture())
            val startError = assertIs<ForbiddenError>(startFailure.error)
            assertEquals("audio_capture", startError.resource)

            val stopFailure = assertIs<AppResult.Failure>(UnavailableAudioCapturePort.stopCapture())
            val stopError = assertIs<ForbiddenError>(stopFailure.error)
            assertEquals("audio_capture", stopError.resource)

            UnavailableAudioCapturePort.cancelCapture()
            assertEquals(AudioCaptureState.IDLE, UnavailableAudioCapturePort.state.value)
        }

    @Test
    fun fakeAudioCapturePortTransitionsStatesStreamsLevelsAndTracksConfig() =
        runTest {
            val expectedBytes = byteArrayOf(1, 2, 3, 4)
            val customConfig = AudioCaptureConfig(sampleRateHz = 44_100, channels = 2, bitDepth = 24)
            val fake = FakeAudioCapturePort(recordedBytes = expectedBytes)

            assertEquals(AudioCaptureState.IDLE, fake.state.value)
            assertNull(fake.lastConfig)

            assertIs<AppResult.Success<Unit>>(fake.startCapture(customConfig))
            assertEquals(AudioCaptureState.RECORDING, fake.state.value)
            assertEquals(customConfig, fake.lastConfig)

            val sample1 =
                AudioLevelSample(
                    normalizedAmplitude = 0.25f,
                    peakAmplitude = 0.40f,
                    timestampEpochMillis = 100L,
                )
            val sample2 =
                AudioLevelSample(
                    normalizedAmplitude = 0.80f,
                    peakAmplitude = 0.95f,
                    timestampEpochMillis = 200L,
                )
            fake.levels.test {
                fake.emitLevel(sample1)
                fake.emitLevel(sample2)
                assertEquals(sample1, awaitItem())
                assertEquals(sample2, awaitItem())
            }

            val stopped = assertIs<AppResult.Success<ByteArray>>(fake.stopCapture())
            assertContentEquals(expectedBytes, stopped.value)
            assertEquals(AudioCaptureState.IDLE, fake.state.value)

            assertIs<AppResult.Success<Unit>>(fake.startCapture())
            assertEquals(AudioCaptureState.RECORDING, fake.state.value)
            fake.cancelCapture()
            assertEquals(AudioCaptureState.IDLE, fake.state.value)
        }

    @Test
    fun fakeAudioCapturePortInjectsConfiguredFailure() =
        runTest {
            val injected = UnexpectedError(message = "Microphone busy")
            val fake = FakeAudioCapturePort(failureError = injected)

            val startFailure = assertIs<AppResult.Failure>(fake.startCapture())
            assertEquals(injected, startFailure.error)
            assertEquals(AudioCaptureState.ERROR, fake.state.value)

            val stopFailure = assertIs<AppResult.Failure>(fake.stopCapture())
            assertEquals(injected, stopFailure.error)
            assertEquals(AudioCaptureState.ERROR, fake.state.value)
        }

    @Test
    fun unavailableSpeechToTextPortReturnsForbiddenErrorsAndIdleState() =
        runTest {
            assertEquals(SpeechRecognitionState.IDLE, UnavailableSpeechToTextPort.state.value)
            assertEquals(emptyList(), UnavailableSpeechToTextPort.transcripts.toList())

            val startFailure = assertIs<AppResult.Failure>(UnavailableSpeechToTextPort.startListening("en-US"))
            val startError = assertIs<ForbiddenError>(startFailure.error)
            assertEquals("speech_to_text", startError.resource)

            val stopFailure = assertIs<AppResult.Failure>(UnavailableSpeechToTextPort.stopListening())
            val stopError = assertIs<ForbiddenError>(stopFailure.error)
            assertEquals("speech_to_text", stopError.resource)

            UnavailableSpeechToTextPort.cancelListening()
            assertEquals(SpeechRecognitionState.IDLE, UnavailableSpeechToTextPort.state.value)
        }

    @Test
    fun fakeSpeechToTextPortStreamsChunksTracksLocaleAndReturnsLastChunk() =
        runTest {
            val fake = FakeSpeechToTextPort()
            assertEquals(SpeechRecognitionState.IDLE, fake.state.value)
            assertNull(fake.lastLocaleTag)

            assertIs<AppResult.Success<Unit>>(fake.startListening(localeTag = "en-US", partialResults = true))
            assertEquals(SpeechRecognitionState.LISTENING, fake.state.value)
            assertEquals("en-US", fake.lastLocaleTag)
            assertTrue(fake.lastPartialResults)

            val partial =
                SpeechTranscriptChunk(
                    text = "order two",
                    isFinal = false,
                    confidence = 0.72f,
                    localeTag = "en-US",
                )
            val finalChunk =
                SpeechTranscriptChunk(
                    text = "order two coffees",
                    isFinal = true,
                    confidence = 0.96f,
                    localeTag = "en-US",
                )
            fake.transcripts.test {
                fake.emitTranscript(partial)
                fake.emitTranscript(finalChunk)
                assertEquals(partial, awaitItem())
                assertEquals(finalChunk, awaitItem())
            }

            val stopped = assertIs<AppResult.Success<SpeechTranscriptChunk?>>(fake.stopListening())
            assertEquals(finalChunk, stopped.value)
            assertEquals(SpeechRecognitionState.IDLE, fake.state.value)

            assertIs<AppResult.Success<Unit>>(fake.startListening(localeTag = "fr-FR", partialResults = false))
            assertEquals("fr-FR", fake.lastLocaleTag)
            assertFalse(fake.lastPartialResults)
            fake.cancelListening()
            assertEquals(SpeechRecognitionState.IDLE, fake.state.value)
            val afterCancel = assertIs<AppResult.Success<SpeechTranscriptChunk?>>(fake.stopListening())
            assertNull(afterCancel.value)
        }

    @Test
    fun fakeSpeechToTextPortInjectsConfiguredFailure() =
        runTest {
            val injected = ForbiddenError(resource = "microphone", message = "Permission denied")
            val fake = FakeSpeechToTextPort(failureError = injected)

            val startFailure = assertIs<AppResult.Failure>(fake.startListening(localeTag = "en-GB"))
            assertEquals(injected, startFailure.error)
            assertEquals("en-GB", fake.lastLocaleTag)
            assertEquals(SpeechRecognitionState.ERROR, fake.state.value)

            val stopFailure = assertIs<AppResult.Failure>(fake.stopListening())
            assertEquals(injected, stopFailure.error)
            assertEquals(SpeechRecognitionState.ERROR, fake.state.value)
        }

    @Test
    fun unavailableDesktopSystemPortReportsUnsupportedAndReturnsForbidden() =
        runTest {
            assertFalse(UnavailableDesktopSystemPort.isSupported)

            val trayFailure =
                assertIs<AppResult.Failure>(
                    UnavailableDesktopSystemPort.updateTrayState(DesktopTrayState(tooltip = "Abeta")),
                )
            assertEquals("desktop_system", assertIs<ForbiddenError>(trayFailure.error).resource)

            val settingsFailure =
                assertIs<AppResult.Failure>(
                    UnavailableDesktopSystemPort.openOsSettings(OsSettingsSection.PRIVACY_MICROPHONE),
                )
            assertEquals("desktop_system", assertIs<ForbiddenError>(settingsFailure.error).resource)

            val badgeFailure = assertIs<AppResult.Failure>(UnavailableDesktopSystemPort.setBadgeCount(3))
            assertEquals("desktop_system", assertIs<ForbiddenError>(badgeFailure.error).resource)

            val attentionFailure =
                assertIs<AppResult.Failure>(UnavailableDesktopSystemPort.requestUserAttention(critical = true))
            assertEquals("desktop_system", assertIs<ForbiddenError>(attentionFailure.error).resource)
        }

    @Test
    fun recordingDesktopSystemPortCapturesAllInteractionsAndPlatformFactoryConstructs() =
        runTest {
            val port = RecordingDesktopSystemPort()
            assertTrue(port.isSupported)

            val trayState =
                DesktopTrayState(
                    tooltip = "Syncing orders",
                    status = TrayStatusKind.SYNCING,
                    badgeCount = 4,
                )
            assertIs<AppResult.Success<Unit>>(port.updateTrayState(trayState))
            assertIs<AppResult.Success<Unit>>(port.openOsSettings(OsSettingsSection.PRIVACY_ACCESSIBILITY))
            assertIs<AppResult.Success<Unit>>(port.setBadgeCount(4))
            assertIs<AppResult.Success<Unit>>(port.requestUserAttention())
            assertIs<AppResult.Success<Unit>>(port.requestUserAttention(critical = true))

            assertEquals(listOf(trayState), port.trayStates)
            assertEquals(listOf(OsSettingsSection.PRIVACY_ACCESSIBILITY), port.openedSettings)
            assertEquals(listOf(4), port.badgeCounts)
            assertEquals(listOf(false, true), port.attentionRequests)

            port.clear()
            assertTrue(port.trayStates.isEmpty())
            assertTrue(port.openedSettings.isEmpty())
            assertTrue(port.badgeCounts.isEmpty())
            assertTrue(port.attentionRequests.isEmpty())

            val platformPort = createPlatformDesktopSystemPort()
            assertNotNull(platformPort)
        }
}
