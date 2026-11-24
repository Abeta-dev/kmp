@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

private const val STREAM_BUFFER_CAPACITY = 64

/**
 * PCM audio capture parameters passed to [AudioCapturePort.startCapture].
 */
data class AudioCaptureConfig(
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val channels: Int = DEFAULT_CHANNELS,
    val bitDepth: Int = DEFAULT_BIT_DEPTH,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ: Int = 16_000
        const val DEFAULT_CHANNELS: Int = 1
        const val DEFAULT_BIT_DEPTH: Int = 16
    }
}

/**
 * Real-time microphone metering snapshot emitted by [AudioCapturePort.levels].
 */
data class AudioLevelSample(
    val normalizedAmplitude: Float,
    val peakAmplitude: Float = normalizedAmplitude,
    val timestampEpochMillis: Long = 0L,
)

/**
 * Lifecycle states reported by an [AudioCapturePort].
 */
enum class AudioCaptureState {
    IDLE,
    RECORDING,
    PAUSED,
    ERROR,
}

/**
 * Dependency-inverted OS microphone capture port decoupled from platform audio frameworks.
 */
interface AudioCapturePort {
    /** Live capture lifecycle state. */
    val state: StateFlow<AudioCaptureState>

    /** Stream of normalized microphone amplitude samples while recording. */
    val levels: Flow<AudioLevelSample>

    /** Starts capturing microphone audio using [config]. */
    suspend fun startCapture(config: AudioCaptureConfig = AudioCaptureConfig()): AppResult<Unit>

    /** Stops the active capture session and returns the recorded audio bytes. */
    suspend fun stopCapture(): AppResult<ByteArray>

    /** Aborts the active capture session without returning audio data. */
    fun cancelCapture()
}

/**
 * Fallback [AudioCapturePort] when microphone capture is unavailable or not bound on the host.
 */
object UnavailableAudioCapturePort : AudioCapturePort {
    private val idleState = MutableStateFlow(AudioCaptureState.IDLE).asStateFlow()

    override val state: StateFlow<AudioCaptureState> = idleState

    override val levels: Flow<AudioLevelSample> = emptyFlow()

    override suspend fun startCapture(config: AudioCaptureConfig): AppResult<Unit> =
        AppResult.Failure(
            ForbiddenError(
                resource = "audio_capture",
                message = "Audio capture is not available on this host.",
            ),
        )

    override suspend fun stopCapture(): AppResult<ByteArray> =
        AppResult.Failure(
            ForbiddenError(
                resource = "audio_capture",
                message = "Audio capture is not available on this host.",
            ),
        )

    override fun cancelCapture() {
        // No-op when audio capture is unavailable.
    }
}

/**
 * Deterministic test fake for [AudioCapturePort] supporting synthetic level emission and failure injection.
 */
class FakeAudioCapturePort(
    private val recordedBytes: ByteArray = ByteArray(0),
    private val failureError: AppError? = null,
) : AudioCapturePort {
    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    private val _levels =
        MutableSharedFlow<AudioLevelSample>(
            extraBufferCapacity = STREAM_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val levels: Flow<AudioLevelSample> = _levels.asSharedFlow()

    private var lastConfigValue: AudioCaptureConfig? = null

    /** Most recent [AudioCaptureConfig] supplied to [startCapture]. */
    val lastConfig: AudioCaptureConfig?
        get() = lastConfigValue

    /** Emits a synthetic [AudioLevelSample] to active [levels] collectors. */
    fun emitLevel(sample: AudioLevelSample) {
        _levels.tryEmit(sample)
    }

    override suspend fun startCapture(config: AudioCaptureConfig): AppResult<Unit> {
        lastConfigValue = config
        if (failureError != null) {
            _state.value = AudioCaptureState.ERROR
            return AppResult.Failure(failureError)
        }
        _state.value = AudioCaptureState.RECORDING
        return AppResult.Success(Unit)
    }

    override suspend fun stopCapture(): AppResult<ByteArray> {
        if (failureError != null) {
            _state.value = AudioCaptureState.ERROR
            return AppResult.Failure(failureError)
        }
        _state.value = AudioCaptureState.IDLE
        return AppResult.Success(recordedBytes.copyOf())
    }

    override fun cancelCapture() {
        _state.value = AudioCaptureState.IDLE
    }
}

/**
 * Incremental or final recognized speech segment emitted by [SpeechToTextPort.transcripts].
 */
data class SpeechTranscriptChunk(
    val text: String,
    val isFinal: Boolean = false,
    val confidence: Float? = null,
    val localeTag: String? = null,
)

/**
 * Lifecycle states reported by a [SpeechToTextPort].
 */
enum class SpeechRecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR,
}

