@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

/** Creates the default Apple [LocationPort] (host apps inject CoreLocation providers). */
actual fun createPlatformLocationPort(): LocationPort = UnavailableLocationPort

/** Creates the default Apple [AudioCapturePort] (defaults to [UnavailableAudioCapturePort]). */
actual fun createPlatformAudioCapturePort(): AudioCapturePort = UnavailableAudioCapturePort
