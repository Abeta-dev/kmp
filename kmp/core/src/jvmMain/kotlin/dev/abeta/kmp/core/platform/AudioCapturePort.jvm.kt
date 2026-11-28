@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

private const val JVM_AUDIO_LEVEL_CAPACITY = 64
private const val PCM_CHUNK_BYTES = 1024

internal class JvmAudioCapturePort(
    private val captureScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : AudioCapturePort {
    private val lock = Any()
    private val _state = MutableStateFlow(AudioCaptureState.IDLE)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    private val _levels =
        MutableSharedFlow<AudioLevelSample>(
            extraBufferCapacity = JVM_AUDIO_LEVEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val levels: Flow<AudioLevelSample> = _levels.asSharedFlow()

    private var activeLine: TargetDataLine? = null
    private var readerJob: Job? = null
    private var capturedBytes: ByteArrayOutputStream? = null

    override suspend fun startCapture(config: AudioCaptureConfig): AppResult<Unit> {
        val validationError = validateConfig(config)
        return if (validationError != null) {
            AppResult.Failure(validationError)
        } else {
            synchronized(lock) {
                startCaptureLocked(config)
            }
        }
    }

    override suspend fun stopCapture(): AppResult<ByteArray> {
        val jobToJoin: Job?
        val output: ByteArrayOutputStream?
        synchronized(lock) {
            if (_state.value != AudioCaptureState.RECORDING) {
                return AppResult.Failure(
                    ForbiddenError(resource = "audio_capture", message = "No active audio capture session to stop."),
                )
            }
            closeLineLocked()
            jobToJoin = readerJob
            readerJob = null
            output = capturedBytes
            capturedBytes = null
            _state.value = AudioCaptureState.IDLE
        }
        jobToJoin?.join()
        return AppResult.Success(output?.toByteArray() ?: ByteArray(0))
    }

    override fun cancelCapture() {
        synchronized(lock) {
            closeLineLocked()
            readerJob?.cancel()
            readerJob = null
            capturedBytes = null
            _state.value = AudioCaptureState.IDLE
        }
    }

    private fun startCaptureLocked(config: AudioCaptureConfig): AppResult<Unit> {
        val format =
            AudioFormat(
                config.sampleRateHz.toFloat(),
                config.bitDepth,
                config.channels,
                true,
                false,
            )
        val info = DataLine.Info(TargetDataLine::class.java, format)
        return when {
            _state.value == AudioCaptureState.RECORDING -> {
                AppResult.Failure(
                    ForbiddenError(resource = "audio_capture", message = "Audio capture is already active."),
                )
            }

            !AudioSystem.isLineSupported(info) -> {
                AppResult.Failure(
                    ForbiddenError(
                        resource = "audio_capture",
                        message = "No JVM audio input line supports ${config.sampleRateHz}Hz/${config.bitDepth}-bit.",
                    ),
                )
            }

            else -> {
                openAndStartLineLocked(info, format, config.bitDepth)
            }
        }
    }

    private fun validateConfig(config: AudioCaptureConfig): ValidationError? =
        when {
            config.sampleRateHz <= 0 -> {
                ValidationError(field = "sampleRateHz", message = "Sample rate must be positive.")
            }

            config.channels <= 0 -> {
                ValidationError(field = "channels", message = "Channel count must be positive.")
            }

            config.bitDepth != PCM_16_BIT_DEPTH && config.bitDepth != PCM_8_BIT_DEPTH -> {
                ValidationError(field = "bitDepth", message = "Unsupported PCM bit depth: ${config.bitDepth}.")
            }

            else -> {
                null
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun openAndStartLineLocked(
        info: DataLine.Info,
        format: AudioFormat,
        bitDepth: Int,
    ): AppResult<Unit> =
        try {
            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(format)
            line.start()
            val output = ByteArrayOutputStream()
            activeLine = line
            capturedBytes = output
            _state.value = AudioCaptureState.RECORDING
            readerJob =
                captureScope.launch {
                    readAudioLoop(line = line, output = output, bitDepth = bitDepth)
                }
            AppResult.Success(Unit)
        } catch (err: LineUnavailableException) {
            closeLineLocked()
            AppResult.Failure(
                ForbiddenError(
                    resource = "audio_capture",
                    message = "Audio capture device unavailable: ${err.message}",
                    cause = err,
                ),
            )
        } catch (err: SecurityException) {
            closeLineLocked()
            AppResult.Failure(
                ForbiddenError(
                    resource = "audio_capture",
                    message = "Audio capture permission denied: ${err.message}",
                    cause = err,
                ),
            )
        } catch (err: Throwable) {
            closeLineLocked()
            AppResult.Failure(
                UnexpectedError(message = "Unable to start JVM audio capture: ${err.message}", cause = err),
            )
        }

    @Suppress("TooGenericExceptionCaught")
    private fun CoroutineScope.readAudioLoop(
        line: TargetDataLine,
        output: ByteArrayOutputStream,
        bitDepth: Int,
    ) {
        val chunk = ByteArray(PCM_CHUNK_BYTES)
        try {
            while (isActive && line.isOpen) {
                val read = line.read(chunk, 0, chunk.size)
                if (read > 0) {
                    output.write(chunk, 0, read)
                    _levels.tryEmit(computePcmAudioLevel(buffer = chunk, length = read, bitDepth = bitDepth))
                } else {
                    break
                }
            }
        } catch (_: Throwable) {
            // Line closed by stopCapture/cancelCapture or disconnected mid-stream.
        }
    }

    private fun closeLineLocked() {
        val line = activeLine
        activeLine = null
        runCatching { line?.stop() }
        runCatching { line?.close() }
    }
}

/** Creates a JVM `javax.sound.sampled` [TargetDataLine]-backed [AudioCapturePort]. */
actual fun createPlatformAudioCapturePort(): AudioCapturePort = JvmAudioCapturePort()