/**
 * Dependency-inverted speech recognition port abstracting on-device or host STT engines.
 */
interface SpeechToTextPort {
    /** Live speech recognition lifecycle state. */
    val state: StateFlow<SpeechRecognitionState>

    /** Stream of partial and final [SpeechTranscriptChunk] emissions. */
    val transcripts: Flow<SpeechTranscriptChunk>

    /** Begins listening for speech using an optional BCP-47 [localeTag] and [partialResults] flag. */
    suspend fun startListening(
        localeTag: String? = null,
        partialResults: Boolean = true,
    ): AppResult<Unit>

    /** Stops listening and returns the final (or most recent) [SpeechTranscriptChunk], if any. */
    suspend fun stopListening(): AppResult<SpeechTranscriptChunk?>

    /** Aborts the active speech recognition session and discards pending results. */
    fun cancelListening()
}

/**
 * Fallback [SpeechToTextPort] when speech recognition is unavailable or not bound on the host.
 */
object UnavailableSpeechToTextPort : SpeechToTextPort {
    private val idleState = MutableStateFlow(SpeechRecognitionState.IDLE).asStateFlow()

    override val state: StateFlow<SpeechRecognitionState> = idleState

    override val transcripts: Flow<SpeechTranscriptChunk> = emptyFlow()

    override suspend fun startListening(
        localeTag: String?,
        partialResults: Boolean,
    ): AppResult<Unit> =
        AppResult.Failure(
            ForbiddenError(
                resource = "speech_to_text",
                message = "Speech recognition is not available on this host.",
            ),
        )

    override suspend fun stopListening(): AppResult<SpeechTranscriptChunk?> =
        AppResult.Failure(
            ForbiddenError(
                resource = "speech_to_text",
                message = "Speech recognition is not available on this host.",
            ),
        )

    override fun cancelListening() {
        // No-op when speech recognition is unavailable.
    }
}

/**
 * Deterministic test fake for [SpeechToTextPort] supporting transcript emission and failure injection.
 */
class FakeSpeechToTextPort(
    private val failureError: AppError? = null,
) : SpeechToTextPort {
    private val _state = MutableStateFlow(SpeechRecognitionState.IDLE)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private val _transcripts =
        MutableSharedFlow<SpeechTranscriptChunk>(
            extraBufferCapacity = STREAM_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val transcripts: Flow<SpeechTranscriptChunk> = _transcripts.asSharedFlow()

    private var lastLocaleTagValue: String? = null
    private var lastPartialResultsValue: Boolean = true
    private var lastEmittedChunk: SpeechTranscriptChunk? = null

    /** Most recent BCP-47 locale tag passed to [startListening]. */
    val lastLocaleTag: String?
        get() = lastLocaleTagValue

    /** Most recent `partialResults` setting passed to [startListening]. */
    val lastPartialResults: Boolean
        get() = lastPartialResultsValue

    /** Emits a synthetic [SpeechTranscriptChunk] and records it as the latest transcript. */
    fun emitTranscript(chunk: SpeechTranscriptChunk) {
        lastEmittedChunk = chunk
        _transcripts.tryEmit(chunk)
    }

    override suspend fun startListening(
        localeTag: String?,
        partialResults: Boolean,
    ): AppResult<Unit> {
        lastLocaleTagValue = localeTag
        lastPartialResultsValue = partialResults
        lastEmittedChunk = null
        if (failureError != null) {
            _state.value = SpeechRecognitionState.ERROR
            return AppResult.Failure(failureError)
        }
        _state.value = SpeechRecognitionState.LISTENING
        return AppResult.Success(Unit)
    }

    override suspend fun stopListening(): AppResult<SpeechTranscriptChunk?> {
        if (failureError != null) {
            _state.value = SpeechRecognitionState.ERROR
            return AppResult.Failure(failureError)
        }
        _state.value = SpeechRecognitionState.IDLE
        return AppResult.Success(lastEmittedChunk)
    }

    override fun cancelListening() {
        lastEmittedChunk = null
        _state.value = SpeechRecognitionState.IDLE
    }
}
