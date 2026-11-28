@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

/** Creates the default Android [AudioCapturePort] (defaults to [UnavailableAudioCapturePort]). */
actual fun createPlatformAudioCapturePort(): AudioCapturePort = UnavailableAudioCapturePort
