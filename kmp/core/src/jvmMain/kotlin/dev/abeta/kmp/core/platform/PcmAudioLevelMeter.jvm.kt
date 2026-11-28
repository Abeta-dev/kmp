@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import kotlin.math.abs
import kotlin.math.sqrt

internal const val PCM_16_BIT_DEPTH = 16
internal const val PCM_8_BIT_DEPTH = 8
private const val PCM_16_MAX_AMPLITUDE = 32_768.0
private const val PCM_8_MAX_AMPLITUDE = 128.0
private const val BYTE_MASK = 0xFF
private const val HIGH_BYTE_SHIFT = 8

internal fun computePcmAudioLevel(
    buffer: ByteArray,
    length: Int,
    bitDepth: Int = PCM_16_BIT_DEPTH,
    timestampEpochMillis: Long = System.currentTimeMillis(),
): AudioLevelSample {
    val safeLength = length.coerceIn(0, buffer.size)
    return when {
        safeLength == 0 -> AudioLevelSample(0f, 0f, timestampEpochMillis)
        bitDepth == PCM_16_BIT_DEPTH -> computePcm16Level(buffer, safeLength, timestampEpochMillis)
        else -> computePcm8Level(buffer, safeLength, timestampEpochMillis)
    }
}

private fun computePcm16Level(
    buffer: ByteArray,
    length: Int,
    timestampEpochMillis: Long,
): AudioLevelSample {
    val sampleCount = length / 2
    if (sampleCount == 0) {
        return AudioLevelSample(0f, 0f, timestampEpochMillis)
    }
    var sumSquares = 0.0
    var peak = 0.0
    var index = 0
    while (index + 1 < length) {
        val low = buffer[index].toInt() and BYTE_MASK
        val high = buffer[index + 1].toInt() shl HIGH_BYTE_SHIFT
        val sample = (high or low).toShort().toDouble() / PCM_16_MAX_AMPLITUDE
        val magnitude = abs(sample)
        sumSquares += sample * sample
        if (magnitude > peak) peak = magnitude
        index += 2
    }
    val rms = sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
    return AudioLevelSample(
        normalizedAmplitude = rms,
        peakAmplitude = peak.toFloat().coerceIn(0f, 1f),
        timestampEpochMillis = timestampEpochMillis,
    )
}

private fun computePcm8Level(
    buffer: ByteArray,
    length: Int,
    timestampEpochMillis: Long,
): AudioLevelSample {
    var sumSquares = 0.0
    var peak = 0.0
    for (i in 0 until length) {
        val sample = buffer[i].toDouble() / PCM_8_MAX_AMPLITUDE
        val magnitude = abs(sample)
        sumSquares += sample * sample
        if (magnitude > peak) peak = magnitude
    }
    val rms = sqrt(sumSquares / length).toFloat().coerceIn(0f, 1f)
    return AudioLevelSample(
        normalizedAmplitude = rms,
        peakAmplitude = peak.toFloat().coerceIn(0f, 1f),
        timestampEpochMillis = timestampEpochMillis,
    )
}
